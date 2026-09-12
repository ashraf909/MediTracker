package com.meditracker.app.ui;

import android.Manifest;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.meditracker.app.R;
import com.meditracker.app.alarms.MedicineAlarmScheduler;
import com.meditracker.app.data.FirebaseRepository;
import com.meditracker.app.data.SessionManager;
import com.meditracker.app.model.ActivityLog;
import com.meditracker.app.model.Caregiver;
import com.meditracker.app.model.FamilyState;
import com.meditracker.app.model.MedicineDose;
import com.meditracker.app.model.Patient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class PatientHomeActivity extends BaseActivity {
    private final FirebaseRepository repository = FirebaseRepository.get();
    private SessionManager session;
    private ListenerRegistration registration;
    private MedicineAdapter adapter;
    private Patient patient;
    private List<Caregiver> caregivers = new ArrayList<>();
    private List<MedicineDose> medicines = new ArrayList<>();
    private List<ActivityLog> logs = new ArrayList<>();
    private String handledAlertId = "";
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), allowed -> {});

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_patient_home);
        session = new SessionManager(this);
        if (!session.isLinked()) { goToOnboarding(); return; }
        ((TextView) findViewById(R.id.familyCodeText)).setText(getString(R.string.family_code_format, session.familyCode()));
        DateStripHelper.bind(this, (LinearLayout) findViewById(R.id.dateStripContainer));
        RecyclerView list = findViewById(R.id.medicineList); list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MedicineAdapter(true, new MedicineAdapter.Actions() {
            @Override public void onPrimary(MedicineDose dose) { markTaken(dose); }
            @Override public void onEdit(MedicineDose dose) {}
            @Override public void onDelete(MedicineDose dose) {}
        }); list.setAdapter(adapter);
        findViewById(R.id.signOutButton).setOnClickListener(v -> signOut());
        findViewById(R.id.settingsButton).setOnClickListener(v -> showProfileDialog());
        findViewById(R.id.copyFamilyKeyButton).setOnClickListener(v -> copyFamilyKey());
        findViewById(R.id.testNextAlarmButton).setOnClickListener(v -> {
            MedicineDose next = nextPending(); if (next == null) Toast.makeText(this, R.string.no_more_medicine, Toast.LENGTH_SHORT).show(); else openAlarm(next, "test");
        });
        requestAlarmAccess();
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> repository.savePatientPushToken(session.familyCode(), token));
        listen();
    }

    private void listen() {
        repository.listenFamily(session.familyCode(), new FirebaseRepository.FamilyListener() {
            @Override public void onData(FamilyState state) {
                patient = state.getPatient(); caregivers = new ArrayList<>(state.getCaregivers());
                medicines = new ArrayList<>(state.getMedicines()); logs = new ArrayList<>(state.getLogs());
                Collections.sort(medicines, (first, second) -> Integer.compare(timeMinutes(first.getTiming()), timeMinutes(second.getTiming())));
                if (session.isNewDay()) resetForNewDay();
                render(); MedicineAlarmScheduler.syncMedicines(PatientHomeActivity.this, medicines);
                Map<String, Object> alert = state.getAlert();
                if (alert != null && "ACTIVE".equals(String.valueOf(alert.get("status")))) {
                    String id = String.valueOf(alert.get("id")); String medicineId = String.valueOf(alert.get("medicineId"));
                    if (!id.equals(handledAlertId)) { MedicineDose dose = findDose(medicineId); if (dose != null) { handledAlertId = id; openAlarm(dose, "caregiver_reminder"); } }
                }
            }
            @Override public void onError(Exception error) { Toast.makeText(PatientHomeActivity.this, R.string.error_sync_family, Toast.LENGTH_LONG).show(); }
        }, new FirebaseRepository.Result<ListenerRegistration>() {
            @Override public void onSuccess(ListenerRegistration value) { registration = value; }
            @Override public void onError(Exception error) { Toast.makeText(PatientHomeActivity.this, R.string.error_phone_not_linked_family, Toast.LENGTH_LONG).show(); }
        });
    }

    private void render() {
        String name = patient == null || patient.getName().isEmpty() ? "" : ", " + patient.getName();
        ((TextView) findViewById(R.id.patientGreeting)).setText(getString(R.string.greeting_patient, name));
        ((TextView) findViewById(R.id.connectedCaregiverCount)).setText(String.valueOf(caregivers.size()));
        TextView caregiverNames = findViewById(R.id.connectedCaregiversText);
        if (caregivers.isEmpty()) caregiverNames.setText(R.string.no_caregiver_connected);
        else {
            StringBuilder summary = new StringBuilder();
            for (int i = 0; i < caregivers.size() && i < 3; i++) {
                Caregiver caregiver = caregivers.get(i);
                if (i > 0) summary.append("  •  ");
                summary.append(caregiver.getName().isEmpty() ? getString(R.string.caregiver_fallback) : caregiver.getName());
                if (!caregiver.getRelation().isEmpty()) summary.append(" (").append(caregiver.getRelation()).append(")");
            }
            if (caregivers.size() > 3) summary.append("  ").append(getString(R.string.more_count, caregivers.size() - 3));
            caregiverNames.setText(summary.toString());
        }
        adapter.submit(medicines);
        findViewById(R.id.emptyText).setVisibility(medicines.isEmpty() ? View.VISIBLE : View.GONE);
        MedicineDose next = nextPending(); TextView nextName = findViewById(R.id.nextMedicineName); TextView detail = findViewById(R.id.nextMedicineDetail);
        if (next == null) { nextName.setText(R.string.no_more_medicine); detail.setText(medicines.isEmpty() ? R.string.waiting_for_schedule : R.string.all_medicine_taken); }
        else { nextName.setText(next.displayName(this)); detail.setText(getString(R.string.next_medicine_summary, next.getTiming(), next.displayDosage(this), next.mealLabel(this))); }
    }

    private void markTaken(MedicineDose dose) {
        repository.confirmTaken(session.familyCode(), dose.getId(), patient == null ? "Patient" : patient.getName(), new FirebaseRepository.Result<Void>() {
            @Override public void onSuccess(Void value) { MedicineAlarmScheduler.cancelMedicine(PatientHomeActivity.this, dose.getId()); Toast.makeText(PatientHomeActivity.this, R.string.medicine_taken, Toast.LENGTH_SHORT).show(); }
            @Override public void onError(Exception error) { Toast.makeText(PatientHomeActivity.this, R.string.error_update_firebase, Toast.LENGTH_LONG).show(); }
        });
    }

    private void showProfileDialog() {
        if (patient == null) { Toast.makeText(this, R.string.profile_loading_patient, Toast.LENGTH_SHORT).show(); return; }
        View content = getLayoutInflater().inflate(R.layout.dialog_patient_profile, null);
        EditText nameInput = content.findViewById(R.id.editPatientName);
        EditText ageInput = content.findViewById(R.id.editPatientAge);
        nameInput.setText(patient.getName());
        ageInput.setText(String.valueOf(patient.getAge()));
        MaterialButtonToggleGroup languageGroup = content.findViewById(R.id.languageGroup);
        languageGroup.check(LocaleManager.isBangla(this) ? R.id.languageBanglaButton : R.id.languageEnglishButton);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(content).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            long age;
            try { age = Long.parseLong(ageInput.getText().toString().trim()); }
            catch (Exception error) { ageInput.setError(getString(R.string.enter_valid_age)); return; }
            if (name.isEmpty()) { nameInput.setError(getString(R.string.enter_patient_name)); return; }
            if (age < 1 || age > 120) { ageInput.setError(getString(R.string.age_range)); return; }
            Patient updated = new Patient();
            updated.setId(patient.getId()); updated.setName(name); updated.setAge(age);
            updated.setRelation(patient.getRelation()); updated.setFamilyCode(patient.getFamilyCode());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            String nextLanguage = languageGroup.getCheckedButtonId() == R.id.languageBanglaButton ? LocaleManager.BANGLA : LocaleManager.ENGLISH;
            boolean languageChanged = !nextLanguage.equals(LocaleManager.language(PatientHomeActivity.this));
            repository.updatePatientProfile(session.familyCode(), updated, new FirebaseRepository.Result<Void>() {
                @Override public void onSuccess(Void value) { Toast.makeText(PatientHomeActivity.this, R.string.profile_updated, Toast.LENGTH_SHORT).show(); dialog.dismiss(); if (languageChanged) { LocaleManager.setLanguage(PatientHomeActivity.this, nextLanguage); recreate(); } }
                @Override public void onError(Exception error) { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); Toast.makeText(PatientHomeActivity.this, R.string.profile_update_failed, Toast.LENGTH_LONG).show(); }
            });
        }));
        dialog.show();
    }

    private void copyFamilyKey() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.family_key), session.familyCode()));
        Toast.makeText(this, R.string.family_key_copied, Toast.LENGTH_SHORT).show();
    }

    private void resetForNewDay() {
        boolean changed = false;
        for (MedicineDose dose : medicines) if (dose.isTaken() && dose.getTakenDate() != null && !FirebaseRepository.dayKey().equals(dose.getTakenDate())) { dose.setStatus("PENDING"); dose.setTakenAt(null); dose.setTakenDate(null); changed = true; }
        if (changed && patient != null) repository.saveSnapshot(session.familyCode(), patient, medicines, logs, silentResult());
    }

    private void openAlarm(MedicineDose dose, String reason) {
        startActivity(new Intent(this, AlarmActivity.class).putExtra(MedicineAlarmScheduler.EXTRA_MEDICINE_ID, dose.getId()).putExtra(MedicineAlarmScheduler.EXTRA_REASON, reason));
    }

    private void requestAlarmAccess() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !MedicineAlarmScheduler.canScheduleExact(this)) {
            try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) {}
        }
        if (Build.VERSION.SDK_INT >= 34 && !MedicineAlarmScheduler.canUseFullScreen(this)) {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) {}
        }
    }

    private MedicineDose nextPending() { for (MedicineDose dose : medicines) if (!dose.isTaken()) return dose; return null; }
    private MedicineDose findDose(String id) { for (MedicineDose dose : medicines) if (dose.getId().equals(id)) return dose; return null; }
    private int timeMinutes(String value) { try { java.util.Date d = new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US).parse(value); java.util.Calendar c = java.util.Calendar.getInstance(); c.setTime(d); return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE); } catch (Exception e) { return 9999; } }
    private FirebaseRepository.Result<Void> silentResult() { return new FirebaseRepository.Result<Void>() { public void onSuccess(Void v) {} public void onError(Exception e) {} }; }

    private void signOut() {
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> repository.removePatientPushToken(session.familyCode(), token));
        MedicineAlarmScheduler.cancelAll(this); session.clear(); goToOnboarding();
    }
    private void goToOnboarding() { startActivity(new Intent(this, RoleSelectionActivity.class)); finish(); }
    @Override protected void onDestroy() { if (registration != null) registration.remove(); super.onDestroy(); }
}
