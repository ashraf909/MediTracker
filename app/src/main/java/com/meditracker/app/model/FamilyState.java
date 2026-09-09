package com.meditracker.app.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FamilyState {
    private final Patient patient;
    private final List<Caregiver> caregivers;
    private final List<MedicineDose> medicines;
    private final List<ActivityLog> logs;
    private final Map<String, Object> alert;

    public FamilyState(Patient patient, List<Caregiver> caregivers, List<MedicineDose> medicines, List<ActivityLog> logs, Map<String, Object> alert) {
        this.patient = patient;
        this.caregivers = caregivers;
        this.medicines = medicines;
        this.logs = logs;
        this.alert = alert;
    }

    @SuppressWarnings("unchecked")
    public static FamilyState fromMap(Map<String, Object> data) {
        Patient patient = Patient.fromMap((Map<String, Object>) data.get("patient"));
        List<Caregiver> caregivers = new ArrayList<>();
        Object caregiverValue = data.get("caregivers");
        if (caregiverValue instanceof List) {
            for (Object item : (List<?>) caregiverValue) if (item instanceof Map) caregivers.add(Caregiver.fromMap((Map<String, Object>) item));
        }
        List<MedicineDose> medicines = new ArrayList<>();
        Object medicineValue = data.get("medicines");
        if (medicineValue instanceof List) {
            for (Object item : (List<?>) medicineValue) if (item instanceof Map) medicines.add(MedicineDose.fromMap((Map<String, Object>) item));
        }
        List<ActivityLog> logs = new ArrayList<>();
        Object logValue = data.get("logs");
        if (logValue instanceof List) {
            for (Object item : (List<?>) logValue) if (item instanceof Map) logs.add(ActivityLog.fromMap((Map<String, Object>) item));
        }
        Map<String, Object> alert = data.get("alert") instanceof Map ? (Map<String, Object>) data.get("alert") : null;
        return new FamilyState(patient, caregivers, medicines, logs, alert);
    }

    public Patient getPatient() { return patient; }
    public List<Caregiver> getCaregivers() { return caregivers; }
    public List<MedicineDose> getMedicines() { return medicines; }
    public List<ActivityLog> getLogs() { return logs; }
    public Map<String, Object> getAlert() { return alert; }
}
