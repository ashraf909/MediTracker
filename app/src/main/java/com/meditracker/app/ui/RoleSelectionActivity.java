package com.meditracker.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.meditracker.app.R;
import com.meditracker.app.data.FirebaseRepository;
import com.meditracker.app.data.SessionManager;
import com.meditracker.app.model.FamilyState;

public class RoleSelectionActivity extends BaseActivity {
    private LinearLayout roleButtons, patientForm, caregiverForm;
    private TextView status;
    private Button createPatient, reconnectPatient, connectCaregiver;
    private final FirebaseRepository repository = FirebaseRepository.get();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);
        roleButtons = findViewById(R.id.roleButtons); patientForm = findViewById(R.id.patientForm); caregiverForm = findViewById(R.id.caregiverForm);
        status = findViewById(R.id.statusText); createPatient = findViewById(R.id.createPatientButton);
        reconnectPatient = findViewById(R.id.reconnectPatientButton); connectCaregiver = findViewById(R.id.connectCaregiverButton);
        findViewById(R.id.patientRoleButton).setOnClickListener(v -> showForm(true));
        findViewById(R.id.caregiverRoleButton).setOnClickListener(v -> showForm(false));
        findViewById(R.id.languageButton).setOnClickListener(v -> {
            LocaleManager.setLanguage(this, LocaleManager.isBangla(this) ? LocaleManager.ENGLISH : LocaleManager.BANGLA);
            recreate();
        });
        findViewById(R.id.backButton).setOnClickListener(v -> showRoles());
        createPatient.setOnClickListener(v -> createPatient());
        reconnectPatient.setOnClickListener(v -> reconnectPatient());
        connectCaregiver.setOnClickListener(v -> connectCaregiver());
    }

    private void showForm(boolean patient) {
        roleButtons.setVisibility(View.GONE); patientForm.setVisibility(patient ? View.VISIBLE : View.GONE);
        caregiverForm.setVisibility(patient ? View.GONE : View.VISIBLE); findViewById(R.id.backButton).setVisibility(View.VISIBLE); status.setText("");
    }
    private void showRoles() {
        roleButtons.setVisibility(View.VISIBLE); patientForm.setVisibility(View.GONE); caregiverForm.setVisibility(View.GONE);
        findViewById(R.id.backButton).setVisibility(View.GONE); status.setText("");
    }

    private String text(int id) { return ((EditText) findViewById(id)).getText().toString().trim(); }
    private void working(Button button, boolean value) { button.setEnabled(!value); status.setText(value ? getString(R.string.connecting_securely) : ""); }

    private void createPatient() {
        String name = text(R.id.patientNameInput); if (name.isEmpty()) { status.setText(R.string.error_patient_name); return; }
        long age; try { age = Long.parseLong(text(R.id.patientAgeInput)); }
        catch (Exception ignored) { status.setText(R.string.error_patient_age); return; }
        if (age < 1 || age > 120) { status.setText(R.string.error_valid_age); return; }
        working(createPatient, true);
        repository.createPatient(name, age, new FirebaseRepository.Result<String>() {
            @Override public void onSuccess(String code) {
                new SessionManager(RoleSelectionActivity.this).save(SessionManager.PATIENT, code, "");
                startActivity(new Intent(RoleSelectionActivity.this, PatientHomeActivity.class)); finish();
            }
            @Override public void onError(Exception error) { working(createPatient, false); status.setText(friendly(error)); }
        });
    }

    private void reconnectPatient() {
        String code = text(R.id.patientCodeInput); if (SessionManager.normalizeCode(code).length() != 8) { status.setText(R.string.error_complete_key); return; }
        working(reconnectPatient, true);
        repository.reconnectPatient(code, new FirebaseRepository.Result<FamilyState>() {
            @Override public void onSuccess(FamilyState value) {
                new SessionManager(RoleSelectionActivity.this).save(SessionManager.PATIENT, code, "");
                startActivity(new Intent(RoleSelectionActivity.this, PatientHomeActivity.class)); finish();
            }
            @Override public void onError(Exception error) { working(reconnectPatient, false); status.setText(friendly(error)); }
        });
    }

    private void connectCaregiver() {
        String name = text(R.id.caregiverNameInput), relation = text(R.id.relationInput), phone = text(R.id.phoneInput), code = text(R.id.caregiverCodeInput);
        if (name.isEmpty() || relation.isEmpty() || SessionManager.normalizeCode(code).length() != 8) { status.setText(R.string.error_caregiver_form); return; }
        working(connectCaregiver, true);
        repository.joinCaregiver(code, name, relation, phone, new FirebaseRepository.Result<FamilyState>() {
            @Override public void onSuccess(FamilyState value) {
                new SessionManager(RoleSelectionActivity.this).saveCaregiver(code, name, relation, phone);
                startActivity(new Intent(RoleSelectionActivity.this, CaregiverDashboardActivity.class)); finish();
            }
            @Override public void onError(Exception error) { working(connectCaregiver, false); status.setText(friendly(error)); }
        });
    }

    private String friendly(Exception error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        if (message.contains("INVALID_CODE")) return getString(R.string.error_key_not_found);
        if (message.contains("PAIRING_DISABLED")) return getString(R.string.error_pairing_disabled);
        return getString(R.string.error_connect);
    }
}
