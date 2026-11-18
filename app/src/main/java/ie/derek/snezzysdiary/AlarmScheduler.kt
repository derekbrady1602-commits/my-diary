package ie.derek.snezzysdiary

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps a list of appointment alarms and hands them to Android's AlarmManager.
 * The list is saved so the alarms can be set again after the device restarts.
 */
object AlarmScheduler {
    private const val PREFS = "diary_alarms"

    fun schedule(ctx: Context, id: String, at: Long, title: String, text: String) {
        val obj = JSONObject().put("at", at).put("title", title).put("text", text)
        prefs(ctx).edit().putString(id, obj.toString()).apply()
        arm(ctx, id, at, title, text)
    }

    fun cancel(ctx: Context, id: String) {
        alarmManager(ctx).cancel(pending(ctx, id, "", ""))
        prefs(ctx).edit().remove(id).apply()
    }

    fun list(ctx: Context): String = JSONArray(prefs(ctx).all.keys.toList()).toString()

    /** Called after a restart or an app update: sets every alarm that is still in the future. */
    fun rescheduleAll(ctx: Context) {
        val now = System.currentTimeMillis()
        val p = prefs(ctx)
        for ((id, value) in p.all) {
            try {
                val o = JSONObject(value as String)
                val at = o.getLong("at")
                if (at > now) arm(ctx, id, at, o.optString("title"), o.optString("text"))
                else p.edit().remove(id).apply()
            } catch (e: Exception) {
                p.edit().remove(id).apply()
            }
        }
    }

    fun forget(ctx: Context, id: String) { prefs(ctx).edit().remove(id).apply() }

    private fun arm(ctx: Context, id: String, at: Long, title: String, text: String) {
        val am = alarmManager(ctx)
        val pi = pending(ctx, id, title, text)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exactAllowed) {
            // An "alarm clock" alarm rings exactly on time and shows the alarm icon in the status bar
            val show = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pending(ctx: Context, id: String, title: String, text: String): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction("ie.derek.snezzysdiary.ALARM")
            .setData(Uri.parse("diary://alarm/" + Uri.encode(id)))   // makes every alarm unique
            .putExtra("id", id).putExtra("title", title).putExtra("text", text)
        return PendingIntent.getBroadcast(
            ctx, id.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun alarmManager(ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
}
