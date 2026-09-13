package com.meditracker.app.alarms;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.meditracker.app.R;
import com.meditracker.app.ui.AlarmActivity;
import com.meditracker.app.ui.LocaleManager;

public class MedicineAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !MedicineAlarmScheduler.ACTION_FIRE.equals(intent.getAction())) return;
        String medicineId = intent.getStringExtra(MedicineAlarmScheduler.EXTRA_MEDICINE_ID);
        if (medicineId == null || medicineId.isEmpty()) return;
        MedicineAlarmScheduler.ensureChannel(context);

        Intent open = new Intent(context, AlarmActivity.class)
            .putExtra(MedicineAlarmScheduler.EXTRA_MEDICINE_ID, medicineId)
            .putExtra(MedicineAlarmScheduler.EXTRA_TITLE, intent.getStringExtra(MedicineAlarmScheduler.EXTRA_TITLE))
            .putExtra(MedicineAlarmScheduler.EXTRA_BODY, intent.getStringExtra(MedicineAlarmScheduler.EXTRA_BODY))
            .putExtra(MedicineAlarmScheduler.EXTRA_REASON, intent.getStringExtra(MedicineAlarmScheduler.EXTRA_REASON))
            .putExtra(MedicineAlarmScheduler.EXTRA_ALERT_ID, intent.getStringExtra(MedicineAlarmScheduler.EXTRA_ALERT_ID))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, medicineId.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        String title = intent.getStringExtra(MedicineAlarmScheduler.EXTRA_TITLE);
        String body = intent.getStringExtra(MedicineAlarmScheduler.EXTRA_BODY);
        Context localized = LocaleManager.wrap(context);
        if ("caregiver_reminder".equals(intent.getStringExtra(MedicineAlarmScheduler.EXTRA_REASON))) body = localized.getString(R.string.medicine_due_now);
        String displayTitle = title == null || title.isEmpty() ? localized.getString(R.string.medicine_reminder) : title;
        String displayBody = body == null || body.isEmpty() ? localized.getString(R.string.medicine_due_now) : body;
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, MedicineAlarmScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_meditracker).setContentTitle(displayTitle)
            .setContentText(displayBody)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(displayBody)).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(sound).setVibrate(new long[]{0, 700, 250, 700, 250, 1000}).setDefaults(Notification.DEFAULT_LIGHTS)
            .setContentIntent(pending).setFullScreenIntent(pending, true).setOngoing(true).setAutoCancel(false);
        try { NotificationManagerCompat.from(context).notify(MedicineAlarmScheduler.notificationTag(medicineId), 0, notification.build()); }
        catch (SecurityException ignored) {}
    }
}
