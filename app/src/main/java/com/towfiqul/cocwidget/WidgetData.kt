package com.towfiqul.cocwidget

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Widget sections, in display order. */
enum class Section {
    BUILDERS, LAB, HEROES, PETS
}

/** One upgrade currently in progress. [targetLevel] is lvl+1: `lvl` in the
 * export is the level the item has now, not the level being built. */
data class UpgradeItem(
    val name: String,
    val targetLevel: Int,
    val remainingSeconds: Long,
    val section: Section
)

/** One parsed account, ready to render. */
data class AccountView(
    val tag: String,
    val displayName: String,
    val townHallLevel: Int,
    val exportedEpoch: Long,
    val helpersReadySeconds: Long,
    val builders: List<UpgradeItem>,
    val lab: List<UpgradeItem>,
    val heroes: List<UpgradeItem>,
    val pets: List<UpgradeItem>
) {
    /** (Section title, items) pairs in display order. Empty sections are
     * skipped by the renderers, so PETS disappears when it has no rows. */
    val sections: List<Pair<String, List<UpgradeItem>>>
        get() = listOf(
            "BUILDERS" to builders,
            "LAB" to lab,
            "HEROES" to heroes,
            "PETS" to pets
        )
}

/**
 * Parses the in-game village export as text (the format ClashClock reads).
 * Timer rule: remaining = timer - (now - timestamp). A row with a `timer`
 * field is an upgrade in progress; a row without one is finished.
 * Names come from the bundled assets/names.json (238 gameplay IDs).
 */
object VillageParser {

    private var names: Map<String, Map<String, String>>? = null

    fun loadNames(context: Context) {
        if (names != null) return
        val text = context.assets.open("names.json").bufferedReader().readText()
        val root = JSONObject(text)
        val out = mutableMapOf<String, Map<String, String>>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val section = keys.next()
            val sectionObj = root.getJSONObject(section)
            val map = mutableMapOf<String, String>()
            val ids = sectionObj.keys()
            while (ids.hasNext()) {
                val id = ids.next()
                map[id] = sectionObj.getString(id)
            }
            out[section] = map
        }
        names = out
    }

    private fun lookup(sectionKey: String, dataId: Int): String {
        return names?.get(sectionKey)?.get(dataId.toString()) ?: "Unknown ($dataId)"
    }

    /** Returns null when the text is not a valid village export. */
    fun parse(context: Context, jsonText: String, displayName: String): AccountView? {
        return try {
            loadNames(context)
            val root = JSONObject(jsonText)
            val tag = root.optString("tag", "?")
            val timestamp = root.optLong("timestamp", 0L)
            val now = System.currentTimeMillis() / 1000
            val elapsed = (now - timestamp).coerceAtLeast(0)

            fun items(sectionKey: String, section: Section): List<UpgradeItem> {
                val arr = root.optJSONArray(sectionKey) ?: return emptyList()
                val list = mutableListOf<UpgradeItem>()
                for (i in 0 until arr.length()) {
                    val row = arr.optJSONObject(i) ?: continue
                    if (!row.has("timer")) continue
                    val dataId = row.optInt("data", -1)
                    val lvl = row.optInt("lvl", 0)
                    val timer = row.optLong("timer", 0L)
                    val remaining = timer - elapsed
                    if (remaining <= 0) continue
                    list.add(UpgradeItem(lookup(sectionKey, dataId), lvl + 1, remaining, section))
                }
                return list.sortedBy { it.remainingSeconds }
            }

            // BUILDERS = home-village buildings + traps.
            val builders = (items("buildings", Section.BUILDERS) +
                items("traps", Section.BUILDERS)).sortedBy { it.remainingSeconds }
            // LAB = troops + spells + siege machines.
            val lab = (items("units", Section.LAB) +
                items("spells", Section.LAB) +
                items("siege_machines", Section.LAB)).sortedBy { it.remainingSeconds }
            val heroes = items("heroes", Section.HEROES)
            val pets = items("pets", Section.PETS)

            // Town Hall = buildings row with data 1000001; its lvl IS the TH level.
            var townHall = 0
            val buildings = root.optJSONArray("buildings")
            if (buildings != null) {
                for (i in 0 until buildings.length()) {
                    val row = buildings.optJSONObject(i) ?: continue
                    if (row.optInt("data", -1) == 1000001) {
                        townHall = row.optInt("lvl", 0)
                        break
                    }
                }
            }

            // Helpers share one cooldown: the same helper_cooldown value sits
            // on every helper row, so the first row is enough.
            var helperCooldown = 0L
            val helpers = root.optJSONArray("helpers")
            if (helpers != null && helpers.length() > 0) {
                helperCooldown = helpers.optJSONObject(0).optLong("helper_cooldown", 0L)
            }
            val helpersReady = (helperCooldown - elapsed).coerceAtLeast(0)

            AccountView(
                tag = tag,
                displayName = displayName,
                townHallLevel = townHall,
                exportedEpoch = timestamp,
                helpersReadySeconds = helpersReady,
                builders = builders,
                lab = lab,
                heroes = heroes,
                pets = pets
            )
        } catch (e: Exception) {
            null
        }
    }
}

/** Stored village JSON + display names. Falls back to the bundled exports. */
object Prefs {
    private const val FILE = "coc_widget_prefs"
    const val KEY_JSON1 = "json1"
    const val KEY_JSON2 = "json2"
    const val KEY_NAME1 = "name1"
    const val KEY_NAME2 = "name2"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun bundledJson(context: Context, which: Int): String {
        val asset = if (which == 1) "account1.json" else "account2.json"
        return context.assets.open(asset).bufferedReader().readText()
    }

    fun jsonText(context: Context, which: Int): String {
        val key = if (which == 1) KEY_JSON1 else KEY_JSON2
        return prefs(context).getString(key, null) ?: bundledJson(context, which)
    }

    fun displayName(context: Context, which: Int, tag: String): String {
        val key = if (which == 1) KEY_NAME1 else KEY_NAME2
        return prefs(context).getString(key, null) ?: tag
    }

    /** Parses both stored accounts. An account whose JSON fails to parse is
     * skipped, so one bad paste never blanks the whole widget. */
    fun loadAccounts(context: Context): List<AccountView> {
        val list = mutableListOf<AccountView>()
        for (which in 1..2) {
            val parsed = VillageParser.parse(context, jsonText(context, which), "?")
            if (parsed != null) {
                list.add(parsed.copy(displayName = displayName(context, which, parsed.tag)))
            }
        }
        return list
    }
}

/** "2d 03:14:22" when >= 1 day, otherwise "HH:MM:SS". */
fun formatDuration(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val days = s / 86400
    val h = (s % 86400) / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (days > 0) {
        "%dd %02d:%02d:%02d".format(days, h, m, sec)
    } else {
        "%02d:%02d:%02d".format(h, m, sec)
    }
}

/** Export time in the device's timezone, e.g. "7 Nov 2026, 11:37 PM". */
fun formatExported(epochSeconds: Long): String {
    val sdf = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
    return sdf.format(Date(epochSeconds * 1000))
}
