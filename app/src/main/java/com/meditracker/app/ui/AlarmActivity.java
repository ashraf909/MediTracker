package com.meditracker.app.ui;

import android.os.Bundle;
import android.graphics.drawable.GradientDrawable;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import com.google.firebase.firestore.ListenerRegistration;
import com.meditracker.app.R;
import com.meditracker.app.alarms.MedicineAlarmScheduler;
import com.meditracker.app.data.FirebaseRepository;
import com.meditracker.app.data.SessionManager;
import com.meditracker.app.model.FamilyState;
import com.meditracker.app.model.MedicineDose;

public class AlarmActivity extends BaseActivity {
    private final FirebaseRepository repository = FirebaseRepository.get();
    private SessionManager session; private ListenerRegistration registration; private MedicineDose medicine; private AlarmAudioManager audio; private String medicineId;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        setContentView(R.layout.activity_alarm); session = new SessionManager(this); audio = new AlarmAudioManager(this); audio.startAlarm();
        medicineId = getIntent().getStringExtra(MedicineAlarmScheduler.EXTRA_MEDICINE_ID);
        String title = getIntent().getStringExtra(MedicineAlarmScheduler.EXTRA_TITLE); String body = getIntent().getStringExtra(MedicineAlarmScheduler.EXTRA_BODY);
        ((TextView) findViewById(R.id.medicineNameText)).setText(title == null ? getString(R.string.medicine_reminder) : title);
        ((TextView) findViewById(R.id.dosageText)).setText(body == null ? getString(R.string.your_medicine_due) : body);
        findViewById(R.id.takenButton).setOnClickListener(v -> taken()); findViewById(R.id.snoozeButton).setOnClickListener(v -> snooze());
        findViewById(R.id.speakButton).setOnClickListener(v -> speak());
        if (session.isLinked()) repository.listenFamily(session.familyCode(), new FirebaseRepository.FamilyListener() {
            @Override public void onData(FamilyState state) { for (MedicineDose item : state.getMedicines()) if (item.getId().equals(medicineId)) { medicine = item; render(); break; } }
            @Override public void onError(Exception error) {}
        }, new FirebaseRepository.Result<ListenerRegistration>() {
            @Override public void onSuccess(ListenerRegistration value) { registration = value; }
            @Override public void onError(Exception error) {}
        });
    }

    private void render() {
        ((TextView) findViewById(R.id.timeText)).setText(medicine.getTiming());
        TextView pill = findViewById(R.id.pillText);
        pill.setText(medicine.displayName(this).toUpperCase(java.util.Locale.getDefault()));
        GradientDrawable pillBackground = new GradientDrawable();
        pillBackground.setShape(GradientDrawable.RECTANGLE);
        pillBackground.setColor(ContextCompat.getColor(this, pillColor(medicine.getColorTheme())));
        pillBackground.setCornerRadius(42f * getResources().getDisplayMetrics().density);
        pill.setBackground(pillBackground);
        ((TextView) findViewById(R.id.medicineNameText)).setText(medicine.displayName(this));
        ((TextView) findViewById(R.id.dosageText)).setText(medicine.displayDosage(this));
        ((TextView) findViewById(R.id.mealText)).setText(medicine.mealLabel(this)); speak();
    }
    private int pillColor(String theme) {
        if ("coral".equalsIgnoreCase(theme)) return R.color.pill_coral;
        if ("violet".equalsIgnoreCase(theme)) return R.color.violet;
        if ("blue".equalsIgnoreCase(theme)) return R.color.pill_blue;
        if ("amber".equalsIgnoreCase(theme)) return R.color.pill_amber;
        return R.color.teal_dark;
    }
    private void speak() {
        if (medicine == null) return;
        String text = medicine.getCustomVoiceText().isEmpty()
            ? getString(LocaleManager.isBangla(this) ? R.string.speech_bangla : R.string.speech_english, medicine.displayName(this), medicine.displayDosage(this))
            : medicine.getCustomVoiceText();
        audio.speak(text);
    }
    private void taken() {
        if (medicine == null) { Toast.makeText(this, R.string.medicine_data_loading, Toast.LENGTH_SHORT).show(); return; }
        audio.stopAlarm(); repository.confirmTaken(session.familyCode(), medicine.getId(), getString(R.string.patient_fallback), new FirebaseRepository.Result<Void>() {
            @Override public void onSuccess(Void value) { MedicineAlarmScheduler.cancelMedicine(AlarmActivity.this, medicine.getId()); finish(); }
            @Override public void onError(Exception error) { audio.startAlarm(); Toast.makeText(AlarmActivity.this, R.string.error_update_firebase, Toast.LENGTH_LONG).show(); }
        });
    }
    private void snooze() {
        if (medicine == null) { Toast.makeText(this, R.string.medicine_data_loading, Toast.LENGTH_SHORT).show(); return; }
        audio.stopAlarm(); repository.clearAlert(session.familyCode()); MedicineAlarmScheduler.snooze(this, medicine, 10); finish();
    }
    @Override protected void onDestroy() { if (registration != null) registration.remove(); if (audio != null) audio.close(); super.onDestroy(); }
}
