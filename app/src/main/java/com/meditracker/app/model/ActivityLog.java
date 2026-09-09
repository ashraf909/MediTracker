package com.meditracker.app.model;

import java.util.HashMap;
import java.util.Map;

public class ActivityLog {
    private String id = "";
    private String time = "";
    private String title = "";
    private String description = "";
    private String type = "info";

    public ActivityLog() {}
    public ActivityLog(String id, String time, String title, String description, String type) {
        this.id = id; this.time = time; this.title = title; this.description = description; this.type = type;
    }

    public static ActivityLog fromMap(Map<String, Object> map) {
        if (map == null) return new ActivityLog();
        return new ActivityLog(value(map.get("id")), value(map.get("time")), value(map.get("title")), value(map.get("description")), value(map.get("type")));
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id); map.put("time", time); map.put("title", title);
        map.put("description", description); map.put("type", type);
        return map;
    }

    private static String value(Object value) { return value == null ? "" : String.valueOf(value); }
    public String getId() { return id; }
    public String getTime() { return time; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getType() { return type; }
}
