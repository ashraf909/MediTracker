package com.meditracker.app.ui;

import android.content.Intent;
import android.os.Bundle;
import com.meditracker.app.data.SessionManager;

public class MainActivity extends BaseActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Route a saved role to its dashboard; otherwise show role selection.
        SessionManager session = new SessionManager(this);
        Class<?> target = RoleSelectionActivity.class;
        if (session.isLinked() && SessionManager.PATIENT.equals(session.role())) target = PatientHomeActivity.class;
        if (session.isLinked() && SessionManager.CAREGIVER.equals(session.role())) target = CaregiverDashboardActivity.class;
        startActivity(new Intent(this, target));
        finish();
    }
}
