package com.meditracker.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SessionManager {
    public static final String PATIENT = "PATIENT";
    public static final String CAREGIVER = "CAREGIVER";
    private static final String PREFS = "meditracker_session_native";
    private final SharedPreferences preferences;

    public SessionManager(Context context) { preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    // Save lightweight local routing data; shared medical data remains in Firestore.
    public void save(String role, String familyCode, String caregiverName) {
        preferences.edit().putString("role", role).putString("familyCode", formatCode(familyCode))
            .putString("caregiverName", caregiverName == null ? "" : caregiverName).apply();
    }

    // Save the caregiver's local role and form values for future app launches.
    public void saveCaregiver(String familyCode, String name, String relation, String phone) {
        preferences.edit().putString("role", CAREGIVER).putString("familyCode", formatCode(familyCode))
            .putString("caregiverName", name == null ? "" : name.trim())
            .putString("caregiverRelation", relation == null ? "" : relation.trim())
            .putString("caregiverPhone", phone == null ? "" : phone.trim()).apply();
    }

    public String role() { return preferences.getString("role", ""); }
    public String familyCode() { return preferences.getString("familyCode", ""); }
    public String caregiverName() { return preferences.getString("caregiverName", ""); }
    public String caregiverRelation() { return preferences.getString("caregiverRelation", ""); }
    public String caregiverPhone() { return preferences.getString("caregiverPhone", ""); }
    public void updateCaregiverName(String name) {
        preferences.edit().putString("caregiverName", name == null ? "" : name.trim()).apply();
    }
    public void updateCaregiverProfile(String name, String relation, String phone) {
        preferences.edit().putString("caregiverName", name == null ? "" : name.trim())
            .putString("caregiverRelation", relation == null ? "" : relation.trim())
            .putString("caregiverPhone", phone == null ? "" : phone.trim()).apply();
    }
    // A local session is usable only when it has a role and a complete family key.
    public boolean isLinked() { return !role().isEmpty() && normalizeCode(familyCode()).length() == 8; }
    public void clear() { preferences.edit().clear().apply(); }

    // Compare the last local visit date so yesterday's taken doses can be reset.
    public boolean isNewDay() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String previous = preferences.getString("lastDay", "");
        preferences.edit().putString("lastDay", today).apply();
        return !previous.isEmpty() && !today.equals(previous);
    }

    // Convert a displayed key such as ABCD-1234 into Firestore ID ABCD1234.
    public static String normalizeCode(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    // Add a hyphen to an eight-character key for easier reading and sharing.
    public static String formatCode(String value) {
        String clean = normalizeCode(value);
        return clean.length() > 4 ? clean.substring(0, Math.min(4, clean.length())) + "-" + clean.substring(4, Math.min(8, clean.length())) : clean;
    }
}
