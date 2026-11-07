package com.towfiqul.cocwidget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class CoCWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return CoCViewsFactory(applicationContext)
    }
}

private sealed class Row {
    data class AccountHeader(val title: String) : Row()
    data class SectionHeader(val title: String) : Row()
    data class Upgrade(val item: UpgradeItem) : Row()
    data class Footer(val text: String) : Row()
}

private class CoCViewsFactory(private val context: Context) :
    RemoteViewsService.RemoteViewsFactory {

    private var rows: List<Row> = emptyList()

    override fun onCreate() {}

    override fun onDestroy() {}

    /** Rebuilds the row list from the stored JSON. All countdowns are
     * recomputed from the stored export timestamp at this moment, so every
     * re-render (15-minute WorkManager tick, boot, manual refresh) shows
     * live remaining times without needing fresh data. */
    override fun onDataSetChanged() {
        rows = buildRows()
    }

    private fun buildRows(): List<Row> {
        val out = mutableListOf<Row>()
        for (account in Prefs.loadAccounts(context)) {
            val thText = if (account.townHallLevel > 0) {
                "TH${account.townHallLevel}"
            } else {
                account.tag
            }
            out.add(Row.AccountHeader("${account.displayName}  •  $thText"))
            for ((title, items) in account.sections) {
                if (items.isEmpty()) continue
                out.add(Row.SectionHeader(title))
                for (item in items) {
                    out.add(Row.Upgrade(item))
                }
            }
            if (account.helpersReadySeconds > 0) {
                out.add(Row.Footer("Helpers ready in ${formatDuration(account.helpersReadySeconds)}"))
            } else {
                out.add(Row.Footer("Helpers ready now"))
            }
            out.add(Row.Footer("Exported ${formatExported(account.exportedEpoch)}"))
        }
        return out
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position < 0 || position >= rows.size) return null
        return when (val row = rows[position]) {
            is Row.AccountHeader -> {
                val views = RemoteViews(context.packageName, R.layout.widget_row_account)
                views.setTextViewText(R.id.row_account_title, row.title)
                views
            }
            is Row.SectionHeader -> {
                val views = RemoteViews(context.packageName, R.layout.widget_row_section)
                views.setTextViewText(R.id.row_section_title, row.title)
                views
            }
            is Row.Upgrade -> {
                val views = RemoteViews(context.packageName, R.layout.widget_row_upgrade)
                views.setTextViewText(R.id.row_upgrade_name, row.item.name)
                views.setTextViewText(R.id.row_upgrade_target, "→ ${row.item.targetLevel}")
                views.setTextViewText(R.id.row_upgrade_time, formatDuration(row.item.remainingSeconds))
                views
            }
            is Row.Footer -> {
                val views = RemoteViews(context.packageName, R.layout.widget_row_footer)
                views.setTextViewText(R.id.row_footer_text, row.text)
                views
            }
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 4

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
