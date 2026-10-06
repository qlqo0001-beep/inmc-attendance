package com.inmc.attendance.gui;

import com.inmc.attendance.AttendancePlugin;
import com.inmc.attendance.attend.AttendanceService;
import com.inmc.attendance.attend.Board;
import com.inmc.attendance.attend.PlayerData;
import com.inmc.attendance.item.StorageMode;
import com.inmc.attendance.item.StoredItem;
import com.inmc.attendance.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 출석 GUI 일체. inmc-menu AttendanceMenus.kt의 Java·무코어 이식판 (입력은 Paper Dialog). */
public final class AttendanceGui {

    private final AttendancePlugin plugin;

    public AttendanceGui(AttendancePlugin plugin) { this.plugin = plugin; }

    public AttendancePlugin plugin() { return plugin; }

    private AttendanceService service() { return plugin.service(); }

    public Component text(String raw, Player viewer) {
        return TextUtil.renderFlat(raw, null, viewer);
    }

    // --- 공용 표시 ---

    public static String cellName(Board board, int n) {
        return switch (board.mode()) {
            case CALENDAR -> n + "일";
            case STREAK -> n + "일째";
            case TOTAL -> n + "번째";
        };
    }

    public List<String> rewardLines(Board.Reward reward) {
        if (reward == null || reward.isEmpty()) return List.of("<dark_gray>보상 없음</dark_gray>");
        List<String> out = new ArrayList<>();
        for (Board.RewardItem e : reward.items()) {
            out.add("<gray>· <white>" + escape(e.item().label()) + "</white> x" + e.amount() + "</gray>");
        }
        if (reward.money() > 0) out.add("<gray>· <gold>" + plugin.economy().format(reward.money()) + "</gold></gray>");
        if (!reward.commands().isEmpty()) out.add("<gray>· 특별 보상 " + reward.commands().size() + "개</gray>");
        return out;
    }

    private static String escape(String s) {
        return s.replace("<", "\\<");
    }

    public ItemStack rewardIcon(Board.Reward reward) {
        if (reward == null || reward.isEmpty()) return null;
        if (!reward.items().isEmpty()) return reward.items().get(0).item().create(1, plugin.mmoItems());
        return new ItemStack(reward.money() > 0 ? Material.GOLD_INGOT : Material.PAPER);
    }

    public static String monthLabel(YearMonth m) {
        return m.getYear() + "년 " + m.getMonthValue() + "월";
    }

    // --- 진입 ---

    public void open(Player player, Runnable back) {
        List<Board> boards = service().enabled();
        if (boards.isEmpty()) {
            plugin.messages().send(player, "attend-none");
            return;
        }
        if (boards.size() == 1) {
            new BoardViewMenu(player, boards.get(0).id(), back).open();
        } else {
            new BoardListMenu(player, back).open();
        }
    }

    public void openAdmin(Player player) {
        new AdminMenu(player).open();
    }

    // --- 판 고르기 ---

    static List<Integer> centerSlots(int count) {
        if (count <= 0) return List.of();
        if (count <= 5) {
            List<Integer> out = new ArrayList<>();
            for (int i = 0; i < count; i++) out.add(9 + (9 - (2 * count - 1)) / 2 + 2 * i);
            return out;
        }
        List<Integer> out = new ArrayList<>();
        int n = Math.min(9, count);
        for (int i = 0; i < n; i++) out.add(9 + (9 - n) / 2 + i);
        return out;
    }

    final class BoardListMenu extends Menu {
        private final Runnable back;
        BoardListMenu(Player viewer, Runnable back) {
            super(AttendanceGui.this, viewer, 27, "출석");
            this.back = back;
        }
        @Override
        public void draw() {
            clear();
            List<Board> boards = service().enabled().stream().limit(9).toList();
            LocalDate today = service().today();
            PlayerData data = service().data(viewer.getUniqueId());
            List<Integer> slots = centerSlots(boards.size());
            for (int i = 0; i < boards.size(); i++) {
                Board board = boards.get(i);
                boolean done = data != null && today.equals(data.record(board).last);
                List<String> lore = new ArrayList<>(List.of(
                    "<gray>" + board.mode().label + " · " + board.claim().label + "</gray>",
                    done ? "<green>오늘 출석함 ✔</green>" : "<yellow>오늘 아직</yellow>",
                    "", "<yellow>▶ 클릭: 열기</yellow>"));
                ItemStack stack = Icons.named(safeIcon(board.icon()), board.name(), lore, viewer);
                if (!done) stack.editMeta(m -> m.setEnchantmentGlintOverride(true));
                final Board b = board;
                set(slots.get(i), stack, e -> new BoardViewMenu(viewer, b.id(), () -> new BoardListMenu(viewer, back).open()).open());
            }
            fillEmpty(Icons.filler());
            navigation(back, 18, 26);
        }
    }

    private static ItemStack safeIcon(Material m) {
        try { return new ItemStack(m); }
        catch (Throwable t) { return new ItemStack(Material.CLOCK); }
    }

    // --- 판 하나 ---

    final class BoardViewMenu extends Menu {
        static final int SIZE = 54;
        static final int CELLS = 45;
        static final int BONUS_ROW = 36;
        static final int SLOT_INFO = 48;
        static final int SLOT_PENDING = 49;

        private final String boardId;
        private final Runnable back;

        BoardViewMenu(Player viewer, String boardId, Runnable back) {
            super(AttendanceGui.this, viewer, SIZE,
                    plugin.service().board(boardId) != null ? plugin.service().board(boardId).name() : boardId);
            this.boardId = boardId;
            this.back = back;
        }

        @Override
        public void draw() {
            clear();
            Board board = service().board(boardId);
            if (board == null) {
                set(22, Icons.of(Material.BARRIER, "<red>출석판이 없습니다.</red>", List.of(), viewer));
                navigation(back);
                return;
            }
            PlayerData data = service().data(viewer.getUniqueId());
            Board.Record record = data != null ? data.record(board) : Board.Record.empty(board.round());
            LocalDate today = service().today();
            List<Board.CellState> states = board.states(record, today);
            int minutes = data != null ? data.minutes(today) : 0;
            for (int i = 0; i < states.size() && i < CELLS; i++) {
                Board.CellState state = states.get(i);
                if (state == Board.CellState.NONE) continue;
                int n = i + 1;
                final Board.CellState st = state;
                final Board b = board;
                set(i, cell(board, n, state, minutes), st == Board.CellState.TODAY ? e -> {
                    service().attend(viewer, b);
                    refresh();
                } : null);
            }
            if (board.mode() == Board.Mode.CALENDAR) {
                YearMonth m = YearMonth.from(today);
                int count = record.daysIn(m).size();
                List<Integer> keys = new ArrayList<>(new TreeMap<>(board.bonuses()).keySet());
                for (int i = 0; i < keys.size() && BONUS_ROW + i < BONUS_ROW + 9; i++) {
                    int threshold = keys.get(i);
                    boolean claimed = record.bonusesIn(m).contains(threshold);
                    Board.Reward reward = board.bonuses().get(threshold);
                    List<String> lore = new ArrayList<>(rewardLines(reward));
                    lore.add("");
                    lore.add(claimed ? "<green>받음 ✔</green>" : "<gray>이달 <white>" + count + "</white>/" + threshold + "일</gray>");
                    ItemStack icon = rewardIcon(reward);
                    ItemStack stack = Icons.named(icon != null ? icon : new ItemStack(Material.CHEST),
                            "<gold>이달 " + threshold + "일 출석</gold>", lore, viewer);
                    if (claimed) stack.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
                    set(BONUS_ROW + i, stack);
                }
            }
            set(SLOT_INFO, info(board, record, today));
            List<ItemStack> pending = service().pending(viewer.getUniqueId());
            if (!pending.isEmpty()) {
                set(SLOT_PENDING, Icons.of(Material.CHEST_MINECART,
                        "<yellow>못 받은 보상 " + pending.size() + "개</yellow>", viewer,
                        "<gray>가방이 가득 차서 보관해 둔 것.</gray>", "", "<yellow>▶ 클릭: 받기</yellow>"),
                        e -> {
                            service().claimPending(viewer);
                            refresh();
                        });
            }
            fillEmpty(Icons.filler());
            navigation(back);
        }

        private ItemStack cell(Board board, int n, Board.CellState state, int minutes) {
            String label = cellName(board, n);
            Board.Reward reward = board.rewards().get(n);
            ItemStack stack;
            String name;
            switch (state) {
                case DONE -> { stack = new ItemStack(Material.LIME_STAINED_GLASS_PANE); name = "<green>" + label + " ✔</green>"; }
                case TODAY_DONE -> { stack = new ItemStack(Material.LIME_STAINED_GLASS_PANE); name = "<green>" + label + " — 오늘 출석함 ✔</green>"; }
                case MISSED -> { stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE); name = "<dark_gray>" + label + " — 놓침</dark_gray>"; }
                case TODAY -> {
                    ItemStack icon = rewardIcon(reward);
                    stack = icon != null ? icon : new ItemStack(Material.CHEST);
                    name = "<yellow><bold>" + label + " — 오늘</bold></yellow>";
                }
                default -> {
                    ItemStack icon = rewardIcon(reward);
                    stack = icon != null ? icon : new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
                    name = "<white>" + label + "</white>";
                }
            }
            List<String> lore = new ArrayList<>(rewardLines(reward));
            if (state == Board.CellState.TODAY) {
                lore.add("");
                if (board.claim() == Board.Claim.MANUAL) lore.add("<yellow>▶ 클릭: 출석</yellow>");
                else if (minutes >= board.autoMinutes()) lore.add("<yellow>▶ 곧 자동으로 출석됩니다</yellow>");
                else lore.add("<gray>" + (board.autoMinutes() - minutes) + "분 더 접속하면 자동으로 출석됩니다</gray>");
            }
            ItemStack out = Icons.named(stack, name, lore, viewer);
            try { out.setAmount(Math.max(1, Math.min(n, Math.max(1, out.getMaxStackSize())))); }
            catch (Throwable ignored) {}
            if (state == Board.CellState.TODAY) out.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            return out;
        }

        private ItemStack info(Board board, Board.Record record, LocalDate today) {
            String progress = switch (board.mode()) {
                case CALENDAR -> "<gray>이달 출석: <white>" + record.daysIn(YearMonth.from(today)).size() + "일</white></gray>";
                case STREAK -> "<gray>연속: <white>" + record.liveStreak(today) + "일</white></gray>";
                case TOTAL -> "<gray>총 출석: <white>" + record.total + "번</white></gray>";
            };
            String claim = board.claim() == Board.Claim.AUTO && board.autoMinutes() > 0
                    ? board.claim().label + " — 접속 " + board.autoMinutes() + "분 뒤"
                    : board.claim().label + " — " + board.claim().help;
            return Icons.named(new ItemStack(Material.BOOK), board.name(), List.of(
                    "<gray>방식: <white>" + board.mode().label + "</white> <dark_gray>" + board.mode().help + "</dark_gray></gray>",
                    "<gray>받는 법: <white>" + claim + "</white></gray>",
                    progress), viewer);
        }
    }

    // --- 관리 ---

    final class AdminMenu extends Menu {
        static final int SIZE = 54;
        static final int SLOT_NEW = 45;
        static final int SLOT_DAY = 49;

        AdminMenu(Player viewer) {
            super(AttendanceGui.this, viewer, SIZE, "출석 관리");
        }

        @Override
        public void draw() {
            clear();
            LocalDate today = service().today();
            List<Board> boards = service().boards().stream().limit(45).toList();
            for (int slot = 0; slot < boards.size(); slot++) {
                Board board = boards.get(slot);
                String state = !board.enabled() ? "<red>꺼짐</red>"
                        : board.openOn(today) ? "<green>켜짐 — 지금 열림</green>"
                        : "<yellow>켜짐 — 지금은 그 달이 아님</yellow>";
                List<String> lore = new ArrayList<>();
                lore.add("<dark_gray>" + board.id() + "</dark_gray>");
                lore.add(state);
                if (board.month() != null) lore.add("<gray>사용할 달: <white>" + monthLabel(board.month()) + "</white></gray>");
                lore.add("<gray>" + board.mode().label + " · " + board.claim().label + " · 칸 " + board.cells() + "개</gray>");
                long filled = board.rewards().values().stream().filter(r -> !r.isEmpty()).count();
                lore.add("<gray>보상 넣은 칸 <white>" + filled + "</white>개</gray>");
                lore.add("");
                lore.add("<yellow>▶ 클릭: 고치기</yellow>");
                lore.add("<aqua>▶ 우클릭: 복사</aqua>");
                lore.add("<red>▶ Shift+우클릭: 지우기</red>");
                ItemStack stack = Icons.named(safeIcon(board.icon()), board.name(), lore, viewer);
                final Board b = board;
                set(slot, stack, event -> {
                    if (event.isShiftClick() && event.isRightClick()) askDelete(b);
                    else if (event.isRightClick()) askCopy(b);
                    else new BoardEditorMenu(viewer, b.id()).open();
                });
            }
            set(SLOT_NEW, Icons.of(Material.LIME_DYE, "<green>새 출석판</green>", viewer,
                    "<gray>클릭하면 입력창으로 이름·방식·받는 법을 묻습니다.</gray>"), e -> askCreate());
            var c = plugin.config();
            set(SLOT_DAY, Icons.of(Material.CLOCK, "<aqua>하루 경계</aqua>", viewer,
                    "<gray>시간대: <white>" + c.timezone() + "</white></gray>",
                    "<gray>날이 바뀌는 시각: <white>" + c.resetHour() + "시</white></gray>",
                    "<gray>수동 판 안내: </gray>" + Icons.toggle(c.remind()),
                    "", "<yellow>▶ 클릭: 고치기</yellow>"), e -> askDay());
            fillEmpty(Icons.filler());
            set(53, Icons.close(), e -> viewer.closeInventory());
        }

        private void askCreate() {
            ask(new DialogForm("<green>새 출석판</green>")
                    .text("id", "이름(소문자 영문·숫자·한글·_-)", "")
                    .text("name", "보이는 이름(MiniMessage)", "<gold>출석</gold>")
                    .choice("mode", "방식", List.of(
                            DialogForm.Option.of(Board.Mode.CALENDAR.name(), Board.Mode.CALENDAR.label + " — " + Board.Mode.CALENDAR.help),
                            DialogForm.Option.of(Board.Mode.STREAK.name(), Board.Mode.STREAK.label + " — " + Board.Mode.STREAK.help),
                            DialogForm.Option.of(Board.Mode.TOTAL.name(), Board.Mode.TOTAL.label + " — " + Board.Mode.TOTAL.help)),
                            Board.Mode.CALENDAR.name())
                    .choice("claim", "받는 법", List.of(
                            DialogForm.Option.of(Board.Claim.AUTO.name(), Board.Claim.AUTO.label + " — " + Board.Claim.AUTO.help),
                            DialogForm.Option.of(Board.Claim.MANUAL.name(), Board.Claim.MANUAL.label + " — " + Board.Claim.MANUAL.help)),
                            Board.Claim.AUTO.name()),
                () -> new AdminMenu(viewer).open(), (p, v) -> {
                    String id = v.text("id").trim().toLowerCase();
                    if (!Board.ID.matcher(id).matches()) {
                        plugin.messages().send(viewer, "invalid-id", TextUtil.tokens().value(id));
                        return;
                    }
                    if (service().board(id) != null) {
                        plugin.messages().send(viewer, "already-exists", TextUtil.tokens().value(id));
                        return;
                    }
                    Board.Mode mode = Board.Mode.parse(v.choice("mode"));
                    Board.Claim claim = Board.Claim.parse(v.choice("claim"));
                    Map<Integer, Board.Reward> b = new LinkedHashMap<>();
                    if (mode == Board.Mode.CALENDAR) {
                        for (int d : Board.DEFAULT_BONUSES) b.put(d, new Board.Reward(List.of(), List.of(), 0));
                    }
                    service().put(new Board(id, v.text("name").isBlank() ? id : v.text("name"),
                            Material.CLOCK, true, mode, claim, 7, Board.AfterEnd.REPEAT,
                            0, Map.of(), b, null, 0));
                    plugin.messages().send(viewer, "attend-board-created", TextUtil.tokens().value(id));
                    new BoardEditorMenu(viewer, id).open();
                });
        }

        private void askCopy(Board board) {
            Board draft = board.copyDraft();
            ask(new DialogForm("<aqua>출석판 복사: " + board.id() + "</aqua>")
                    .line("<gray>보상·설정을 그대로 옮깁니다. 사람들의 기록은 옮기지 않습니다.</gray>")
                    .text("id", "새 이름(소문자 영문·숫자·한글·_-)", draft.id())
                    .text("name", "보이는 이름(MiniMessage)", draft.name())
                    .text("month", "사용할 달(예: 2026-11, 비우면 늘)",
                            draft.month() != null ? draft.month().toString() : ""),
                () -> new AdminMenu(viewer).open(), (p, v) -> {
                    String id = v.text("id").trim().toLowerCase();
                    if (!Board.ID.matcher(id).matches()) {
                        plugin.messages().send(viewer, "invalid-id", TextUtil.tokens().value(id));
                        return;
                    }
                    if (service().board(id) != null) {
                        plugin.messages().send(viewer, "already-exists", TextUtil.tokens().value(id));
                        return;
                    }
                    YearMonth m = null;
                    String t = v.text("month").trim();
                    if (!t.isEmpty()) {
                        try { m = YearMonth.parse(t); }
                        catch (Throwable ex) {
                            plugin.messages().send(viewer, "attend-month-invalid", TextUtil.tokens().value(t));
                            return;
                        }
                    }
                    String name = v.text("name");
                    service().put(new Board(id, name.isBlank() ? id : name, board.icon(),
                            board.enabled(), board.mode(), board.claim(), board.length(),
                            board.afterEnd(), board.autoMinutes(), board.rewards(),
                            board.bonuses(), m, 0));
                    plugin.messages().send(viewer, "attend-board-copied", TextUtil.tokens().value(id));
                    new BoardEditorMenu(viewer, id).open();
                });
        }

        private void askDelete(Board board) {
            new ConfirmMenu(viewer, "<red>'" + board.id() + "' 출석판을 지울까요?</red>",
                    List.of("<gray>보상 설정이 사라집니다. 사람들의 기록은 남습니다.</gray>"),
                    () -> {
                        service().delete(board.id());
                        plugin.messages().send(viewer, "attend-board-deleted", TextUtil.tokens().value(board.id()));
                        new AdminMenu(viewer).open();
                    },
                    () -> new AdminMenu(viewer).open()).open();
        }

        private void askDay() {
            var c = plugin.config();
            ask(new DialogForm("<aqua>출석 하루 경계</aqua>")
                    .text("zone", "시간대(예: Asia/Seoul)", c.timezone())
                    .longValue("hour", "날이 바뀌는 시각(0~23시)", (long) c.resetHour(), 0, 23)
                    .toggle("remind", "수동 판을 안 눌렀으면 알리기", c.remind()),
                () -> new AdminMenu(viewer).open(), (p, v) -> {
                    String zone = v.text("zone").trim();
                    try { java.time.ZoneId.of(zone); }
                    catch (Throwable t) { zone = c.timezone(); }
                    Long hour = v.longValue("hour");
                    plugin.updateConfig(new com.inmc.attendance.config.AttendanceConfig(
                            zone, hour != null ? hour.intValue() : c.resetHour(), v.bool("remind")));
                });
        }
    }

    // --- 판 고치기 ---

    final class BoardEditorMenu extends Menu {
        static final int SIZE = 54;
        private final String boardId;

        BoardEditorMenu(Player viewer, String boardId) {
            super(AttendanceGui.this, viewer, SIZE, "출석판: " + boardId);
            this.boardId = boardId;
        }

        private Board board() { return service().board(boardId); }

        private void save(java.util.function.UnaryOperator<Board> change) {
            Board b = board();
            if (b != null) service().put(change.apply(b));
            refresh();
        }

        @Override
        public void draw() {
            clear();
            Board b = board();
            if (b == null) { navigation(() -> new AdminMenu(viewer).open()); return; }
            set(10, Icons.of(Material.NAME_TAG, "<yellow>이름</yellow>", viewer,
                    "<gray>지금: </gray>" + b.name(), "", "<yellow>▶ 클릭: 바꾸기</yellow>"),
                    e -> ask(new DialogForm("<yellow>출석판 이름</yellow>")
                            .text("name", "보이는 이름(MiniMessage)", b.name()),
                        () -> new BoardEditorMenu(viewer, boardId).open(),
                        (p, v) -> save(x -> x.withName(v.text("name").isBlank() ? x.id() : v.text("name")))));
            set(11, Icons.named(safeIcon(b.icon()), "<yellow>그림</yellow>",
                    List.of("<gray>고르기 화면의 그림.</gray>", "", "<yellow>▶ 클릭: 손에 든 것으로</yellow>"), viewer),
                    e -> {
                        ItemStack hand = viewer.getInventory().getItemInMainHand();
                        if (hand.getType().isAir()) {
                            plugin.messages().send(viewer, "hand-empty");
                            return;
                        }
                        save(x -> x.withIcon(hand.getType()));
                    });
            set(12, Icons.of(Icons.toggleMaterial(b.enabled()), "<yellow>켜짐: </yellow>" + Icons.toggle(b.enabled()), viewer,
                    "<gray>끄면 화면에도 안 나오고 출석도 안 됩니다.</gray>"),
                    e -> save(x -> x.withEnabled(!x.enabled())));
            set(13, Icons.of(Material.COMPASS, "<yellow>방식: </yellow><white>" + b.mode().label + "</white>", viewer,
                    "<gray>" + b.mode().help + "</gray>", "", "<gray>클릭해서 바꾸기</gray>"),
                    e -> save(x -> x.withMode(Board.Mode.values()[(x.mode().ordinal() + 1) % Board.Mode.values().length])));
            set(14, Icons.of(Material.LEVER, "<yellow>받는 법: </yellow><white>" + b.claim().label + "</white>", viewer,
                    "<gray>" + b.claim().help + "</gray>", "", "<gray>클릭해서 바꾸기</gray>"),
                    e -> save(x -> x.withClaim(Board.Claim.values()[(x.claim().ordinal() + 1) % Board.Claim.values().length])));
            if (b.mode() != Board.Mode.CALENDAR) {
                set(15, Icons.of(Material.LADDER, "<yellow>칸 수: </yellow><white>" + b.length() + "칸</white>", viewer,
                        "<gray>1~" + Board.MAX_LENGTH + "</gray>"),
                        e -> ask(new DialogForm("<yellow>칸 수</yellow>")
                                .longValue("length", "칸 수(1~" + Board.MAX_LENGTH + ")", (long) b.length(), 1, Board.MAX_LENGTH),
                            () -> new BoardEditorMenu(viewer, boardId).open(),
                            (p, v) -> {
                                Long n = v.longValue("length");
                                if (n != null) save(x -> x.withLength(n.intValue()));
                            }));
                set(16, Icons.of(Material.REPEATER, "<yellow>끝난 뒤: </yellow><white>" + b.afterEnd().label + "</white>", viewer,
                        "<gray>" + b.length() + "칸을 다 채운 다음 날.</gray>"),
                        e -> save(x -> x.withAfterEnd(Board.AfterEnd.values()[(x.afterEnd().ordinal() + 1) % Board.AfterEnd.values().length])));
            } else {
                List<Integer> keys = new ArrayList<>(new TreeMap<>(b.bonuses()).keySet());
                String joined = keys.isEmpty() ? "없음" : keys.stream().map(k -> k + "일").reduce((a, x) -> a + ", " + x).orElse("없음");
                set(15, Icons.of(Material.GOLD_INGOT, "<yellow>이달 출석 보너스 기준: </yellow><white>" + joined + "</white>", viewer,
                        "<gray>이달 그만큼 출석하면 따로 받는 보상.</gray>"),
                        e -> ask(new DialogForm("<yellow>이달 출석 보너스 기준</yellow>")
                                .text("days", "일 수(쉼표로, 1~31)",
                                        keys.stream().map(String::valueOf).reduce((a, x) -> a + ", " + x).orElse("")),
                            () -> new BoardEditorMenu(viewer, boardId).open(),
                            (p, v) -> {
                                List<Integer> days = new ArrayList<>();
                                for (String part : v.text("days").split("[,\\s]+")) {
                                    try {
                                        int d = Integer.parseInt(part.trim());
                                        if (d >= 1 && d <= 31 && !days.contains(d)) days.add(d);
                                    } catch (Throwable ignored) {}
                                    if (days.size() >= 9) break;
                                }
                                days.sort(Comparator.naturalOrder());
                                Map<Integer, Board.Reward> nb = new LinkedHashMap<>();
                                for (int d : days) nb.put(d, b.bonuses().getOrDefault(d, new Board.Reward(List.of(), List.of(), 0)));
                                save(x -> x.withBonuses(nb));
                            }));
            }
            if (b.claim() == Board.Claim.AUTO) {
                set(19, Icons.of(Material.CLOCK, "<yellow>자동 출석 대기: </yellow><white>"
                        + (b.autoMinutes() > 0 ? "오늘 " + b.autoMinutes() + "분 접속한 뒤" : "들어오자마자") + "</white>", viewer),
                        e -> ask(new DialogForm("<yellow>자동 출석 대기</yellow>")
                                .longValue("minutes", "오늘 접속한 분(0 = 들어오자마자)", (long) b.autoMinutes(), 0, 1440),
                            () -> new BoardEditorMenu(viewer, boardId).open(),
                            (p, v) -> {
                                Long n = v.longValue("minutes");
                                if (n != null) save(x -> x.withAutoMinutes(n.intValue()));
                            }));
            }
            set(20, Icons.of(Material.FILLED_MAP, "<yellow>사용할 달: </yellow><white>"
                    + (b.month() != null ? monthLabel(b.month()) + "만" : "늘 (정하지 않음)") + "</white>", viewer,
                    "<gray>정하면 그 달에만 화면에 나오고 출석됩니다.</gray>",
                    "<gray>다음 달 판을 미리 만들어 두면 1일에 저절로 바뀝니다.</gray>"),
                    e -> ask(new DialogForm("<yellow>사용할 달</yellow>")
                            .text("month", "달(예: 2026-11, 비우면 늘)",
                                    b.month() != null ? b.month().toString() : ""),
                        () -> new BoardEditorMenu(viewer, boardId).open(),
                        (p, v) -> {
                            String t = v.text("month").trim();
                            if (t.isEmpty()) { save(x -> x.withMonth(null)); return; }
                            YearMonth m;
                            try { m = YearMonth.parse(t); }
                            catch (Throwable ex) {
                                plugin.messages().send(viewer, "attend-month-invalid", TextUtil.tokens().value(t));
                                return;
                            }
                            save(x -> x.withMonth(m));
                        }));
            set(31, Icons.of(Material.TNT, "<red>초기화</red>", viewer,
                    "<gray>모든 사람의 이 판 기록을 처음부터 — 접속하지 않은 사람도.</gray>",
                    "<gray>오늘 이미 출석한 사람도 다시 출석해 보상을 받습니다.</gray>",
                    "<gray>보관 중인 못 받은 보상은 그대로입니다.</gray>",
                    "<dark_gray>한 사람만: /출석 초기화 <플레이어> <판></dark_gray>",
                    "", "<red>▶ 클릭: 초기화</red>"),
                    e -> new ConfirmMenu(viewer, "<red>'" + b.id() + "' 출석판을 초기화할까요?</red>",
                        List.of("<gray>모든 사람의 이 판 기록이 처음부터입니다. 되돌릴 수 없습니다.</gray>"),
                        () -> {
                            Board cur = service().board(boardId);
                            if (cur != null) service().resetBoard(cur);
                            plugin.messages().send(viewer, "attend-board-reset", TextUtil.tokens().value(boardId));
                            new BoardEditorMenu(viewer, boardId).open();
                        },
                        () -> new BoardEditorMenu(viewer, boardId).open()).open());
            set(22, Icons.of(Material.CHEST, "<gold>보상 고치기</gold>", viewer,
                    "<gray>칸마다 아이템·돈·명령어.</gray>", "", "<yellow>▶ 클릭</yellow>"),
                    e -> new RewardGridMenu(viewer, boardId).open());
            set(24, Icons.of(Material.SPYGLASS, "<aqua>미리 보기</aqua>", viewer,
                    "<gray>플레이어가 보는 화면(내 기록으로).</gray>"),
                    e -> new BoardViewMenu(viewer, boardId, () -> new BoardEditorMenu(viewer, boardId).open()).open());
            fillEmpty(Icons.filler());
            navigation(() -> new AdminMenu(viewer).open());
        }
    }

    // --- 보상 목록 ---

    final class RewardGridMenu extends Menu {
        static final int SIZE = 54;
        private final String boardId;

        RewardGridMenu(Player viewer, String boardId) {
            super(AttendanceGui.this, viewer, SIZE, "보상: " + boardId);
            this.boardId = boardId;
        }

        @Override
        public void draw() {
            clear();
            Board b = service().board(boardId);
            if (b == null) { navigation(() -> new BoardEditorMenu(viewer, boardId).open()); return; }
            int cells = Math.min(b.cells(), 45);
            for (int n = 1; n <= cells; n++) {
                final int cell = n;
                set(n - 1, cellIcon(cellName(b, n), b.rewards().get(n), n),
                        e -> new RewardEditorMenu(viewer, boardId, cell, false).open());
            }
            if (b.mode() == Board.Mode.CALENDAR) {
                List<Integer> keys = new ArrayList<>(new TreeMap<>(b.bonuses()).keySet());
                for (int i = 0; i < keys.size() && 36 + i < 45; i++) {
                    final int threshold = keys.get(i);
                    set(36 + i, cellIcon("<gold>이달 " + threshold + "일 출석 보너스</gold>",
                            b.bonuses().get(threshold), threshold),
                            e -> new RewardEditorMenu(viewer, boardId, threshold, true).open());
                }
            }
            fillEmpty(Icons.filler());
            navigation(() -> new BoardEditorMenu(viewer, boardId).open());
        }

        private ItemStack cellIcon(String label, Board.Reward reward, int n) {
            ItemStack base = rewardIcon(reward);
            if (base == null) base = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
            List<String> lore = new ArrayList<>(rewardLines(reward));
            lore.add("");
            lore.add("<yellow>▶ 클릭: 고치기</yellow>");
            ItemStack out = Icons.named(base, "<white>" + label + "</white>", lore, viewer);
            try { out.setAmount(Math.max(1, Math.min(n, Math.max(1, out.getMaxStackSize())))); }
            catch (Throwable ignored) {}
            return out;
        }
    }

    // --- 칸 보상 고치기 ---

    final class RewardEditorMenu extends Menu {
        static final int SIZE = 54;
        static final int INPUT_END = 36;
        static final int SLOT_COMMANDS = 47;
        static final int SLOT_MONEY = 48;
        static final int SLOT_HELP = 49;
        static final int SLOT_MODE = 50;
        static final int SLOT_CLEAR = 51;

        private final String boardId;
        private final int cell;
        private final boolean bonus;

        RewardEditorMenu(Player viewer, String boardId, int cell, boolean bonus) {
            super(AttendanceGui.this, viewer, SIZE,
                    "보상 고치기: " + (bonus ? "이달 " + cell + "일 보너스" : cell + "칸"));
            this.boardId = boardId;
            this.cell = cell;
            this.bonus = bonus;
        }

        private Board.Reward reward() {
            Board b = service().board(boardId);
            if (b == null) return new Board.Reward(List.of(), List.of(), 0);
            Board.Reward r = bonus ? b.bonuses().get(cell) : b.rewards().get(cell);
            return r != null ? r : new Board.Reward(List.of(), List.of(), 0);
        }

        private void store(Board.Reward reward) {
            Board b = service().board(boardId);
            if (b == null) return;
            service().put(bonus ? b.withBonus(cell, reward) : b.withReward(cell, reward));
        }

        private void persist() {
            List<Board.RewardItem> items = new ArrayList<>();
            for (int slot = 0; slot < INPUT_END; slot++) {
                ItemStack stack = getInventory().getItem(slot);
                if (stack == null || stack.getType().isAir()) continue;
                items.add(new Board.RewardItem(
                        StoredItem.capture(stack, plugin.mmoItems()), stack.getAmount()));
            }
            Board.Reward cur = reward();
            store(new Board.Reward(items, cur.commands(), cur.money()));
        }

        @Override
        public void draw() {
            clear();
            Board.Reward reward = reward();
            int slot = 0;
            for (Board.RewardItem e : reward.items()) {
                int left = e.amount();
                int guard = 0;
                while (left > 0 && slot < INPUT_END && guard++ < 64) {
                    ItemStack stack = e.item().create(left, plugin.mmoItems());
                    if (stack == null || stack.getAmount() <= 0) break;
                    getInventory().setItem(slot++, stack);
                    left -= stack.getAmount();
                }
            }
            for (int s = INPUT_END; s < SIZE; s++) set(s, Icons.edge());
            List<String> cmdLore = new ArrayList<>();
            if (reward.commands().isEmpty()) cmdLore.add("<dark_gray>(없음)</dark_gray>");
            else for (String c : reward.commands().stream().limit(5).toList()) cmdLore.add("<gray>/ " + c + "</gray>");
            if (reward.commands().size() > 5) cmdLore.add("<dark_gray>… 외 " + (reward.commands().size() - 5) + "개</dark_gray>");
            cmdLore.add("");
            cmdLore.add("<gray>콘솔이 돌린다 · {player} = 받는 사람</gray>");
            cmdLore.add("<yellow>▶ 클릭: 고치기</yellow>");
            set(SLOT_COMMANDS, Icons.of(Material.COMMAND_BLOCK, "<yellow>명령어</yellow>", cmdLore, viewer),
                    e -> ask(new DialogForm("<yellow>명령어</yellow>")
                            .line("<gray>한 줄에 하나. 콘솔이 돌립니다. {player} = 받는 사람.</gray>")
                            .text("commands", "명령어", String.join("\n", reward.commands()), 4096, true),
                        () -> new RewardEditorMenu(viewer, boardId, cell, bonus).open(),
                        (p, v) -> {
                            Board.Reward cur = reward();
                            List<String> cmds = new ArrayList<>();
                            for (String part : v.text("commands").split("\\R")) {
                                String c = part.trim();
                                if (c.startsWith("/")) c = c.substring(1);
                                if (!c.isEmpty()) cmds.add(c);
                            }
                            store(new Board.Reward(cur.items(), cmds, cur.money()));
                        }));
            set(SLOT_MONEY, Icons.of(Material.GOLD_INGOT, "<yellow>돈</yellow>", viewer,
                    "<gray>지금: <white>" + (reward.money() > 0 ? plugin.economy().format(reward.money()) : "없음") + "</white> <dark_gray>(기본 화폐·Vault)</dark_gray></gray>",
                    "", "<yellow>▶ 클릭: 고치기</yellow>"),
                    e -> ask(new DialogForm("<yellow>돈</yellow>")
                            .longValue("money", "금액(0 = 없음)", reward.money(), 0, Long.MAX_VALUE),
                        () -> new RewardEditorMenu(viewer, boardId, cell, bonus).open(),
                        (p, v) -> {
                            Long m = v.longValue("money");
                            if (m == null) return;
                            Board.Reward cur = reward();
                            store(new Board.Reward(cur.items(), cur.commands(), m));
                        }));
            set(SLOT_HELP, Icons.of(Material.PAPER, "<yellow>도움말</yellow>", viewer,
                    "<gray>위 네 줄에 아이템을 올려 두면 그것이 보상입니다(수량 그대로).</gray>",
                    "<gray>닫거나 나갈 때 저장합니다. 올린 아이템은 돌려주지 않습니다 — 견본을 올리세요.</gray>"));
            set(SLOT_MODE, modeIcon(reward), e -> toggleMode());
            set(SLOT_CLEAR, Icons.of(Material.LAVA_BUCKET, "<red>비우기</red>", viewer,
                    "<gray>아이템·명령어·돈 모두.</gray>"),
                    e -> {
                        for (int s = 0; s < INPUT_END; s++) getInventory().setItem(s, null);
                        store(new Board.Reward(List.of(), List.of(), 0));
                        refresh();
                    });
            navigation(() -> new RewardGridMenu(viewer, boardId).open());
        }

        @Override
        public boolean isSlotEditable(int slot) { return slot < INPUT_END; }

        /** 이 칸 보상의 저장 방식 — urb의 아이템별 참조/스냅샷과 같은 뜻. 칸 전체에 한 번에 바꾼다. */
        private ItemStack modeIcon(Board.Reward reward) {
            List<String> lore = new ArrayList<>();
            Material mat;
            String state;
            boolean clickable;
            if (reward.items().isEmpty()) {
                mat = Material.GRAY_DYE;
                state = "<dark_gray>아이템 없음</dark_gray>";
                clickable = false;
            } else {
                boolean allRef = reward.items().stream().allMatch(e -> e.item().mode() == StorageMode.REFERENCE);
                boolean allSnap = reward.items().stream().allMatch(e -> e.item().mode() == StorageMode.SNAPSHOT);
                mat = allRef ? Material.LIME_DYE : Material.GRAY_DYE;
                if (allRef) state = "<white>참조</white> <gray>— MMOItems 원본을 고치면 따라감</gray>";
                else if (allSnap) state = "<white>스냅샷</white> <gray>— 등록한 그대로 나감</gray>";
                else state = "<white>섞임</white> <gray>— 누르면 하나로 통일</gray>";
                clickable = true;
            }
            lore.add("<gray>지금: </gray>" + state);
            lore.add("<gray>바닐라 아이템은 어느 쪽이나 같습니다.</gray>");
            if (clickable) lore.add("<yellow>▶ 클릭: 바꾸기</yellow>");
            return Icons.of(mat, "<yellow>저장 방식</yellow>", lore, viewer);
        }

        private void toggleMode() {
            Board.Reward cur = reward();
            if (cur.items().isEmpty()) return;
            persist();
            Board.Reward saved = reward();
            boolean allRef = saved.items().stream().allMatch(e -> e.item().mode() == StorageMode.REFERENCE);
            StorageMode next = allRef ? StorageMode.SNAPSHOT : StorageMode.REFERENCE;
            List<Board.RewardItem> items = new ArrayList<>();
            for (Board.RewardItem e : saved.items()) {
                items.add(new Board.RewardItem(e.item().withMode(next), e.amount()));
            }
            store(new Board.Reward(items, saved.commands(), saved.money()));
            refresh();
        }

        @Override
        public boolean acceptsShiftInsert() { return true; }

        @Override
        public void onClose(InventoryCloseEvent event) { persist(); }
    }

    // --- 확인창 ---

    final class ConfirmMenu extends Menu {
        private final String question;
        private final List<String> detail;
        private final Runnable onConfirm;
        private final Runnable onCancel;

        ConfirmMenu(Player viewer, String question, List<String> detail, Runnable onConfirm, Runnable onCancel) {
            super(AttendanceGui.this, viewer, 27, "<dark_red>확인</dark_red>");
            this.question = question;
            this.detail = detail;
            this.onConfirm = onConfirm;
            this.onCancel = onCancel;
        }

        @Override
        public void draw() {
            clear();
            fillEmpty(Icons.edge());
            set(13, Icons.of(Material.PAPER, question, detail, viewer));
            set(11, Icons.confirm(viewer), e -> onConfirm.run());
            set(15, Icons.cancel(viewer), e -> onCancel.run());
        }
    }

    /** 클릭 라우팅에서 씀. */
    public static boolean isMenu(org.bukkit.inventory.InventoryHolder holder) {
        return holder instanceof Menu;
    }

    @SuppressWarnings("unused")
    private void unused(InventoryClickEvent e) {}
}
