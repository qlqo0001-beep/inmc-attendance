package com.inmc.attendance.attend;

import com.inmc.attendance.AttendancePlugin;
import com.inmc.attendance.config.AttendanceConfig;
import com.inmc.attendance.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 출석판(attendance/id.yml) + 개인 기록(attendance-data/uuid.yml).
 * 기록을 먼저 쓰고 보상을 준다. 순서를 바꾸면 종료 시점에 두 번 받는다.
 */
public final class AttendanceService {

    public enum Outcome { DONE, ALREADY, LOADING, WAIT, LOCKED, CLOSED }

    private static final int MAX_STACKS = 64;

    private final AttendancePlugin plugin;
    private volatile List<Board> boards = List.of();
    private final Map<UUID, PlayerData> data = new HashMap<>();
    private LocalDate lastDay;

    public AttendanceService(AttendancePlugin plugin) { this.plugin = plugin; }

    private File folder() { return new File(plugin.getDataFolder(), "attendance"); }
    private File dataFolder() { return new File(plugin.getDataFolder(), "attendance-data"); }
    private File file(UUID id) { return new File(dataFolder(), id + ".yml"); }

    public void loadBoards() {
        File dir = folder();
        if (!dir.isDirectory()) {
            dir.mkdirs();
            Board starter = Board.starter();
            try {
                starter.save().save(new File(dir, starter.id() + ".yml"));
            } catch (Exception e) {
                plugin.getLogger().warning("기본 출석판을 쓰지 못했습니다: " + e.getMessage());
            }
        }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        List<Board> out = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (!f.isFile()) continue;
                String id = f.getName().substring(0, f.getName().length() - 4);
                if (!Board.ID.matcher(id).matches()) {
                    plugin.getLogger().warning("출석판 이름이 맞지 않아 건너뜁니다: " + f.getName());
                    continue;
                }
                try {
                    out.add(Board.load(id, YamlConfiguration.loadConfiguration(f)));
                } catch (Exception e) {
                    plugin.getLogger().warning("출석판을 읽지 못했습니다(" + f.getName() + "): " + e.getMessage());
                }
            }
        }
        out.sort((a, b) -> a.id().compareTo(b.id()));
        boards = List.copyOf(out);
    }

    public List<Board> boards() { return boards; }

    public Board board(String id) {
        for (Board b : boards) if (b.id().equals(id)) return b;
        return null;
    }

    public List<Board> enabled() {
        LocalDate today = today();
        List<Board> out = new ArrayList<>();
        for (Board b : boards) if (b.openOn(today)) out.add(b);
        return out;
    }

    public void put(Board board) {
        List<Board> next = new ArrayList<>();
        for (Board b : boards) if (!b.id().equals(board.id())) next.add(b);
        next.add(board);
        next.sort((a, b) -> a.id().compareTo(b.id()));
        boards = List.copyOf(next);
        YamlConfiguration yaml = board.save();
        File target = new File(folder(), board.id() + ".yml");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try { yaml.save(target); }
            catch (Exception e) { plugin.getLogger().severe("출석판을 쓰지 못했습니다(" + board.id() + "): " + e.getMessage()); }
        });
    }

    public Board resetBoard(Board board) {
        Board next = board.withRound(board.round() + 1);
        put(next);
        return next;
    }

    public void resetPlayer(String boardId, UUID target, Consumer<Boolean> then) {
        PlayerData loaded = data.get(target);
        if (loaded != null) {
            then.accept(forgetLoaded(loaded, boardId));
            return;
        }
        File f = file(target);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean removed = PlayerData.forget(f, boardId);
            Bukkit.getScheduler().runTask(plugin, () -> {
                PlayerData joined = data.get(target);
                boolean mem = joined != null && forgetLoaded(joined, boardId);
                then.accept(removed || mem);
            });
        });
    }

    private boolean forgetLoaded(PlayerData pd, String boardId) {
        if (pd.records.remove(boardId) == null) return false;
        write(pd);
        return true;
    }

    public void delete(String id) {
        List<Board> next = new ArrayList<>();
        for (Board b : boards) if (!b.id().equals(id)) next.add(b);
        boards = List.copyOf(next);
        File f = new File(folder(), id + ".yml");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, f::delete);
    }

    // --- 날 ---

    public LocalDate today() {
        AttendanceConfig c = plugin.config();
        return Days.today(Instant.now(), zone(c.timezone()), c.resetHour());
    }

    private ZoneId zone(String raw) {
        try { return ZoneId.of(raw); }
        catch (Throwable t) { return ZoneId.systemDefault(); }
    }

    // --- 사람 ---

    public PlayerData data(UUID id) { return data.get(id); }

    public void onJoin(Player player) {
        UUID id = player.getUniqueId();
        File f = file(id);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerData loaded = PlayerData.read(id, f);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (Bukkit.getPlayer(id) == null) return;
                data.put(id, loaded);
                autoCheck(player);
                remind(player);
            });
        });
    }

    public void onQuit(Player player) {
        PlayerData pd = data.remove(player.getUniqueId());
        if (pd != null) write(pd);
    }

    public void loadOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!data.containsKey(p.getUniqueId())) onJoin(p);
        }
    }

    /** 1분마다: 접속 분 누적 + 자동 출석 + 자정 넘기면 수동 안내. */
    public void tick() {
        LocalDate today = today();
        boolean rolled = lastDay != null && !lastDay.equals(today);
        lastDay = today;
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData pd = data.get(player.getUniqueId());
            if (pd == null) continue;
            pd.addMinute(today);
            autoCheck(player);
            if (rolled) remind(player);
        }
    }

    private void autoCheck(Player player) {
        if (!plugin.hasUse(player)) return;
        PlayerData pd = data.get(player.getUniqueId());
        if (pd == null) return;
        LocalDate today = today();
        for (Board board : enabled()) {
            if (board.claim() != Board.Claim.AUTO) continue;
            if (today.equals(pd.record(board).last)) continue;
            if (pd.minutes(today) >= board.autoMinutes()) attend(player, board);
        }
    }

    private void remind(Player player) {
        if (!plugin.config().remind() || !plugin.hasUse(player)) return;
        PlayerData pd = data.get(player.getUniqueId());
        if (pd == null) return;
        LocalDate today = today();
        for (Board board : enabled()) {
            if (board.claim() == Board.Claim.MANUAL && !today.equals(pd.record(board).last)) {
                plugin.messages().send(player, "attend-remind", TextUtil.tokens().value(board.name()));
            }
        }
    }

    public Outcome attend(Player player, Board board) {
        if (!plugin.hasUse(player)) {
            plugin.messages().send(player, "no-permission");
            return Outcome.LOCKED;
        }
        PlayerData pd = data.get(player.getUniqueId());
        if (pd == null) {
            plugin.messages().send(player, "attend-loading");
            return Outcome.LOADING;
        }
        LocalDate today = today();
        if (!board.openOn(today)) {
            plugin.messages().send(player, "attend-closed", TextUtil.tokens().value(board.name()));
            return Outcome.CLOSED;
        }
        if (board.claim() == Board.Claim.AUTO && pd.minutes(today) < board.autoMinutes()) {
            plugin.messages().send(player, "attend-wait",
                    TextUtil.tokens().count(board.autoMinutes() - pd.minutes(today)));
            return Outcome.WAIT;
        }
        Board.Attended result = board.attend(pd.record(board), today);
        if (result == null) {
            plugin.messages().send(player, "attend-already");
            return Outcome.ALREADY;
        }
        pd.records.put(board.id(), result.record());
        write(pd);
        int before = pd.pending.size();
        give(player, pd, board.rewards().get(result.cell()));
        for (int count : result.bonuses()) {
            Board.Reward bonus = board.bonuses().get(count);
            if (bonus == null || bonus.isEmpty()) continue;
            give(player, pd, bonus);
            plugin.messages().send(player, "attend-bonus",
                    TextUtil.tokens().value(board.name()).count(count));
        }
        String key = switch (board.mode()) {
            case CALENDAR -> "attend-done-calendar";
            case STREAK -> "attend-done-streak";
            case TOTAL -> "attend-done-total";
        };
        plugin.messages().send(player, key,
                TextUtil.tokens().value(board.name()).count(result.count()));
        if (pd.pending.size() > before) {
            write(pd);
            plugin.messages().send(player, "attend-pending",
                    TextUtil.tokens().count(pd.pending.size() - before));
        }
        return Outcome.DONE;
    }

    public void claimPending(Player player) {
        PlayerData pd = data.get(player.getUniqueId());
        if (pd == null) {
            plugin.messages().send(player, "attend-loading");
            return;
        }
        if (pd.pending.isEmpty()) return;
        List<ItemStack> waiting = new ArrayList<>(pd.pending);
        pd.pending.clear();
        write(pd);
        int given = 0;
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack stack : waiting) {
            Map<Integer, ItemStack> rest = player.getInventory().addItem(stack);
            if (rest.isEmpty()) given++;
            else left.addAll(rest.values());
        }
        pd.pending.addAll(left);
        write(pd);
        plugin.messages().send(player, "attend-claimed", TextUtil.tokens().count(given));
        if (!pd.pending.isEmpty()) {
            plugin.messages().send(player, "attend-claim-left", TextUtil.tokens().count(pd.pending.size()));
        }
    }

    private void give(Player player, PlayerData pd, Board.Reward reward) {
        if (reward == null || reward.isEmpty()) return;
        for (Board.RewardItem entry : reward.items()) {
            int left = entry.amount();
            int built = 0;
            while (left > 0 && built < MAX_STACKS) {
                ItemStack stack = entry.item().create(left, plugin.mmoItems());
                if (stack == null || stack.getAmount() <= 0) break;
                left -= stack.getAmount();
                built++;
                pd.pending.addAll(player.getInventory().addItem(stack).values());
            }
        }
        if (reward.money() > 0) plugin.economy().deposit(player, reward.money());
        for (String template : reward.commands()) {
            String command = TextUtil.substituteOnly(template,
                    TextUtil.tokens().player(player.getName()), player).trim();
            if (command.startsWith("/")) command = command.substring(1);
            if (command.isEmpty()) continue;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    private void write(PlayerData pd) {
        try { pd.write(file(pd.id())); }
        catch (Throwable t) { plugin.getLogger().severe("출석 기록을 쓰지 못했습니다(" + pd.id() + "): " + t.getMessage()); }
    }

    public void shutdown() {
        for (PlayerData pd : data.values()) {
            try { pd.write(file(pd.id())); } catch (Throwable ignored) {}
        }
        data.clear();
    }

    public List<ItemStack> pending(UUID id) {
        PlayerData pd = data.get(id);
        return pd == null ? List.of() : Collections.unmodifiableList(pd.pending);
    }

    /** 하루 경계: 시간대의 resetHour시에 날이 바뀐다. */
    public static final class Days {
        private Days() {}
        public static LocalDate today(Instant now, ZoneId zone, int resetHour) {
            int h = Math.max(0, Math.min(23, resetHour));
            return now.atZone(zone).minusHours(h).toLocalDate();
        }
    }
}
