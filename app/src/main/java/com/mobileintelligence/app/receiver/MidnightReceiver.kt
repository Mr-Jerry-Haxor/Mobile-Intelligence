package com.mobileintelligence.app.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mobileintelligence.app.util.DateUtils
import com.mobileintelligence.app.worker.MidnightRolloverWorker

class MidnightReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MidnightReceiver"
        private const val REQUEST_CODE = 9001

        fun schedule(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, MidnightReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerTime = DateUtils.getMidnightTriggerTime()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                    )
                } else {
                    // Fallback: inexact alarm — will fire within a window around midnight
                    Log.w(TAG, "Exact alarm permission not granted, using inexact alarm")
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                )
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Trigger midnight rollover work
        MidnightRolloverWorker.enqueue(context)
        // Re-schedule for next midnight
        schedule(context)
    }
}
