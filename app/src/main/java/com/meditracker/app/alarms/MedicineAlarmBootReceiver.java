package com.meditracker.app.alarms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MedicineAlarmBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
            || "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(action)) {
            MedicineAlarmScheduler.ensureChannel(context); MedicineAlarmScheduler.restore(context);
        }
    }
}
