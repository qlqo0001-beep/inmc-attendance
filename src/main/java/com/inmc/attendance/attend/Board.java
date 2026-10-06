package com.inmc.attendance.attend;

import com.inmc.attendance.item.StoredItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** 출석판 + 칸 보상 + 한 사람의 판 기록 + 순수 계산. inmc-menu Board.kt의 Java 이식판. */
public final class Board {

    public enum Mode {
        CALENDAR("달력형", "이번 달 1~31일 칸 — 다음 달 새로"),
        STREAK("연속형", "연속 1~N일째 — 하루 빠지면 1일째로"),
        TOTAL("누적형", "총 1~N번째");

        public final String label;
        public final String help;
        Mode(String label, String help) { this.label = label; this.help = help; }

        public static Mode parse(String raw) {
            if (raw == null) return CALENDAR;
            for (Mode m : values()) if (m.name().equalsIgnoreCase(raw.trim())) return m;
            return CALENDAR;
        }
    }

    public enum Claim {
        AUTO("자동", "접속하면(정한 분만큼 접속한 뒤) 저절로"),
        MANUAL("수동", "출석 화면에서 오늘 칸을 눌러야");

        public final String label;
        public final String help;
        Claim(String label, String help) { this.label = label; this.help = help; }

        public static Claim parse(String raw) {
            if (raw == null) return AUTO;
            for (Claim c : values()) if (c.name().equalsIgnoreCase(raw.trim())) return c;
            return AUTO;
        }
    }

    public enum AfterEnd {
        REPEAT("처음부터 반복"), KEEP_LAST("마지막 칸 유지");

        public final String label;
        AfterEnd(String label) { this.label = label; }

        public static AfterEnd parse(String raw) {
            if (raw == null) return REPEAT;
            String t = raw.trim().replace('-', '_');
            for (AfterEnd a : values()) if (a.name().equalsIgnoreCase(t)) return a;
            return REPEAT;
        }
    }

    public enum CellState { DONE, TODAY, TODAY_DONE, MISSED, LATER, NONE }

    public record RewardItem(StoredItem item, int amount) {}

    public static final class Reward {
        public static final int MAX_AMOUNT = 3456;
        private final List<RewardItem> items;
        private final List<String> commands;
        private final long money;

        public Reward(List<RewardItem> items, List<String> commands, long money) {
            this.items = List.copyOf(items);
            this.commands = List.copyOf(commands);
            this.money = Math.max(0, money);
        }

        public List<RewardItem> items() { return items; }
        public List<String> commands() { return commands; }
        public long money() { return money; }

        public boolean isEmpty() { return items.isEmpty() && commands.isEmpty() && money <= 0; }

        public void save(ConfigurationSection section) {
            for (int i = 0; i < items.size(); i++) {
                RewardItem e = items.get(i);
                ConfigurationSection sub = section.createSection("items." + i);
                e.item().save(sub);
                sub.set("amount", e.amount());
            }
            if (!commands.isEmpty()) section.set("commands", commands);
            if (money > 0) section.set("money", money);
        }

        public static Reward load(ConfigurationSection section) {
            if (section == null) return new Reward(List.of(), List.of(), 0);
            List<RewardItem> items = new ArrayList<>();
            ConfigurationSection list = section.getConfigurationSection("items");
            if (list != null) {
                List<String> keys = new ArrayList<>(list.getKeys(false));
                keys.sort((a, b) -> Integer.compare(parseInt(a), parseInt(b)));
                for (String key : keys) {
                    ConfigurationSection sub = list.getConfigurationSection(key);
                    if (sub == null) continue;
                    StoredItem stored = StoredItem.load(sub);
                    if (stored == null) continue;
                    int amount = Math.max(1, Math.min(MAX_AMOUNT, sub.getInt("amount", 1)));
                    items.add(new RewardItem(stored, amount));
                }
            }
            return new Reward(items, section.getStringList("commands"), Math.max(0, section.getLong("money", 0)));
        }

        private static int parseInt(String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return Integer.MAX_VALUE; }
        }
    }

    /** 한 사람의 출석판 하나 기록. */
    public static final class Record {
        public final LocalDate last;
        public final int streak;
        public final int total;
        public final YearMonth month;
        public final java.util.Set<Integer> days;
        public final java.util.Set<Integer> bonuses;
        public final int round;

        public Record(LocalDate last, int streak, int total, YearMonth month,
                      java.util.Set<Integer> days, java.util.Set<Integer> bonuses, int round) {
            this.last = last;
            this.streak = streak;
            this.total = total;
            this.month = month;
            this.days = Collections.unmodifiableSet(new TreeSet<>(days));
            this.bonuses = Collections.unmodifiableSet(new TreeSet<>(bonuses));
            this.round = round;
        }

        public static Record empty(int round) {
            return new Record(null, 0, 0, null, java.util.Set.of(), java.util.Set.of(), round);
        }

        public java.util.Set<Integer> daysIn(YearMonth m) {
            return m != null && m.equals(month) ? days : java.util.Set.of();
        }

        public java.util.Set<Integer> bonusesIn(YearMonth m) {
            return m != null && m.equals(month) ? bonuses : java.util.Set.of();
        }

        public int liveStreak(LocalDate today) {
            if (last != null && (last.equals(today) || last.equals(today.minusDays(1)))) return streak;
            return 0;
        }

        public void save(ConfigurationSection section) {
            if (last != null) section.set("last", last.toString());
            if (streak > 0) section.set("streak", streak);
            if (total > 0) section.set("total", total);
            if (month != null) section.set("month", month.toString());
            if (!days.isEmpty()) section.set("days", new ArrayList<>(new TreeSet<>(days)));
            if (!bonuses.isEmpty()) section.set("bonuses", new ArrayList<>(new TreeSet<>(bonuses)));
            if (round > 0) section.set("round", round);
        }

        public static Record load(ConfigurationSection section) {
            if (section == null) return empty(0);
            LocalDate last = null;
            try {
                String s = section.getString("last");
                if (s != null && !s.isBlank()) last = LocalDate.parse(s.trim());
            } catch (Throwable ignored) {}
            YearMonth month = null;
            try {
                String s = section.getString("month");
                if (s != null && !s.isBlank()) month = YearMonth.parse(s.trim());
            } catch (Throwable ignored) {}
            return new Record(last, section.getInt("streak"), section.getInt("total"), month,
                    new TreeSet<>(section.getIntegerList("days")),
                    new TreeSet<>(section.getIntegerList("bonuses")),
                    section.getInt("round"));
        }
    }

    public record Attended(Record record, int cell, int count, List<Integer> bonuses) {}

    public static final int MAX_LENGTH = 45;
    public static final List<Integer> DEFAULT_BONUSES = List.of(7, 14, 21, 28);
    public static final Pattern ID = Pattern.compile("^[a-z0-9_가-힣-]{1,32}$");

    private final String id;
    private final String name;
    private final Material icon;
    private final boolean enabled;
    private final Mode mode;
    private final Claim claim;
    private final int length;
    private final AfterEnd afterEnd;
    private final int autoMinutes;
    private final Map<Integer, Reward> rewards;
    private final Map<Integer, Reward> bonuses;
    private final YearMonth month;
    private final int round;

    public Board(String id, String name, Material icon, boolean enabled, Mode mode, Claim claim,
                 int length, AfterEnd afterEnd, int autoMinutes,
                 Map<Integer, Reward> rewards, Map<Integer, Reward> bonuses,
                 YearMonth month, int round) {
        this.id = id;
        this.name = name == null || name.isBlank() ? id : name;
        this.icon = icon == null ? Material.CLOCK : icon;
        this.enabled = enabled;
        this.mode = mode == null ? Mode.CALENDAR : mode;
        this.claim = claim == null ? Claim.AUTO : claim;
        this.length = Math.max(1, Math.min(MAX_LENGTH, length));
        this.afterEnd = afterEnd == null ? AfterEnd.REPEAT : afterEnd;
        this.autoMinutes = Math.max(0, Math.min(1440, autoMinutes));
        this.rewards = Collections.unmodifiableMap(new TreeMap<>(rewards == null ? Map.of() : rewards));
        this.bonuses = Collections.unmodifiableMap(new TreeMap<>(bonuses == null ? Map.of() : bonuses));
        this.month = month;
        this.round = Math.max(0, round);
    }

    public String id() { return id; }
    public String name() { return name; }
    public Material icon() { return icon; }
    public boolean enabled() { return enabled; }
    public Mode mode() { return mode; }
    public Claim claim() { return claim; }
    public int length() { return length; }
    public AfterEnd afterEnd() { return afterEnd; }
    public int autoMinutes() { return autoMinutes; }
    public Map<Integer, Reward> rewards() { return rewards; }
    public Map<Integer, Reward> bonuses() { return bonuses; }
    public YearMonth month() { return month; }
    public int round() { return round; }

    public boolean openOn(LocalDate today) {
        if (!enabled) return false;
        if (month == null) return true;
        return month.equals(YearMonth.from(today));
    }

    public Board copyDraft() {
        if (month == null) return copy(id + "_복사", name, null, 0);
        YearMonth next = month.plusMonths(1);
        String newId = shiftMonth(id, month, next);
        if (newId.equals(id)) newId = id + "_복사";
        return copy(newId, shiftMonth(name, month, next), next, 0);
    }

    private Board copy(String newId, String newName, YearMonth newMonth, int newRound) {
        return new Board(newId, newName, icon, enabled, mode, claim, length, afterEnd,
                autoMinutes, rewards, bonuses, newMonth, newRound);
    }

    public int cells() {
        return mode == Mode.CALENDAR ? 31 : Math.max(1, Math.min(MAX_LENGTH, length));
    }

    public int position(int n) {
        if (n <= 0) return 0;
        int c = cells();
        if (n <= c) return n;
        if (afterEnd == AfterEnd.REPEAT) return (n - 1) % c + 1;
        return c;
    }

    public Attended attend(Record record, LocalDate today) {
        if (record.last != null && record.last.equals(today)) return null;
        return switch (mode) {
            case CALENDAR -> {
                YearMonth m = YearMonth.from(today);
                java.util.Set<Integer> days = new TreeSet<>(record.daysIn(m));
                days.add(today.getDayOfMonth());
                java.util.Set<Integer> claimed = new TreeSet<>(record.bonusesIn(m));
                List<Integer> fresh = new ArrayList<>();
                for (Integer k : bonuses.keySet()) {
                    if (k <= days.size() && !claimed.contains(k)) fresh.add(k);
                }
                Collections.sort(fresh);
                java.util.Set<Integer> newBonuses = new TreeSet<>(claimed);
                newBonuses.addAll(fresh);
                Record next = new Record(today, record.streak, record.total + 1, m, days, newBonuses, record.round);
                yield new Attended(next, today.getDayOfMonth(), days.size(), fresh);
            }
            case STREAK -> {
                int streak = (record.last != null && record.last.equals(today.minusDays(1))) ? record.streak + 1 : 1;
                Record next = new Record(today, streak, record.total + 1, record.month, record.days, record.bonuses, record.round);
                yield new Attended(next, position(streak), streak, List.of());
            }
            case TOTAL -> {
                int total = record.total + 1;
                Record next = new Record(today, record.streak, total, record.month, record.days, record.bonuses, record.round);
                yield new Attended(next, position(total), total, List.of());
            }
        };
    }

    public List<CellState> states(Record record, LocalDate today) {
        boolean attendedToday = record.last != null && record.last.equals(today);
        if (mode == Mode.CALENDAR) {
            YearMonth m = YearMonth.from(today);
            java.util.Set<Integer> days = record.daysIn(m);
            int len = m.lengthOfMonth();
            List<CellState> out = new ArrayList<>(31);
            for (int d = 1; d <= 31; d++) {
                if (d > len) out.add(CellState.NONE);
                else if (d == today.getDayOfMonth()) out.add(days.contains(d) ? CellState.TODAY_DONE : CellState.TODAY);
                else if (days.contains(d)) out.add(CellState.DONE);
                else if (d < today.getDayOfMonth()) out.add(CellState.MISSED);
                else out.add(CellState.LATER);
            }
            return out;
        }
        int count = mode == Mode.STREAK ? record.liveStreak(today) : record.total;
        int c = cells();
        List<CellState> out = new ArrayList<>(c);
        if (attendedToday) {
            int pos = position(count);
            for (int i = 1; i <= c; i++) {
                if (i == pos) out.add(CellState.TODAY_DONE);
                else if (i < pos) out.add(CellState.DONE);
                else out.add(CellState.LATER);
            }
            return out;
        }
        int next = position(count + 1);
        int done = (next == 1 && afterEnd == AfterEnd.REPEAT) ? 0 : position(count);
        for (int i = 1; i <= c; i++) {
            if (i == next) out.add(CellState.TODAY);
            else if (i <= done) out.add(CellState.DONE);
            else out.add(CellState.LATER);
        }
        return out;
    }

    public int todayCell(Record record, LocalDate today) {
        return switch (mode) {
            case CALENDAR -> today.getDayOfMonth();
            case STREAK -> position(record.last != null && record.last.equals(today) ? record.streak : record.liveStreak(today) + 1);
            case TOTAL -> position(record.last != null && record.last.equals(today) ? record.total : record.total + 1);
        };
    }

    public YamlConfiguration save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", name);
        y.set("icon", icon.name());
        y.set("enabled", enabled);
        y.set("mode", mode.name().toLowerCase());
        y.set("claim", claim.name().toLowerCase());
        y.set("length", length);
        y.set("after-end", afterEnd.name().toLowerCase().replace('_', '-'));
        y.set("auto-minutes", autoMinutes);
        if (month != null) y.set("month", month.toString());
        if (round > 0) y.set("round", round);
        for (Map.Entry<Integer, Reward> e : new TreeMap<>(rewards).entrySet()) {
            if (!e.getValue().isEmpty()) e.getValue().save(y.createSection("rewards." + e.getKey()));
        }
        for (Map.Entry<Integer, Reward> e : new TreeMap<>(bonuses).entrySet()) {
            e.getValue().save(y.createSection("bonuses." + e.getKey()));
        }
        return y;
    }

    public static Board load(String id, YamlConfiguration y) {
        Mode mode = Mode.parse(y.getString("mode"));
        Claim claim = Claim.parse(y.getString("claim"));
        AfterEnd after = AfterEnd.parse(y.getString("after-end"));
        Material icon = Material.matchMaterial(y.getString("icon", ""));
        if (icon == null) icon = Material.CLOCK;
        YearMonth month = null;
        try {
            String s = y.getString("month");
            if (s != null && !s.isBlank()) month = YearMonth.parse(s.trim());
        } catch (Throwable ignored) {}
        return new Board(
            id,
            y.getString("name", id),
            icon,
            y.getBoolean("enabled", true),
            mode, claim,
            Math.max(1, Math.min(MAX_LENGTH, y.getInt("length", 7))),
            after,
            Math.max(0, Math.min(1440, y.getInt("auto-minutes", 0))),
            cells(y, "rewards", 1, MAX_LENGTH),
            cells(y, "bonuses", 1, 31),
            month,
            Math.max(0, y.getInt("round", 0))
        );
    }

    private static Map<Integer, Reward> cells(YamlConfiguration y, String path, int from, int to) {
        ConfigurationSection s = y.getConfigurationSection(path);
        if (s == null) return Map.of();
        Map<Integer, Reward> out = new LinkedHashMap<>();
        for (String key : s.getKeys(false)) {
            int n;
            try { n = Integer.parseInt(key); } catch (NumberFormatException e) { continue; }
            if (n < from || n > to) continue;
            out.put(n, Reward.load(s.getConfigurationSection(key)));
        }
        return out;
    }

    public static String shiftMonth(String text, YearMonth from, YearMonth to) {
        if (text == null) return "";
        String out = text.replace(from.toString(), to.toString());
        if (from.getYear() != to.getYear()) {
            out = out.replaceAll("(?<!\\d)" + from.getYear() + "년", to.getYear() + "년");
        }
        out = out.replaceAll("(?<!\\d)" + from.getMonthValue() + "월", to.getMonthValue() + "월");
        return out;
    }

    public static Board starter() {
        Map<Integer, Reward> b = new LinkedHashMap<>();
        for (int d : DEFAULT_BONUSES) b.put(d, new Reward(List.of(), List.of(), 0));
        return new Board("daily", "<gold>매일 출석</gold>", Material.CLOCK, true,
                Mode.CALENDAR, Claim.AUTO, 7, AfterEnd.REPEAT, 0, Map.of(), b, null, 0);
    }

    // --- with ---

    public Board withName(String v) { return new Board(id, v, icon, enabled, mode, claim, length, afterEnd, autoMinutes, rewards, bonuses, month, round); }
    public Board withIcon(Material v) { return new Board(id, name, v, enabled, mode, claim, length, afterEnd, autoMinutes, rewards, bonuses, month, round); }
    public Board withEnabled(boolean v) { return new Board(id, name, icon, v, mode, claim, length, afterEnd, autoMinutes, rewards, bonuses, month, round); }
    public Board withMode(Mode v) { return new Board(id, name, icon, enabled, v, claim, length, afterEnd, autoMinutes, rewards, bonuses, month, round); }
    public Board withClaim(Claim v) { return new Board(id, name, icon, enabled, mode, v, length, afterEnd, autoMinutes, rewards, bonuses, month, round); }
    public Board withLength(int v) { return new Board(id, name, icon, enabled, mode, claim, v, afterEnd, autoMinutes, rewards, bonuses, month, round); }
    public Board withAfterEnd(AfterEnd v) { return new Board(id, name, icon, enabled, mode, claim, length, v, autoMinutes, rewards, bonuses, month, round); }
    public Board withAutoMinutes(int v) { return new Board(id, name, icon, enabled, mode, claim, length, afterEnd, v, rewards, bonuses, month, round); }
    public Board withMonth(YearMonth v) { return new Board(id, name, icon, enabled, mode, claim, length, afterEnd, autoMinutes, rewards, bonuses, v, round); }
    public Board withBonuses(Map<Integer, Reward> v) { return new Board(id, name, icon, enabled, mode, claim, length, afterEnd, autoMinutes, rewards, v, month, round); }
    public Board withReward(int cell, Reward v) {
        Map<Integer, Reward> m = new TreeMap<>(rewards);
        m.put(cell, v);
        return new Board(id, name, icon, enabled, mode, claim, length, afterEnd, autoMinutes, m, bonuses, month, round);
    }
    public Board withBonus(int threshold, Reward v) {
        Map<Integer, Reward> m = new TreeMap<>(bonuses);
        m.put(threshold, v);
        return new Board(id, name, icon, enabled, mode, claim, length, afterEnd, autoMinutes, rewards, m, month, round);
    }
    public Board withRound(int v) { return new Board(id, name, icon, enabled, mode, claim, length, afterEnd, autoMinutes, rewards, bonuses, month, v); }
}
