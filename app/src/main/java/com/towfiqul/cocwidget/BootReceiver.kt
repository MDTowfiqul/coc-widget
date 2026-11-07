package com.towfiqul.cocwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After a reboot the periodic refresh schedule is gone, so re-create it
 * and re-render the widget once. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            RefreshScheduler.schedule(context)
            CoCWidgetProvider.updateAllWidgets(context)
        }
    }
}
