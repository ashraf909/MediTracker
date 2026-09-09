package com.meditracker.app.model;

import java.util.HashMap;
import java.util.Map;

public class Caregiver {
    private String id = "";
    private String name = "";
    private String relation = "Caregiver";
    private String phone = "";
    private String location = "Bangladesh";
    private boolean primary;
    private String joinedAt = "";

    public Caregiver() {}

    public static Caregiver fromMap(Map<String, Object> map) {
        Caregiver caregiver = new Caregiver();
        if (map == null) return caregiver;
        caregiver.id = value(map.get("id"));
        caregiver.name = value(map.get("name"));
        caregiver.relation = value(map.get("relation"));
        caregiver.phone = value(map.get("phone"));
        caregiver.location = value(map.get("location"));
        caregiver.primary = Boolean.TRUE.equals(map.get("isPrimary"));
        caregiver.joinedAt = value(map.get("joinedAt"));
        return caregiver;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id); map.put("name", name); map.put("relation", relation);
        map.put("phone", phone); map.put("location", location);
        map.put("isPrimary", primary); map.put("joinedAt", joinedAt);
        return map;
    }

    private static String value(Object value) { return value == null ? "" : String.valueOf(value); }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRelation() { return relation; }
    public void setRelation(String relation) { this.relation = relation; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }
    public String getJoinedAt() { return joinedAt; }
    public void setJoinedAt(String joinedAt) { this.joinedAt = joinedAt; }
}
