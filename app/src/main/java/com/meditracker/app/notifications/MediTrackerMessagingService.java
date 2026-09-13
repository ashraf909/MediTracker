package com.meditracker.app.notifications;

import android.content.Intent;
import androidx.annotation.NonNull;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.meditracker.app.alarms.MedicineAlarmReceiver;
import com.meditracker.app.alarms.MedicineAlarmScheduler;
import com.meditracker.app.data.FirebaseRepository;
import com.meditracker.app.data.SessionManager;
import com.meditracker.app.model.FamilyState;
import java.util.Map;

public class MediTrackerMessagingService extends FirebaseMessagingService {
    @Override public void onMessageReceived(@NonNull RemoteMessage message) {
        Map<String, String> data = message.getData();
        if ("schedule_updated".equals(data.get("type"))) {
            SessionManager session = new SessionManager(this);
            String familyCode = data.get("familyCode");
            if (SessionManager.PATIENT.equals(session.role()) && session.isLinked()
                && SessionManager.normalizeCode(session.familyCode()).equals(SessionManager.normalizeCode(familyCode))) {
                FirebaseRepository.get().fetchFamily(familyCode, new FirebaseRepository.Result<FamilyState>() {
                    @Override public void onSuccess(FamilyState family) { MedicineAlarmScheduler.syncMedicines(MediTrackerMessagingService.this, family.getMedicines()); }
                    @Override public void onError(Exception ignored) {}
                });
            }
            return;
        }
        if (!"caregiver_reminder".equals(data.get("type")) || data.get("medicineId") == null) return;
        int id = Math.abs((data.get("medicineId") + data.get("alertId")).hashCode());
        Intent alarm = new Intent(this, MedicineAlarmReceiver.class).setAction(MedicineAlarmScheduler.ACTION_FIRE)
            .putExtra(MedicineAlarmScheduler.EXTRA_ID, id).putExtra(MedicineAlarmScheduler.EXTRA_MEDICINE_ID, data.get("medicineId"))
            .putExtra(MedicineAlarmScheduler.EXTRA_TITLE, data.get("title")).putExtra(MedicineAlarmScheduler.EXTRA_BODY, data.get("body"))
            .putExtra(MedicineAlarmScheduler.EXTRA_REASON, "caregiver_reminder").putExtra(MedicineAlarmScheduler.EXTRA_ALERT_ID, data.get("alertId"));
        sendBroadcast(alarm);
    }

    @Override public void onNewToken(@NonNull String token) {
        SessionManager session = new SessionManager(this);
        if (SessionManager.PATIENT.equals(session.role()) && session.isLinked()) FirebaseRepository.get().savePatientPushToken(session.familyCode(), token);
    }
}
