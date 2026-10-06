package com.inmc.attendance.config;

import org.bukkit.configuration.file.YamlConfiguration;

/** config.yml 의 attendance 부분만. */
public record AttendanceConfig(String timezone, int resetHour, boolean remind) {

    public static AttendanceConfig defaults() {
        return new AttendanceConfig("Asia/Seoul", 0, true);
    }

    public static AttendanceConfig from(YamlConfiguration y) {
        String zone = y.getString("attendance.timezone", "Asia/Seoul");
        if (zone == null || zone.isBlank()) zone = "Asia/Seoul";
        int hour = y.getInt("attendance.reset-hour", 0);
        hour = Math.max(0, Math.min(23, hour));
        boolean remind = y.getBoolean("attendance.remind", true);
        return new AttendanceConfig(zone, hour, remind);
    }

    public YamlConfiguration toYaml() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("attendance.timezone", timezone);
        y.set("attendance.reset-hour", resetHour);
        y.set("attendance.remind", remind);
        return y;
    }
}
