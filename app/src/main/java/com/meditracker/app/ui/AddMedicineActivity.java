package com.meditracker.app.ui;

import android.app.TimePickerDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.firebase.firestore.ListenerRegistration;
import com.meditracker.app.R;
import com.meditracker.app.data.FirebaseRepository;
import com.meditracker.app.data.SessionManager;
import com.meditracker.app.model.ActivityLog;
import com.meditracker.app.model.FamilyState;
import com.meditracker.app.model.MedicineDose;
import com.meditracker.app.model.Patient;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Iterator;
import java.util.UUID;

public class AddMedicineActivity extends BaseActivity {
    private static final String[] COLOR_CODES = {"teal", "coral", "violet", "blue", "amber"};
    private final FirebaseRepository repository = FirebaseRepository.get();
    private SessionManager session; private ListenerRegistration registration; private FamilyState current; private MedicineDose editing;
    private EditText name, banglaName, dosage, banglaDosage, voice;
    private MaterialAutoCompleteTextView meal, color;
    private TextView error, noTimes;
    private ChipGroup selectedTimesContainer;
    private final List<String> selectedTimes = new ArrayList<>();
    private String[] meals, colors;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_add_medicine); session = new SessionManager(this);
        name = findViewById(R.id.nameInput); banglaName = findViewById(R.id.banglaNameInput); dosage = findViewById(R.id.dosageInput);
        banglaDosage = findViewById(R.id.banglaDosageInput); voice = findViewById(R.id.voiceInput);
        meal = findViewById(R.id.mealSpinner); color = findViewById(R.id.colorSpinner); error = findViewById(R.id.errorText);
        selectedTimesContainer = findViewById(R.id.selectedTimesContainer); noTimes = findViewById(R.id.noTimesText);
        meals = new String[]{getString(R.string.meal_before), getString(R.string.meal_after), getString(R.string.meal_with)};
        colors = new String[]{getString(R.string.color_teal), getString(R.string.color_coral), getString(R.string.color_violet), getString(R.string.color_blue), getString(R.string.color_amber)};
        meal.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, meals));
        color.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, colors));
        meal.setText(meals[1], false);
        color.setText(colors[0], false);
        findViewById(R.id.addTimeButton).setOnClickListener(v -> showTimePicker());
        editing = (MedicineDose) getIntent().getSerializableExtra("medicine");
        if (editing != null) fillEditing();
        findViewById(R.id.cancelButton).setOnClickListener(v -> finish());
        findViewById(R.id.saveButton).setOnClickListener(v -> save());
        repository.listenFamily(session.familyCode(), new FirebaseRepository.FamilyListener() {
            @Override public void onData(FamilyState state) { current = state; }
            @Override public void onError(Exception exception) { error.setText(R.string.error_sync_family); }
        }, new FirebaseRepository.Result<ListenerRegistration>() {
            @Override public void onSuccess(ListenerRegistration value) { registration = value; }
            @Override public void onError(Exception exception) { error.setText(R.string.error_phone_not_linked_family); }
        });
    }

    private void fillEditing() {
        ((TextView) findViewById(R.id.titleText)).setText(R.string.edit_medicine); name.setText(editing.getName()); banglaName.setText(editing.getBanglaName());
        dosage.setText(editing.getDosage()); banglaDosage.setText(editing.getBanglaDosage()); voice.setText(editing.getCustomVoiceText());
        if (!editing.getTiming().isEmpty()) selectedTimes.add(editing.getTiming());
        renderSelectedTimes();
        String relation = editing.getMealRelation();
        meal.setText(meals["before_meal".equals(relation) ? 0 : "with_meal".equals(relation) ? 2 : 1], false);
        String theme = editing.getColorTheme();
        for (int i = 0; i < COLOR_CODES.length; i++) if (COLOR_CODES[i].equalsIgnoreCase(theme)) { color.setText(colors[i], false); break; }
    }

    private void save() {
        if (current == null) { error.setText(R.string.error_family_loading); return; }
        String medicineName = value(name); if (medicineName.isEmpty()) { error.setText(R.string.error_medicine_name_required); return; }
        List<String> schedule = new ArrayList<>(selectedTimes); if (schedule.isEmpty()) { error.setText(R.string.error_time_required); return; }
        List<MedicineDose> next = new ArrayList<>(current.getMedicines());
        if (editing != null) {
            Iterator<MedicineDose> iterator = next.iterator();
            while (iterator.hasNext()) if (iterator.next().getId().equals(editing.getId())) iterator.remove();
        }
        String group = editing == null || editing.getGroupId().isEmpty() ? "medicine_" + System.currentTimeMillis() + "_" + shortId() : editing.getGroupId();
        for (int i = 0; i < schedule.size(); i++) {
            MedicineDose dose = new MedicineDose(); dose.setId(editing != null && i == 0 ? editing.getId() : "dose_" + System.currentTimeMillis() + "_" + i + "_" + shortId());
            dose.setGroupId(group); dose.setName(medicineName); dose.setBanglaName(value(banglaName));
            dose.setDosage(fallback(value(dosage), "1 tablet")); dose.setBanglaDosage(fallback(value(banglaDosage), "১টি ট্যাবলেট"));
            dose.setTiming(schedule.get(i)); dose.setPeriod(period(schedule.get(i))); dose.setMealRelation(mealCode()); dose.setMealRelationBangla(mealBangla());
            dose.setColorTheme(COLOR_CODES[selectedIndex(color, colors, 0)]); dose.setCustomVoiceText(value(voice));
            dose.setStatus("PENDING"); next.add(dose);
        }
        ActivityLog log = new ActivityLog("log_" + System.currentTimeMillis(), FirebaseRepository.displayTime(), editing == null ? "Medicine added" : "Medicine updated", medicineName + " was scheduled " + schedule.size() + " time(s) daily.", "info");
        List<ActivityLog> logs = new ArrayList<>(); logs.add(log); logs.addAll(current.getLogs()); if (logs.size() > 50) logs = new ArrayList<>(logs.subList(0, 50));
        findViewById(R.id.saveButton).setEnabled(false);
        repository.saveSnapshot(session.familyCode(), current.getPatient(), next, logs, new FirebaseRepository.Result<Void>() {
            @Override public void onSuccess(Void value) { repository.notifyScheduleChanged(session.familyCode()); Toast.makeText(AddMedicineActivity.this, R.string.medicine_saved, Toast.LENGTH_SHORT).show(); finish(); }
            @Override public void onError(Exception exception) { findViewById(R.id.saveButton).setEnabled(true); error.setText(R.string.error_save_medicine); }
        });
    }

    private void showTimePicker() {
        Calendar now = Calendar.getInstance();
        new TimePickerDialog(this, (picker, hour, minute) -> {
            Calendar selected = Calendar.getInstance(); selected.set(Calendar.HOUR_OF_DAY, hour); selected.set(Calendar.MINUTE, minute);
            String formatted = new SimpleDateFormat("hh:mm a", Locale.US).format(selected.getTime());
            if (!selectedTimes.contains(formatted)) selectedTimes.add(formatted);
            Collections.sort(selectedTimes, (first, second) -> Integer.compare(timeMinutes(first), timeMinutes(second)));
            renderSelectedTimes(); error.setText("");
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show();
    }

    private void renderSelectedTimes() {
        selectedTimesContainer.removeAllViews();
        noTimes.setVisibility(selectedTimes.isEmpty() ? View.VISIBLE : View.GONE);
        for (String time : selectedTimes) {
            Chip chip = new Chip(this);
            chip.setText(time); chip.setTextColor(ContextCompat.getColor(this, R.color.navy));
            chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.teal_soft)));
            chip.setCloseIconVisible(true); chip.setCloseIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.teal_dark)));
            chip.setEnsureMinTouchTargetSize(true);
            chip.setOnCloseIconClickListener(v -> { selectedTimes.remove(time); renderSelectedTimes(); });
            selectedTimesContainer.addView(chip);
        }
    }
    private int timeMinutes(String value) { try { Date date = new SimpleDateFormat("hh:mm a", Locale.US).parse(value); Calendar calendar = Calendar.getInstance(); calendar.setTime(date); return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE); } catch (Exception ignored) { return 9999; } }
    private String period(String time) { try { Date date = new SimpleDateFormat("hh:mm a", Locale.US).parse(time); int hour = Integer.parseInt(new SimpleDateFormat("H", Locale.US).format(date)); return hour < 12 ? "Morning" : hour < 17 ? "Noon" : hour < 21 ? "Evening" : "Night"; } catch (Exception e) { return "Morning"; } }
    private String mealCode() { int index = selectedIndex(meal, meals, 1); return index == 0 ? "before_meal" : index == 2 ? "with_meal" : "after_meal"; }
    private String mealBangla() { int index = selectedIndex(meal, meals, 1); return index == 0 ? "খাবারের আগে" : index == 2 ? "খাবারের সাথে" : "খাবারের পর"; }
    private int selectedIndex(MaterialAutoCompleteTextView input, String[] options, int fallback) {
        String selected = input.getText().toString().trim();
        for (int i = 0; i < options.length; i++) if (options[i].equalsIgnoreCase(selected)) return i;
        return fallback;
    }
    private String value(EditText input) { return input.getText().toString().trim(); }
    private String fallback(String value, String replacement) { return value.isEmpty() ? replacement : value; }
    private String shortId() { return UUID.randomUUID().toString().substring(0, 4); }
    @Override protected void onDestroy() { if (registration != null) registration.remove(); super.onDestroy(); }
}
