package com.towfiqul.cocwidget

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var name1Edit: EditText
    private lateinit var json1Edit: EditText
    private lateinit var name2Edit: EditText
    private lateinit var json2Edit: EditText
    private lateinit var previewText: TextView
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        name1Edit = findViewById(R.id.name1_edit)
        json1Edit = findViewById(R.id.json1_edit)
        name2Edit = findViewById(R.id.name2_edit)
        json2Edit = findViewById(R.id.json2_edit)
        previewText = findViewById(R.id.preview_text)
        statusText = findViewById(R.id.status_text)

        loadCurrentValues()

        findViewById<Button>(R.id.save_button).setOnClickListener { saveAll() }
        findViewById<Button>(R.id.defaults_button).setOnClickListener { loadBundledDefaults() }
        findViewById<Button>(R.id.update_button).setOnClickListener {
            CoCWidgetProvider.updateAllWidgets(this)
            Toast.makeText(this, R.string.widget_updated, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadCurrentValues() {
        val prefs = Prefs.prefs(this)
        name1Edit.setText(prefs.getString(Prefs.KEY_NAME1, null) ?: defaultTag(1))
        json1Edit.setText(Prefs.jsonText(this, 1))
        name2Edit.setText(prefs.getString(Prefs.KEY_NAME2, null) ?: defaultTag(2))
        json2Edit.setText(Prefs.jsonText(this, 2))
        refreshPreview()
    }

    private fun defaultTag(which: Int): String {
        return VillageParser.parse(this, Prefs.bundledJson(this, which), "?")?.tag ?: ""
    }

    private fun loadBundledDefaults() {
        Prefs.prefs(this).edit()
            .remove(Prefs.KEY_JSON1)
            .remove(Prefs.KEY_JSON2)
            .remove(Prefs.KEY_NAME1)
            .remove(Prefs.KEY_NAME2)
            .apply()
        loadCurrentValues()
        CoCWidgetProvider.updateAllWidgets(this)
        statusText.setText(R.string.defaults_loaded)
    }

    private fun saveAll() {
        val json1 = json1Edit.text.toString().trim()
        val json2 = json2Edit.text.toString().trim()
        val parsed1 = VillageParser.parse(this, json1, "?")
        val parsed2 = VillageParser.parse(this, json2, "?")
        if (parsed1 == null || parsed2 == null) {
            val bad = if (parsed1 == null) {
                getString(R.string.account_1)
            } else {
                getString(R.string.account_2)
            }
            Toast.makeText(this, getString(R.string.invalid_json, bad), Toast.LENGTH_LONG).show()
            return
        }
        val name1 = name1Edit.text.toString().trim().ifEmpty { parsed1.tag }
        val name2 = name2Edit.text.toString().trim().ifEmpty { parsed2.tag }
        Prefs.prefs(this).edit()
            .putString(Prefs.KEY_JSON1, json1)
            .putString(Prefs.KEY_JSON2, json2)
            .putString(Prefs.KEY_NAME1, name1)
            .putString(Prefs.KEY_NAME2, name2)
            .apply()
        refreshPreview()
        CoCWidgetProvider.updateAllWidgets(this)
        RefreshScheduler.schedule(this)
        statusText.setText(R.string.saved)
    }

    /** Same rows as the widget: in-progress upgrades with live remaining
     * times, then the Helpers and Exported lines, per account. */
    private fun refreshPreview() {
        val accounts = Prefs.loadAccounts(this)
        if (accounts.isEmpty()) {
            previewText.setText(R.string.no_data)
            return
        }
        val sb = StringBuilder()
        for (account in accounts) {
            sb.append(account.displayName)
            if (account.townHallLevel > 0) {
                sb.append("  •  TH").append(account.townHallLevel)
            }
            sb.append('\n')
            var any = false
            for ((title, items) in account.sections) {
                if (items.isEmpty()) continue
                any = true
                sb.append(title).append('\n')
                for (item in items) {
                    sb.append("  ").append(item.name)
                        .append(" → ").append(item.targetLevel)
                        .append("  ").append(formatDuration(item.remainingSeconds))
                        .append('\n')
                }
            }
            if (!any) {
                sb.append("  No upgrades in progress\n")
            }
            if (account.helpersReadySeconds > 0) {
                sb.append("  Helpers ready in ")
                    .append(formatDuration(account.helpersReadySeconds)).append('\n')
            } else {
                sb.append("  Helpers ready now\n")
            }
            sb.append("  Exported ").append(formatExported(account.exportedEpoch))
                .append("\n\n")
        }
        previewText.text = sb.toString().trimEnd()
    }
}
