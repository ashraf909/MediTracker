package com.meditracker.app.ui;

import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.meditracker.app.R;
import com.meditracker.app.data.FirebaseRepository;
import com.meditracker.app.data.SessionManager;
import com.meditracker.app.model.ActivityLog;
import com.meditracker.app.model.Caregiver;
import com.meditracker.app.model.FamilyState;
import com.meditracker.app.model.MedicineDose;
import java.util.Locale;
import com.meditracker.app.model.Patient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class CaregiverDashboardActivity extends BaseActivity {
    private final FirebaseRepository repository = FirebaseRepository.get();
    private SessionManager session; private ListenerRegistration registration; private MedicineAdapter adapter;
    private Patient patient; private Caregiver currentCaregiver;
    private List<Caregiver> caregivers = new ArrayList<>();
    private List<MedicineDose> medicines = new ArrayList<>(); private List<ActivityLog> logs = new ArrayList<>(); private int caregiverCount;
    private boolean caregiverRepairRequested;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_caregiver_dashboard); session = new SessionManager(this);
        if (!session.isLinked()) { goToOnboarding(); return; }
        DateStripHelper.bind(this, (LinearLayout) findViewById(R.id.dateStripContainer));
        RecyclerView list = findViewById(R.id.medicineList); list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MedicineAdapter(false, new MedicineAdapter.Actions() {
            @Override public void onPrimary(MedicineDose dose) { sendReminder(dose); }
            @Override public void onEdit(MedicineDose dose) { startActivity(new Intent(CaregiverDashboardActivity.this, AddMedicineActivity.class).putExtra("medicine", dose)); }
            @Override public void onDelete(MedicineDose dose) { confirmDelete(dose); }
        }); list.setAdapter(adapter);
        findViewById(R.id.addMedicineButton).setOnClickListener(v -> startActivity(new Intent(this, AddMedicineActivity.class)));
        findViewById(R.id.resetButton).setOnClickListener(v -> resetToday());
        findViewById(R.id.settingsButton).setOnClickListener(v -> showProfileDialog());
        findViewById(R.id.copyFamilyKeyButton).setOnClickListener(v -> copyFamilyKey());
        findViewById(R.id.signOutButton).setOnClickListener(v -> { session.clear(); goToOnboarding(); });
        listen();
    }

    private void listen() {
        repository.listenFamily(session.familyCode(), new FirebaseRepository.FamilyListener() {
            @Override public void onData(FamilyState state) {
                patient = state.getPatient(); caregivers = new ArrayList<>(state.getCaregivers());
                medicines = new ArrayList<>(state.getMedicines()); logs = new ArrayList<>(state.getLogs()); caregiverCount = caregivers.size();
                currentCaregiver = findCurrentCaregiver();
                if (currentCaregiver == null && !caregiverRepairRequested) repairMissingCaregiverProfile();
                Collections.sort(medicines, (first, second) -> first.getTiming().compareTo(second.getTiming())); render();
            }
            @Override public void onError(Exception error) { Toast.makeText(CaregiverDashboardActivity.this, R.string.error_sync_family, Toast.LENGTH_LONG).show(); }
        }, new FirebaseRepository.Result<ListenerRegistration>() {
            @Override public void onSuccess(ListenerRegistration value) { registration = value; }
            @Override public void onError(Exception error) { Toast.makeText(CaregiverDashboardActivity.this, R.string.error_phone_not_linked_family, Toast.LENGTH_LONG).show(); }
        });
    }

    private void render() {
        String patientName = patient == null ? getString(R.string.patient_fallback) : patient.getName();
        ((TextView) findViewById(R.id.greetingText)).setText(getString(R.string.caregiver_greeting, session.caregiverName().isEmpty() ? getString(R.string.caregiver_fallback) : session.caregiverName()));
        ((TextView) findViewById(R.id.patientStatusText)).setText(getString(R.string.patient_update, patientName));
        ((TextView) findViewById(R.id.patientNameText)).setText(patientName);
        ((TextView) findViewById(R.id.patientInitialText)).setText(patientName.isEmpty() ? getString(R.string.patient_fallback).substring(0, 1) : patientName.substring(0, 1).toUpperCase(Locale.getDefault()));
        String relation = currentCaregiver == null || currentCaregiver.getRelation().isEmpty() ? getString(R.string.caregiver_fallback) : currentCaregiver.getRelation();
        ((TextView) findViewById(R.id.patientMetaText)).setText(patient == null ? getString(R.string.patient_meta_unknown, relation) : getString(R.string.patient_meta, patient.getAge(), relation));
        ((TextView) findViewById(R.id.familyCodeText)).setText(session.familyCode());
        ((TextView) findViewById(R.id.caregiverCountText)).setText(getResources().getQuantityString(R.plurals.caregivers_linked, caregiverCount, caregiverCount));
        int taken = 0; for (MedicineDose dose : medicines) if (dose.isTaken()) taken++;
        ((TextView) findViewById(R.id.progressText)).setText(getString(R.string.progress_format, taken, medicines.size()));
        adapter.submit(medicines); findViewById(R.id.emptyText).setVisibility(medicines.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private Caregiver findCurrentCaregiver() {
        String userId = repository.currentUserId();
        for (Caregiver caregiver : caregivers) if (!userId.isEmpty() && userId.equals(caregiver.getId())) return caregiver;
        for (Caregiver caregiver : caregivers) if (caregiver.getName().equalsIgnoreCase(session.caregiverName())) return caregiver;
        return null;
    }

    private void repairMissingCaregiverProfile() {
        caregiverRepairRequested = true;
        String name = session.caregiverName().isEmpty() ? getString(R.string.caregiver_fallback) : session.caregiverName();
        String relation = session.caregiverRelation().isEmpty() ? "Caregiver" : session.caregiverRelation();
        repository.joinCaregiver(session.familyCode(), name, relation, session.caregiverPhone(), new FirebaseRepository.Result<FamilyState>() {
            @Override public void onSuccess(FamilyState value) {}
            @Override public void onError(Exception error) { caregiverRepairRequested = false; }
        });
    }

    private void showProfileDialog() {
        if (currentCaregiver == null) { Toast.makeText(this, R.string.profile_loading_caregiver, Toast.LENGTH_SHORT).show(); return; }
        View content = getLayoutInflater().inflate(R.layout.dialog_caregiver_profile, null);
        EditText nameInput = content.findViewById(R.id.editCaregiverName);
        EditText relationInput = content.findViewById(R.id.editCaregiverRelation);
        EditText phoneInput = content.findViewById(R.id.editCaregiverPhone);
        nameInput.setText(currentCaregiver.getName()); relationInput.setText(currentCaregiver.getRelation()); phoneInput.setText(currentCaregiver.getPhone());
        MaterialButtonToggleGroup languageGroup = content.findViewById(R.id.languageGroup);
        languageGroup.check(LocaleManager.isBangla(this) ? R.id.languageBanglaButton : R.id.languageEnglishButton);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(content).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String relation = relationInput.getText().toString().trim();
            if (name.isEmpty()) { nameInput.setError(getString(R.string.enter_your_name)); return; }
            if (relation.isEmpty()) { relationInput.setError(getString(R.string.enter_your_relation)); return; }
            Caregiver updated = new Caregiver();
            updated.setId(currentCaregiver.getId()); updated.setName(name); updated.setRelation(relation);
            updated.setPhone(phoneInput.getText().toString().trim()); updated.setLocation(currentCaregiver.getLocation());
            updated.setPrimary(currentCaregiver.isPrimary()); updated.setJoinedAt(currentCaregiver.getJoinedAt());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            String nextLanguage = languageGroup.getCheckedButtonId() == R.id.languageBanglaButton ? LocaleManager.BANGLA : LocaleManager.ENGLISH;
            boolean languageChanged = !nextLanguage.equals(LocaleManager.language(CaregiverDashboardActivity.this));
            repository.updateCaregiverProfile(session.familyCode(), updated, new FirebaseRepository.Result<Void>() {
                @Override public void onSuccess(Void value) { session.updateCaregiverProfile(name, relation, updated.getPhone()); Toast.makeText(CaregiverDashboardActivity.this, R.string.profile_updated, Toast.LENGTH_SHORT).show(); dialog.dismiss(); if (languageChanged) { LocaleManager.setLanguage(CaregiverDashboardActivity.this, nextLanguage); recreate(); } }
                @Override public void onError(Exception error) { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); Toast.makeText(CaregiverDashboardActivity.this, R.string.profile_update_failed, Toast.LENGTH_LONG).show(); }
            });
        }));
        dialog.show();
    }

    private void sendReminder(MedicineDose dose) {
        repository.sendReminder(session.familyCode(), dose, new FirebaseRepository.Result<Void>() {
            @Override public void onSuccess(Void value) { runOnUiThread(() -> Toast.makeText(CaregiverDashboardActivity.this, R.string.reminder_sent, Toast.LENGTH_SHORT).show()); }
            @Override public void onError(Exception error) { runOnUiThread(() -> Toast.makeText(CaregiverDashboardActivity.this, R.string.reminder_failed_simple, Toast.LENGTH_LONG).show()); }
        });
    }

    private void confirmDelete(MedicineDose dose) {
        new AlertDialog.Builder(this).setTitle(R.string.delete_medicine_question).setMessage(getString(R.string.delete_medicine_message, dose.displayName(this), dose.getTiming()))
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete, (dialog, which) -> {
                List<MedicineDose> next = new ArrayList<>(medicines);
                Iterator<MedicineDose> iterator = next.iterator();
                while (iterator.hasNext()) if (iterator.next().getId().equals(dose.getId())) iterator.remove();
                ActivityLog log = new ActivityLog("log_" + System.currentTimeMillis(), FirebaseRepository.displayTime(), "Medicine removed", dose.getName() + " was removed.", "warning");
                List<ActivityLog> nextLogs = withLog(log); repository.saveSnapshot(session.familyCode(), patient, next, nextLogs, toastResult(R.string.medicine_deleted));
            }).show();
    }

    private void resetToday() {
        List<MedicineDose> next = new ArrayList<>(medicines);
        for (MedicineDose dose : next) { dose.setStatus("PENDING"); dose.setTakenAt(null); dose.setTakenDate(null); }
        ActivityLog log = new ActivityLog("log_" + System.currentTimeMillis(), FirebaseRepository.displayTime(), "Day reset", "All medicine doses were reset to pending.", "info");
        repository.saveSnapshot(session.familyCode(), patient, next, withLog(log), toastResult(R.string.doses_reset));
    }

    private List<ActivityLog> withLog(ActivityLog entry) { List<ActivityLog> next = new ArrayList<>(); next.add(entry); next.addAll(logs); return next.size() > 50 ? new ArrayList<>(next.subList(0, 50)) : next; }
    private FirebaseRepository.Result<Void> toastResult(int success) { return new FirebaseRepository.Result<Void>() { public void onSuccess(Void v) { repository.notifyScheduleChanged(session.familyCode()); Toast.makeText(CaregiverDashboardActivity.this, success, Toast.LENGTH_SHORT).show(); } public void onError(Exception e) { Toast.makeText(CaregiverDashboardActivity.this, R.string.error_firebase_update, Toast.LENGTH_LONG).show(); } }; }
    private void copyFamilyKey() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.family_key), session.familyCode()));
        Toast.makeText(this, R.string.family_key_copied, Toast.LENGTH_SHORT).show();
    }
    private void goToOnboarding() { startActivity(new Intent(this, RoleSelectionActivity.class)); finish(); }
    @Override protected void onDestroy() { if (registration != null) registration.remove(); super.onDestroy(); }
}
