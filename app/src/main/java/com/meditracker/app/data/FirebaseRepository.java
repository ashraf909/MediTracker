package com.meditracker.app.data;

import androidx.annotation.NonNull;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.meditracker.app.BuildConfig;
import com.meditracker.app.model.ActivityLog;
import com.meditracker.app.model.Caregiver;
import com.meditracker.app.model.FamilyState;
import com.meditracker.app.model.MedicineDose;
import com.meditracker.app.model.Patient;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class FirebaseRepository {
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final FirebaseRepository INSTANCE = new FirebaseRepository();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore database = FirebaseFirestore.getInstance();
    private final SecureRandom random = new SecureRandom();

    public interface Result<T> { void onSuccess(T value); void onError(Exception error); }
    public interface FamilyListener { void onData(FamilyState state); void onError(Exception error); }

    public static FirebaseRepository get() { return INSTANCE; }
    private FirebaseRepository() {}

    public String currentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? "" : user.getUid();
    }

    private void withUser(Result<FirebaseUser> result) {
        FirebaseUser current = auth.getCurrentUser();
        if (current != null) { result.onSuccess(current); return; }
        auth.signInAnonymously().addOnSuccessListener(value -> result.onSuccess(value.getUser()))
            .addOnFailureListener(result::onError);
    }

    private DocumentReference family(String code) {
        return database.collection("families").document(SessionManager.normalizeCode(code));
    }

    public void createPatient(String name, long age, Result<String> result) {
        withUser(new Result<FirebaseUser>() {
            @Override public void onSuccess(FirebaseUser user) { tryCreatePatient(user, name.trim(), age, 0, result); }
            @Override public void onError(Exception error) { result.onError(error); }
        });
    }

    private void tryCreatePatient(FirebaseUser user, String name, long age, int attempt, Result<String> result) {
        if (attempt >= 6) { result.onError(new IllegalStateException("Could not create a unique family code.")); return; }
        String code = generateCode();
        Patient patient = new Patient();
        patient.setId("patient_" + user.getUid());
        patient.setName(name);
        patient.setAge(age);
        patient.setRelation("Self");
        patient.setFamilyCode(SessionManager.formatCode(code));

        ActivityLog ready = new ActivityLog("log_" + System.currentTimeMillis(), displayTime(), "MediTracker ready", "Your medicine schedule is ready to use.", "info");
        Map<String, Object> data = new HashMap<>();
        data.put("ownerUid", user.getUid());
        data.put("memberUids", Collections.singletonList(user.getUid()));
        data.put("pairingEnabled", true);
        data.put("patient", patient.toMap());
        data.put("caregivers", new ArrayList<>());
        data.put("medicines", new ArrayList<>());
        data.put("logs", Collections.singletonList(ready.toMap()));
        data.put("alert", null);
        data.put("patientPushTokens", new ArrayList<>());
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());
        family(code).set(data).addOnSuccessListener(unused -> result.onSuccess(SessionManager.formatCode(code)))
            .addOnFailureListener(error -> {
                if (error instanceof FirebaseFirestoreException
                    && ((FirebaseFirestoreException) error).getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    tryCreatePatient(user, name, age, attempt + 1, result);
                }
                else result.onError(error);
            });
    }

    public void reconnectPatient(String code, Result<FamilyState> result) {
        join(code, null, true, result);
    }

    public void joinCaregiver(String code, String name, String relation, String phone, Result<FamilyState> result) {
        Caregiver caregiver = new Caregiver();
        caregiver.setName(name.trim());
        caregiver.setRelation(relation);
        caregiver.setPhone(phone == null ? "" : phone.trim());
        caregiver.setLocation("Bangladesh");
        caregiver.setJoinedAt(new Date().toString());
        join(code, caregiver, false, result);
    }

    @SuppressWarnings("unchecked")
    private void join(String code, Caregiver caregiver, boolean returningPatient, Result<FamilyState> result) {
        String normalized = SessionManager.normalizeCode(code);
        if (normalized.length() != 8) { result.onError(new IllegalArgumentException("INVALID_CODE")); return; }
        withUser(new Result<FirebaseUser>() {
            @Override public void onSuccess(FirebaseUser user) {
                DocumentReference reference = family(normalized);
                reference.get().addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) { result.onError(new IllegalArgumentException("INVALID_CODE")); return; }
                    Map<String, Object> data = snapshot.getData();
                    List<String> members = data != null && data.get("memberUids") instanceof List ? (List<String>) data.get("memberUids") : new ArrayList<>();
                    boolean pairing = data != null && Boolean.TRUE.equals(data.get("pairingEnabled"));
                    if (!pairing && !members.contains(user.getUid())) { result.onError(new IllegalStateException("PAIRING_DISABLED")); return; }
                    if (members.contains(user.getUid())) {
                        if (!returningPatient && caregiver != null) {
                            boolean profileExists = false;
                            for (Map<String, Object> saved : listOfMaps(data == null ? null : data.get("caregivers"))) {
                                if (user.getUid().equals(String.valueOf(saved.get("id")))) { profileExists = true; break; }
                            }
                            if (!profileExists) {
                                caregiver.setId(user.getUid());
                                reference.update("caregivers", FieldValue.arrayUnion(caregiver.toMap()), "updatedAt", FieldValue.serverTimestamp())
                                    .continueWithTask(task -> reference.get())
                                    .addOnSuccessListener(updated -> result.onSuccess(FamilyState.fromMap(updated.getData())))
                                    .addOnFailureListener(result::onError);
                                return;
                            }
                        }
                        result.onSuccess(FamilyState.fromMap(data)); return;
                    }

                    Map<String, Object> changes = new HashMap<>();
                    changes.put("memberUids", FieldValue.arrayUnion(user.getUid()));
                    changes.put("updatedAt", FieldValue.serverTimestamp());
                    if (!returningPatient && caregiver != null) {
                        caregiver.setId(user.getUid());
                        changes.put("caregivers", FieldValue.arrayUnion(caregiver.toMap()));
                    }
                    reference.update(changes).continueWithTask(task -> reference.get())
                        .addOnSuccessListener(updated -> result.onSuccess(FamilyState.fromMap(updated.getData())))
                        .addOnFailureListener(result::onError);
                }).addOnFailureListener(result::onError);
            }
            @Override public void onError(Exception error) { result.onError(error); }
        });
    }

    @SuppressWarnings("unchecked")
    public void listenFamily(String code, FamilyListener listener, Result<ListenerRegistration> ready) {
        withUser(new Result<FirebaseUser>() {
            @Override public void onSuccess(FirebaseUser user) {
                DocumentReference reference = family(code);
                reference.get().addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) { ready.onError(new IllegalStateException("DEVICE_NOT_LINKED")); return; }
                    Object memberValue = snapshot.get("memberUids");
                    if (!(memberValue instanceof List) || !((List<Object>) memberValue).contains(user.getUid())) {
                        ready.onError(new IllegalStateException("DEVICE_NOT_LINKED")); return;
                    }
                    ListenerRegistration registration = reference.addSnapshotListener((value, error) -> {
                        if (error != null) { listener.onError(error); return; }
                        if (value != null && value.exists() && value.getData() != null) listener.onData(FamilyState.fromMap(value.getData()));
                    });
                    ready.onSuccess(registration);
                }).addOnFailureListener(ready::onError);
            }
            @Override public void onError(Exception error) { ready.onError(error); }
        });
    }

    @SuppressWarnings("unchecked")
    public void fetchFamily(String code, Result<FamilyState> result) {
        withUser(new Result<FirebaseUser>() {
            @Override public void onSuccess(FirebaseUser user) {
                family(code).get().addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists() || snapshot.getData() == null) {
                        result.onError(new IllegalStateException("DEVICE_NOT_LINKED")); return;
                    }
                    Object memberValue = snapshot.get("memberUids");
                    if (!(memberValue instanceof List) || !((List<Object>) memberValue).contains(user.getUid())) {
                        result.onError(new IllegalStateException("DEVICE_NOT_LINKED")); return;
                    }
                    result.onSuccess(FamilyState.fromMap(snapshot.getData()));
                }).addOnFailureListener(result::onError);
            }
            @Override public void onError(Exception error) { result.onError(error); }
        });
    }

    public void saveSnapshot(String code, Patient patient, List<MedicineDose> medicines, List<ActivityLog> logs, Result<Void> result) {
        Map<String, Object> changes = new HashMap<>();
        changes.put("patient", patient.toMap());
        changes.put("medicines", medicineMaps(medicines));
        changes.put("logs", logMaps(logs));
        changes.put("updatedAt", FieldValue.serverTimestamp());
        family(code).update(changes).addOnSuccessListener(unused -> result.onSuccess(null)).addOnFailureListener(result::onError);
    }

    public void updatePatientProfile(String code, Patient patient, Result<Void> result) {
        Map<String, Object> changes = new HashMap<>();
        changes.put("patient", patient.toMap());
        changes.put("updatedAt", FieldValue.serverTimestamp());
        family(code).update(changes)
            .addOnSuccessListener(unused -> result.onSuccess(null))
            .addOnFailureListener(result::onError);
    }

    public void updateCaregiverProfile(String code, Caregiver profile, Result<Void> result) {
        withUser(new Result<FirebaseUser>() {
            @Override public void onSuccess(FirebaseUser user) {
                DocumentReference reference = family(code);
                database.runTransaction(transaction -> {
                    DocumentSnapshot snapshot = transaction.get(reference);
                    Map<String, Object> data = snapshot.getData();
                    if (data == null) throw new IllegalStateException("INVALID_CODE");
                    List<Map<String, Object>> caregivers = listOfMaps(data.get("caregivers"));
                    boolean found = false;
                    for (Map<String, Object> caregiver : caregivers) {
                        if (user.getUid().equals(String.valueOf(caregiver.get("id")))) {
                            caregiver.put("name", profile.getName());
                            caregiver.put("relation", profile.getRelation());
                            caregiver.put("phone", profile.getPhone());
                            found = true;
                            break;
                        }
                    }
                    if (!found) throw new IllegalStateException("CAREGIVER_NOT_FOUND");
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("caregivers", caregivers);
                    updates.put("updatedAt", FieldValue.serverTimestamp());
                    transaction.update(reference, updates);
                    return null;
                }).addOnSuccessListener(unused -> result.onSuccess(null)).addOnFailureListener(result::onError);
            }
            @Override public void onError(Exception error) { result.onError(error); }
        });
    }

    public void confirmTaken(String code, String medicineId, String patientName, Result<Void> result) {
        DocumentReference reference = family(code);
        String takenAt = displayTime();
        String takenDate = dayKey();
        ActivityLog entry = new ActivityLog("log_" + System.currentTimeMillis(), takenAt, "Dose taken", patientName + " took medicine at " + takenAt + ".", "success");
        database.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(reference);
            Map<String, Object> data = snapshot.getData();
            if (data == null) throw new IllegalStateException("INVALID_CODE");
            List<Map<String, Object>> current = listOfMaps(data.get("medicines"));
            boolean found = false;
            for (Map<String, Object> dose : current) {
                if (medicineId.equals(String.valueOf(dose.get("id")))) {
                    dose.put("status", "TAKEN"); dose.put("takenAt", takenAt); dose.put("takenDate", takenDate); found = true;
                }
            }
            if (!found) throw new IllegalStateException("MEDICINE_NOT_FOUND");
            List<Map<String, Object>> logs = listOfMaps(data.get("logs"));
            logs.add(0, entry.toMap());
            if (logs.size() > 50) logs = new ArrayList<>(logs.subList(0, 50));
            Map<String, Object> updates = new HashMap<>();
            updates.put("medicines", current); updates.put("logs", logs); updates.put("alert", null); updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(reference, updates);
            return null;
        }).addOnSuccessListener(unused -> result.onSuccess(null)).addOnFailureListener(result::onError);
    }

    public void clearAlert(String code) {
        if (SessionManager.normalizeCode(code).length() != 8) return;
        family(code).update("alert", null, "updatedAt", FieldValue.serverTimestamp());
    }

    public void savePatientPushToken(String code, String token) {
        if (token == null || token.isEmpty() || SessionManager.normalizeCode(code).length() != 8) return;
        family(code).update("patientPushTokens", FieldValue.arrayUnion(token), "updatedAt", FieldValue.serverTimestamp());
    }

    public void removePatientPushToken(String code, String token) {
        if (token == null || token.isEmpty() || SessionManager.normalizeCode(code).length() != 8) return;
        family(code).update("patientPushTokens", FieldValue.arrayRemove(token), "updatedAt", FieldValue.serverTimestamp());
    }

    public void sendReminder(String code, MedicineDose medicine, Result<Void> result) {
        withUser(new Result<FirebaseUser>() {
            @Override public void onSuccess(FirebaseUser user) {
                String alertId = "alert_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 5);
                Map<String, Object> alert = new HashMap<>();
                alert.put("id", alertId); alert.put("medicineId", medicine.getId()); alert.put("status", "ACTIVE"); alert.put("sentAt", Timestamp.now());
                family(code).update("alert", alert, "updatedAt", FieldValue.serverTimestamp()).addOnSuccessListener(unused ->
                    user.getIdToken(false).addOnSuccessListener(token -> new Thread(() -> postReminder(code, medicine.getId(), alertId, token.getToken(), result)).start())
                        .addOnFailureListener(result::onError)
                ).addOnFailureListener(result::onError);
            }
            @Override public void onError(Exception error) { result.onError(error); }
        });
    }

    public void notifyScheduleChanged(String code) {
        withUser(new Result<FirebaseUser>() {
            @Override public void onSuccess(FirebaseUser user) {
                user.getIdToken(false).addOnSuccessListener(token ->
                    new Thread(() -> postScheduleSync(code, token.getToken())).start());
            }
            @Override public void onError(Exception ignored) {}
        });
    }

    private void postScheduleSync(String code, String idToken) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(BuildConfig.PUSH_API_ORIGIN + "/api/schedules/sync");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST"); connection.setConnectTimeout(15000); connection.setReadTimeout(15000); connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + idToken);
            connection.setRequestProperty("Content-Type", "application/json");
            JSONObject body = new JSONObject(); body.put("familyCode", SessionManager.normalizeCode(code));
            try (OutputStream output = connection.getOutputStream()) { output.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            connection.getResponseCode();
        } catch (Exception ignored) {}
        finally { if (connection != null) connection.disconnect(); }
    }

    private void postReminder(String code, String medicineId, String alertId, String idToken, Result<Void> result) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(BuildConfig.PUSH_API_ORIGIN + "/api/reminders/send");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST"); connection.setConnectTimeout(15000); connection.setReadTimeout(15000); connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + idToken);
            connection.setRequestProperty("Content-Type", "application/json");
            JSONObject body = new JSONObject();
            body.put("familyCode", SessionManager.normalizeCode(code)); body.put("medicineId", medicineId); body.put("alertId", alertId);
            try (OutputStream output = connection.getOutputStream()) { output.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IllegalStateException("Push server returned " + status + ": " + readText(connection.getErrorStream()));
            result.onSuccess(null);
        } catch (Exception error) { result.onError(error); }
        finally { if (connection != null) connection.disconnect(); }
    }

    private static String readText(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line; while ((line = reader.readLine()) != null) text.append(line);
        }
        return text.toString();
    }

    public static List<Map<String, Object>> medicineMaps(List<MedicineDose> values) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (MedicineDose value : values) result.add(value.toMap());
        return result;
    }
    public static List<Map<String, Object>> logMaps(List<ActivityLog> values) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ActivityLog value : values) result.add(value.toMap());
        return result;
    }
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List) for (Object item : (List<?>) value) if (item instanceof Map) result.add(new HashMap<>((Map<String, Object>) item));
        return result;
    }
    private String generateCode() {
        StringBuilder code = new StringBuilder(); for (int i = 0; i < 8; i++) code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length()))); return code.toString();
    }
    public static String displayTime() { return new SimpleDateFormat("hh:mm a", Locale.US).format(new Date()); }
    public static String dayKey() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }
}
