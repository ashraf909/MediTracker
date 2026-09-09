package com.meditracker.app.model;

import android.content.Context;
import com.meditracker.app.R;
import com.meditracker.app.ui.LocaleManager;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class MedicineDose implements Serializable {
    private String id = "";
    private String groupId = "";
    private String name = "";
    private String banglaName = "";
    private String dosage = "";
    private String banglaDosage = "";
    private String timing = "";
    private String period = "";
    private String mealRelation = "after_meal";
    private String mealRelationBangla = "খাবারের পর";
    private String colorTheme = "teal";
    private String customVoiceText = "";
    private String status = "PENDING";
    private String takenAt;
    private String takenDate;

    public MedicineDose() {}

    public static MedicineDose fromMap(Map<String, Object> map) {
        MedicineDose dose = new MedicineDose();
        if (map == null) return dose;
        dose.id = text(map.get("id"));
        dose.groupId = text(map.get("groupId"));
        dose.name = text(map.get("name"));
        dose.banglaName = text(map.get("banglaName"));
        dose.dosage = text(map.get("dosage"));
        dose.banglaDosage = text(map.get("banglaDosage"));
        dose.timing = text(map.get("timing"));
        dose.period = text(map.get("period"));
        dose.mealRelation = fallback(text(map.get("mealRelation")), "after_meal");
        dose.mealRelationBangla = fallback(text(map.get("mealRelationBangla")), "খাবারের পর");
        dose.colorTheme = fallback(text(map.get("colorTheme")), "teal");
        dose.customVoiceText = text(map.get("customVoiceText"));
        dose.status = fallback(text(map.get("status")), "PENDING");
        dose.takenAt = nullableText(map.get("takenAt"));
        dose.takenDate = nullableText(map.get("takenDate"));
        return dose;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("groupId", groupId);
        map.put("name", name);
        map.put("banglaName", banglaName);
        map.put("dosage", dosage);
        map.put("banglaDosage", banglaDosage);
        map.put("timing", timing);
        map.put("period", period);
        map.put("mealRelation", mealRelation);
        map.put("mealRelationBangla", mealRelationBangla);
        map.put("colorTheme", colorTheme);
        map.put("customVoiceText", customVoiceText);
        map.put("status", status);
        map.put("takenAt", takenAt);
        map.put("takenDate", takenDate);
        return map;
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String nullableText(Object value) { return value == null ? null : String.valueOf(value); }
    private static String fallback(String value, String fallback) { return value.isEmpty() ? fallback : value; }

    public boolean isTaken() { return "TAKEN".equals(status); }
    public String displayName() { return banglaName.isEmpty() ? name : banglaName; }
    public String displayDosage() { return banglaDosage.isEmpty() ? dosage : banglaDosage; }
    public String displayName(Context context) { return LocaleManager.isBangla(context) && !banglaName.isEmpty() ? banglaName : name; }
    public String displayDosage(Context context) { return LocaleManager.isBangla(context) && !banglaDosage.isEmpty() ? banglaDosage : dosage; }
    public String mealLabel(Context context) {
        if ("before_meal".equals(mealRelation)) return context.getString(R.string.meal_before);
        if ("with_meal".equals(mealRelation)) return context.getString(R.string.meal_with);
        return context.getString(R.string.meal_after);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBanglaName() { return banglaName; }
    public void setBanglaName(String banglaName) { this.banglaName = banglaName; }
    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }
    public String getBanglaDosage() { return banglaDosage; }
    public void setBanglaDosage(String banglaDosage) { this.banglaDosage = banglaDosage; }
    public String getTiming() { return timing; }
    public void setTiming(String timing) { this.timing = timing; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public String getMealRelation() { return mealRelation; }
    public void setMealRelation(String mealRelation) { this.mealRelation = mealRelation; }
    public String getMealRelationBangla() { return mealRelationBangla; }
    public void setMealRelationBangla(String mealRelationBangla) { this.mealRelationBangla = mealRelationBangla; }
    public String getColorTheme() { return colorTheme; }
    public void setColorTheme(String colorTheme) { this.colorTheme = colorTheme; }
    public String getCustomVoiceText() { return customVoiceText; }
    public void setCustomVoiceText(String customVoiceText) { this.customVoiceText = customVoiceText; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTakenAt() { return takenAt; }
    public void setTakenAt(String takenAt) { this.takenAt = takenAt; }
    public String getTakenDate() { return takenDate; }
    public void setTakenDate(String takenDate) { this.takenDate = takenDate; }
}
