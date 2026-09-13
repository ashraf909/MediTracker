package com.meditracker.app.alarms;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationManagerCompat;
import com.meditracker.app.R;
import com.meditracker.app.model.MedicineDose;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

public final class MedicineAlarmScheduler {
    public static final String CHANNEL_ID = "medicine-alarms-native-v1";
    public static final String ACTION_FIRE = "com.meditracker.app.NATIVE_MEDICINE_ALARM";
    public static final String EXTRA_ID = "notificationId";
    public static final String EXTRA_MEDICINE_ID = "medicineId";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_BODY = "body";
    public static final String EXTRA_REASON = "reason";
    public static final String EXTRA_ALERT_ID = "alertId";
    private static final String PREFS = "meditracker_native_java_alarms";
    private static final String ALARMS = "alarms";

    private MedicineAlarmScheduler() {}

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, context.getString(R.string.medicine_channel_name), NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.medicine_channel_description));
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 700, 250, 700, 250, 1000});
        channel.enableLights(true);
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        channel.setSound(sound, new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).setUsage(AudioAttributes.USAGE_ALARM).build());
        manager.createNotificationChannel(channel);
    }

    public static synchronized void syncMedicines(Context context, List<MedicineDose> medicines) {
        try {
            JSONArray alarms = new JSONArray();
            Set<String> activeMedicineIds = new HashSet<>();
            for (MedicineDose medicine : medicines) if (!medicine.isTaken()) activeMedicineIds.add(medicine.getId());

            // Replace only normal daily alarms. A Firebase refresh must not erase a
            // 10-minute snooze that the patient has just requested.
            JSONArray stored = read(context);
            long now = System.currentTimeMillis();
            for (int index = 0; index < stored.length(); index++) {
                JSONObject saved = stored.optJSONObject(index);
                if (saved == null) continue;
                boolean validSnooze = "snooze".equals(saved.optString(EXTRA_REASON))
                    && saved.optLong("atMillis") > now
                    && activeMedicineIds.contains(saved.optString(EXTRA_MEDICINE_ID));
                if (validSnooze) alarms.put(saved);
                else cancel(context, saved.optInt(EXTRA_ID));
            }

            for (MedicineDose medicine : medicines) {
                if (medicine.isTaken()) continue;
                int[] time = parseTime(medicine.getTiming());
                if (time == null) continue;
                for (int day = 0; day < 30; day++) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.add(Calendar.DAY_OF_YEAR, day);
                    calendar.set(Calendar.HOUR_OF_DAY, time[0]);
                    calendar.set(Calendar.MINUTE, time[1]);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);
                    if (calendar.getTimeInMillis() <= now) continue;
                    String dayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
                    JSONObject alarm = alarmJson(context, medicine, calendar.getTimeInMillis(), "scheduled", stableId(medicine.getId() + dayKey));
                    schedule(context, alarm); alarms.put(alarm);
                }
            }
            save(context, alarms);
        } catch (Exception ignored) {}
    }

    public static synchronized void snooze(Context context, MedicineDose medicine, int minutes) {
        try {
            JSONArray existing = read(context);
            JSONArray stored = new JSONArray();
            long now = System.currentTimeMillis();
            for (int index = 0; index < existing.length(); index++) {
                JSONObject saved = existing.optJSONObject(index);
                if (saved == null) continue;
                boolean oldSnooze = medicine.getId().equals(saved.optString(EXTRA_MEDICINE_ID))
                    && "snooze".equals(saved.optString(EXTRA_REASON));
                if (oldSnooze) cancel(context, saved.optInt(EXTRA_ID));
                else if (saved.optLong("atMillis") > now) stored.put(saved);
            }
            JSONObject alarm = alarmJson(context, medicine, System.currentTimeMillis() + minutes * 60_000L, "snooze", stableId(medicine.getId() + "snooze"));
            schedule(context, alarm); stored.put(alarm); save(context, stored);
        } catch (Exception ignored) {}
    }

    public static synchronized void cancelMedicine(Context context, String medicineId) {
        try { save(context, removeMedicine(context, medicineId)); } catch (Exception ignored) {}
        NotificationManagerCompat.from(context).cancel(notificationTag(medicineId), 0);
    }

    public static synchronized void cancelAll(Context context) {
        try {
            JSONArray stored = read(context);
            for (int i = 0; i < stored.length(); i++) {
                JSONObject alarm = stored.optJSONObject(i);
                if (alarm == null) continue;
                cancel(context, alarm.optInt(EXTRA_ID));
                NotificationManagerCompat.from(context).cancel(notificationTag(alarm.optString(EXTRA_MEDICINE_ID)), 0);
            }
            save(context, new JSONArray());
        } catch (Exception ignored) {}
    }

    public static synchronized void restore(Context context) {
        try {
            JSONArray stored = read(context); JSONArray future = new JSONArray();
            for (int i = 0; i < stored.length(); i++) {
                JSONObject alarm = stored.optJSONObject(i);
                if (alarm != null && alarm.optLong("atMillis") > System.currentTimeMillis()) { schedule(context, alarm); future.put(alarm); }
            }
            save(context, future);
        } catch (Exception ignored) { save(context, new JSONArray()); }
    }

    public static boolean canScheduleExact(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();
    }

    public static boolean canUseFullScreen(Context context) {
        if (Build.VERSION.SDK_INT < 34) return true;
        return context.getSystemService(NotificationManager.class).canUseFullScreenIntent();
    }

    private static JSONObject alarmJson(Context context, MedicineDose medicine, long atMillis, String reason, int id) throws Exception {
        Context localized = com.meditracker.app.ui.LocaleManager.wrap(context);
        JSONObject alarm = new JSONObject();
        alarm.put(EXTRA_ID, id); alarm.put(EXTRA_MEDICINE_ID, medicine.getId());
        alarm.put(EXTRA_TITLE, medicine.displayName(localized).isEmpty() ? localized.getString(R.string.medicine_reminder) : medicine.displayName(localized));
        alarm.put(EXTRA_BODY, medicine.getTiming() + " · " + medicine.displayDosage(localized) + " · " + medicine.mealLabel(localized));
        alarm.put(EXTRA_REASON, reason); alarm.put("atMillis", atMillis); return alarm;
    }

    private static JSONArray removeMedicine(Context context, String medicineId) throws Exception {
        JSONArray stored = read(context); JSONArray kept = new JSONArray();
        for (int i = 0; i < stored.length(); i++) {
            JSONObject alarm = stored.optJSONObject(i); if (alarm == null) continue;
            if (medicineId.equals(alarm.optString(EXTRA_MEDICINE_ID))) cancel(context, alarm.optInt(EXTRA_ID));
            else if (alarm.optLong("atMillis") > System.currentTimeMillis()) kept.put(alarm);
        }
        return kept;
    }

    private static void schedule(Context context, JSONObject alarm) {
        int id = alarm.optInt(EXTRA_ID);
        PendingIntent pending = PendingIntent.getBroadcast(context, id, receiverIntent(context, alarm), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long when = alarm.optLong("atMillis");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (canScheduleExact(context)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
        } else manager.setExact(AlarmManager.RTC_WAKEUP, when, pending);
    }

    private static void cancel(Context context, int id) {
        PendingIntent pending = PendingIntent.getBroadcast(context, id, new Intent(context, MedicineAlarmReceiver.class).setAction(ACTION_FIRE), PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) { ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).cancel(pending); pending.cancel(); }
    }

    private static Intent receiverIntent(Context context, JSONObject alarm) {
        return new Intent(context, MedicineAlarmReceiver.class).setAction(ACTION_FIRE)
            .putExtra(EXTRA_ID, alarm.optInt(EXTRA_ID)).putExtra(EXTRA_MEDICINE_ID, alarm.optString(EXTRA_MEDICINE_ID))
            .putExtra(EXTRA_TITLE, alarm.optString(EXTRA_TITLE)).putExtra(EXTRA_BODY, alarm.optString(EXTRA_BODY))
            .putExtra(EXTRA_REASON, alarm.optString(EXTRA_REASON));
    }

    private static int[] parseTime(String value) {
        try {
            Date date = new SimpleDateFormat("hh:mm a", Locale.US).parse(value);
            Calendar calendar = Calendar.getInstance(); calendar.setTime(date);
            return new int[]{calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE)};
        } catch (Exception error) { return null; }
    }

    private static int stableId(String value) { int id = value.hashCode() & 0x7fffffff; return id == 0 ? 1 : id; }
    public static String notificationTag(String medicineId) { return "medicine-" + medicineId; }
    private static JSONArray read(Context context) throws Exception { return new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ALARMS, "[]")); }
    private static void save(Context context, JSONArray alarms) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ALARMS, alarms.toString()).apply(); }
}
