package com.meditracker.app.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Patient implements Serializable {
    private String id = "";
    private String name = "";
    private long age = 60;
    private String relation = "Self";
    private String familyCode = "";

    public Patient() {}

    public static Patient fromMap(Map<String, Object> map) {
        Patient patient = new Patient();
        if (map == null) return patient;
        patient.id = value(map.get("id"));
        patient.name = value(map.get("name"));
        patient.relation = value(map.get("relation"));
        patient.familyCode = value(map.get("familyCode"));
        Object ageValue = map.get("age");
        if (ageValue instanceof Number) patient.age = ((Number) ageValue).longValue();
        return patient;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("age", age);
        map.put("relation", relation);
        map.put("familyCode", familyCode);
        map.put("caregivers", java.util.Collections.emptyList());
        return map;
    }

    private static String value(Object value) { return value == null ? "" : String.valueOf(value); }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getAge() { return age; }
    public void setAge(long age) { this.age = age; }
    public String getRelation() { return relation; }
    public void setRelation(String relation) { this.relation = relation; }
    public String getFamilyCode() { return familyCode; }
    public void setFamilyCode(String familyCode) { this.familyCode = familyCode; }
}
