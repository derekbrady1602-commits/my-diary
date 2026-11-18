package ie.derek.snezzysdiary

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Android forgets alarms when it restarts, so set them all again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, "android.intent.action.QUICKBOOT_POWERON" ->
                AlarmScheduler.rescheduleAll(ctx)
        }
    }
}
