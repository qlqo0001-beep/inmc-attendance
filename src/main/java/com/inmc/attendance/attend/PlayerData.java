package com.inmc.attendance.attend;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 한 사람의 출석 기록 파일 (attendance-data/uuid.yml). 원자적 저장. */
public final class PlayerData {

    private final UUID id;
    public final Map<String, Board.Record> records = new HashMap<>();
    public final List<ItemStack> pending = new ArrayList<>();
    public LocalDate playedDate;
    public int playedMinutes;

    public PlayerData(UUID id) { this.id = id; }

    public UUID id() { return id; }

    public Board.Record record(Board board) {
        Board.Record r = records.get(board.id());
        if (r != null && r.round == board.round()) return r;
        return Board.Record.empty(board.round());
    }

    public int minutes(LocalDate today) {
        return today.equals(playedDate) ? playedMinutes : 0;
    }

    public void addMinute(LocalDate today) {
        if (!today.equals(playedDate)) {
            playedDate = today;
            playedMinutes = 0;
        }
        playedMinutes++;
    }

    public YamlConfiguration toYaml() {
        YamlConfiguration y = new YamlConfiguration();
        if (playedDate != null) {
            y.set("played.date", playedDate.toString());
            y.set("played.minutes", playedMinutes);
        }
        for (Map.Entry<String, Board.Record> e : records.entrySet()) {
            e.getValue().save(y.createSection("boards." + e.getKey()));
        }
        if (!pending.isEmpty()) {
            List<String> out = new ArrayList<>();
            for (ItemStack stack : pending) {
                try { out.add(Base64.getEncoder().encodeToString(stack.serializeAsBytes())); }
                catch (Throwable ignored) {}
            }
            y.set("pending", out);
        }
        return y;
    }

    public void write(File file) {
        writeAtomically(file, toYaml().saveToString());
    }

    static void writeAtomically(File file, String text) {
        try {
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            File temp = new File(file.getParentFile(), file.getName() + ".tmp");
            java.nio.file.Files.writeString(temp.toPath(), text, java.nio.charset.StandardCharsets.UTF_8);
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Throwable t) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 접속 안 한 사람의 파일에서 판 기록만 지운다. 못 받은 보상은 건드리지 않는다. */
    public static boolean forget(File file, String board) {
        if (!file.isFile()) return false;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        if (!y.contains("boards." + board)) return false;
        y.set("boards." + board, null);
        writeAtomically(file, y.saveToString());
        return true;
    }

    public static PlayerData read(UUID id, File file) {
        PlayerData data = new PlayerData(id);
        if (!file.isFile()) return data;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        try {
            String s = y.getString("played.date");
            if (s != null && !s.isBlank()) data.playedDate = LocalDate.parse(s.trim());
        } catch (Throwable ignored) {}
        data.playedMinutes = y.getInt("played.minutes");
        ConfigurationSection boards = y.getConfigurationSection("boards");
        if (boards != null) {
            for (String key : boards.getKeys(false)) {
                data.records.put(key, Board.Record.load(boards.getConfigurationSection(key)));
            }
        }
        for (String raw : y.getStringList("pending")) {
            try {
                data.pending.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(raw)));
            } catch (Throwable ignored) {}
        }
        return data;
    }
}
