package com.inmc.attendance;

import com.inmc.attendance.attend.AttendanceService;
import com.inmc.attendance.attend.Board;
import com.inmc.attendance.attend.PlayerData;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 출석 순수 계산 — inmc-menu AttendanceTest의 Java 이식판 (서버 없이 돈다). */
class BoardTest {

    private final ZoneId seoul = ZoneId.of("Asia/Seoul");
    private static LocalDate day(String s) { return LocalDate.parse(s); }

    @Test
    void dayBoundary() {
        Instant now = Instant.parse("2026-10-01T15:30:00Z"); // 서울 10-02 00:30
        assertEquals(day("2026-10-02"), AttendanceService.Days.today(now, seoul, 0));
        assertEquals(day("2026-10-01"), AttendanceService.Days.today(now, seoul, 6));
        assertEquals(day("2026-10-01"), AttendanceService.Days.today(now, ZoneId.of("UTC"), 0));
    }

    @Test
    void calendarOnceADayBonusOnceNextMonthFresh() {
        Board board = new Board("d", "d", Material.CLOCK, true, Board.Mode.CALENDAR, Board.Claim.AUTO,
                7, Board.AfterEnd.REPEAT, 0, Map.of(),
                Map.of(2, new Board.Reward(List.of(), List.of(), 10), 3, new Board.Reward(List.of(), List.of(), 20)),
                null, 0);
        Board.Record record = Board.Record.empty(0);
        Board.Attended first = board.attend(record, day("2026-10-30"));
        assertNotNull(first);
        assertEquals(30, first.cell());
        assertEquals(1, first.count());
        assertTrue(first.bonuses().isEmpty());
        record = first.record();
        assertNull(board.attend(record, day("2026-10-30")));

        Board.Attended second = board.attend(record, day("2026-10-31"));
        assertNotNull(second);
        assertEquals(List.of(2), second.bonuses());
        record = second.record();
        assertEquals(Set.of(30, 31), record.days);

        Board.Attended nov = board.attend(record, day("2026-11-01"));
        assertNotNull(nov);
        assertEquals(YearMonth.of(2026, 11), nov.record().month);
        assertEquals(Set.of(1), nov.record().days);
        assertTrue(nov.record().bonuses.isEmpty());
        assertEquals(3, nov.record().total);
        Board.Attended nov2 = board.attend(nov.record(), day("2026-11-05"));
        assertNotNull(nov2);
        assertEquals(List.of(2), nov2.bonuses());
    }

    @Test
    void streakBreakRepeatKeep() {
        Board repeat = new Board("s", "s", Material.CLOCK, true, Board.Mode.STREAK, Board.Claim.AUTO,
                3, Board.AfterEnd.REPEAT, 0, Map.of(), Map.of(), null, 0);
        Board.Record record = Board.Record.empty(0);
        List<Integer> cells = new java.util.ArrayList<>();
        LocalDate date = day("2026-10-01");
        for (int i = 0; i < 5; i++) {
            Board.Attended r = repeat.attend(record, date);
            assertNotNull(r);
            cells.add(r.cell());
            record = r.record();
            date = date.plusDays(1);
        }
        assertEquals(List.of(1, 2, 3, 1, 2), cells);
        assertEquals(5, record.streak);

        Board.Attended broken = repeat.attend(record, date.plusDays(1));
        assertNotNull(broken);
        assertEquals(1, broken.cell());
        assertEquals(1, broken.record().streak);

        Board keep = new Board("k", "k", Material.CLOCK, true, Board.Mode.STREAK, Board.Claim.AUTO,
                3, Board.AfterEnd.KEEP_LAST, 0, Map.of(), Map.of(), null, 0);
        assertEquals(List.of(1, 2, 3, 3, 3),
                java.util.stream.IntStream.rangeClosed(1, 5).map(keep::position).boxed().toList());
    }

    @Test
    void totalContinuesAfterGap() {
        Board board = new Board("t", "t", Material.CLOCK, true, Board.Mode.TOTAL, Board.Claim.AUTO,
                2, Board.AfterEnd.REPEAT, 0, Map.of(), Map.of(), null, 0);
        Board.Attended a = board.attend(Board.Record.empty(0), day("2026-10-01"));
        Board.Attended b = board.attend(a.record(), day("2026-10-09"));
        Board.Attended c = board.attend(b.record(), day("2026-12-25"));
        assertNotNull(a); assertNotNull(b); assertNotNull(c);
        assertEquals(List.of(1, 2, 1), List.of(a.cell(), b.cell(), c.cell()));
        assertEquals(3, c.count());
    }

    @Test
    void calendarStates() {
        Board board = new Board("d", "d", Material.CLOCK, true, Board.Mode.CALENDAR, Board.Claim.AUTO,
                7, Board.AfterEnd.REPEAT, 0, Map.of(), Map.of(), null, 0);
        LocalDate today = day("2026-02-10");
        Board.Record record = new Board.Record(day("2026-02-09"), 0, 2,
                YearMonth.of(2026, 2), Set.of(1, 9), Set.of(), 0);
        List<Board.CellState> states = board.states(record, today);
        assertEquals(31, states.size());
        assertEquals(Board.CellState.DONE, states.get(0));
        assertEquals(Board.CellState.MISSED, states.get(1));
        assertEquals(Board.CellState.DONE, states.get(8));
        assertEquals(Board.CellState.TODAY, states.get(9));
        assertEquals(Board.CellState.LATER, states.get(10));
        assertEquals(Board.CellState.NONE, states.get(28));
        Board.Record after = board.attend(record, today).record();
        assertEquals(Board.CellState.TODAY_DONE, board.states(after, today).get(9));
        assertEquals(Board.CellState.MISSED, board.states(record, day("2026-03-02")).get(0));
    }

    @Test
    void monthGate() {
        Board nov = new Board("nov", "nov", Material.CLOCK, true, Board.Mode.CALENDAR, Board.Claim.AUTO,
                7, Board.AfterEnd.REPEAT, 0, Map.of(), Map.of(), YearMonth.of(2026, 11), 0);
        assertFalse(nov.openOn(day("2026-10-31")));
        assertTrue(nov.openOn(day("2026-11-01")));
        assertFalse(nov.openOn(day("2026-12-01")));
    }

    @Test
    void copyDraftShiftsMonth() {
        Board oct = new Board("2026-10", "<gold>10월 출석</gold>", Material.CLOCK, true,
                Board.Mode.CALENDAR, Board.Claim.AUTO, 7, Board.AfterEnd.REPEAT, 0,
                Map.of(1, new Board.Reward(List.of(), List.of(), 100)), Map.of(),
                YearMonth.of(2026, 10), 3);
        Board draft = oct.copyDraft();
        assertEquals("2026-11", draft.id());
        assertEquals("<gold>11월 출석</gold>", draft.name());
        assertEquals(YearMonth.of(2026, 11), draft.month());
        assertEquals(0, draft.round());
        assertEquals("10월출석_복사", new Board("10월출석", "x", Material.CLOCK, true,
                Board.Mode.CALENDAR, Board.Claim.AUTO, 7, Board.AfterEnd.REPEAT, 0,
                Map.of(), Map.of(), null, 0).copyDraft().id());
    }

    @Test
    void roundResetsRecord() {
        Board board = new Board("d", "d", Material.CLOCK, true, Board.Mode.CALENDAR, Board.Claim.AUTO,
                7, Board.AfterEnd.REPEAT, 0, Map.of(), Map.of(), null, 0);
        PlayerData pd = new PlayerData(UUID.randomUUID());
        LocalDate today = day("2026-10-02");
        pd.records.put("d", board.attend(pd.record(board), today).record());
        assertNull(board.attend(pd.record(board), today));

        Board reset = board.withRound(1);
        assertEquals(1, pd.record(reset).round);
        assertTrue(pd.record(reset).days.isEmpty());
        Board.Attended again = reset.attend(pd.record(reset), today);
        assertNotNull(again);
        assertEquals(1, again.record().round);
        assertEquals(1, again.record().total);
    }

    @Test
    void boardFileRoundTrip() throws Exception {
        com.inmc.attendance.item.StoredItem diamond =
                new com.inmc.attendance.item.StoredItem("minecraft:diamond", Material.DIAMOND, null, null);
        Board board = new Board("week", "<gold>주간</gold>", Material.DIAMOND, false,
                Board.Mode.STREAK, Board.Claim.MANUAL, 7, Board.AfterEnd.KEEP_LAST, 15,
                Map.of(1, new Board.Reward(List.of(new Board.RewardItem(diamond, 3)), List.of("say {player}"), 500)),
                Map.of(7, new Board.Reward(List.of(), List.of(), 0)), YearMonth.of(2026, 11), 2);
        YamlConfiguration saved = board.save();
        Board back = Board.load("week", toYaml(saved.saveToString()));
        assertEquals(board.id(), back.id());
        assertEquals(board.name(), back.name());
        assertEquals(board.icon(), back.icon());
        assertEquals(board.mode(), back.mode());
        assertEquals(board.claim(), back.claim());
        assertEquals(board.length(), back.length());
        assertEquals(board.afterEnd(), back.afterEnd());
        assertEquals(board.autoMinutes(), back.autoMinutes());
        assertEquals(board.month(), back.month());
        assertEquals(board.round(), back.round());
        assertEquals(1, back.rewards().size());
        assertEquals(3, back.rewards().get(1).items().get(0).amount());
        assertEquals("minecraft:diamond", back.rewards().get(1).items().get(0).item().ref());
    }

    private static YamlConfiguration toYaml(String text) {
        YamlConfiguration y = new YamlConfiguration();
        try { y.loadFromString(text); } catch (Exception e) { throw new RuntimeException(e); }
        return y;
    }
}
