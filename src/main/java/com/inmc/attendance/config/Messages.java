package com.inmc.attendance.config;

import com.inmc.attendance.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/** messages.yml 한 벌. 없으면 기본값, 빈 줄은 "그 메시지 끄기". */
public final class Messages {

    public static final String PREFIX = "prefix";

    private final Map<String, String> values;
    private final Map<String, String> defaults;

    public Messages(Map<String, String> values, Map<String, String> defaults) {
        this.values = values;
        this.defaults = defaults;
    }

    public static Messages load(YamlConfiguration config) {
        Map<String, String> values = new LinkedHashMap<>(DEFAULTS);
        for (String key : config.getKeys(true)) {
            if (config.isConfigurationSection(key)) continue;
            String v = config.getString(key);
            if (v != null) values.put(key, v);
        }
        return new Messages(values, DEFAULTS);
    }

    public String raw(String key) {
        String v = values.get(key);
        if (v == null) v = defaults.get(key);
        return v == null ? "" : v;
    }

    public Component component(String key, TextUtil.Tokens tokens, Player viewer) {
        return TextUtil.render(raw(key), tokens, viewer);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, null);
    }

    public void send(CommandSender sender, String key, TextUtil.Tokens tokens) {
        String text = raw(key);
        if (text.isEmpty()) return;
        Player viewer = sender instanceof Player p ? p : null;
        sender.sendMessage(TextUtil.render(raw(PREFIX) + text, tokens, viewer));
    }

    public static final Map<String, String> DEFAULTS = Map.ofEntries(
        Map.entry(PREFIX, "<gradient:#7f7fd5:#86a8e7>[ 출석 ]</gradient> "),
        Map.entry("player-only", "<red>플레이어만 쓸 수 있습니다.</red>"),
        Map.entry("no-permission", "<red>권한이 없습니다.</red>"),
        Map.entry("reloaded", "<green>다시 불러왔습니다. 출석판 {count}개.</green>"),
        Map.entry("invalid-id", "<red>이름은 소문자 영문·숫자·한글·_- 만, 32자까지입니다: {value}</red>"),
        Map.entry("already-exists", "<red>'{value}' 은(는) 이미 있습니다.</red>"),
        Map.entry("hand-empty", "<red>손에 아이템을 들고 누르세요.</red>"),
        Map.entry("attend-none", "<gray>지금 열린 출석판이 없습니다.</gray>"),
        Map.entry("attend-loading", "<gray>출석 기록을 읽는 중입니다 — 잠시 뒤에 다시 눌러 주세요.</gray>"),
        Map.entry("attend-wait", "<gray>오늘 {count}분 더 접속하면 출석할 수 있습니다.</gray>"),
        Map.entry("attend-already", "<yellow>오늘은 이미 출석했습니다.</yellow>"),
        Map.entry("attend-remind", "<yellow>오늘 아직</yellow> {value} <yellow>출석을 하지 않았습니다.</yellow> <gray>/출석</gray>"),
        Map.entry("attend-done-calendar", "{value} <green>출석 완료!</green> <gray>(이달 {count}일째)</gray>"),
        Map.entry("attend-done-streak", "{value} <green>출석 완료!</green> <gray>(연속 {count}일째)</gray>"),
        Map.entry("attend-done-total", "{value} <green>출석 완료!</green> <gray>(총 {count}번째)</gray>"),
        Map.entry("attend-bonus", "{value} <gold>— 이달 {count}일 출석 보상!</gold>"),
        Map.entry("attend-pending", "<yellow>가방이 가득 차 보상 {count}개를 보관했습니다 — /출석 에서 받으세요.</yellow>"),
        Map.entry("attend-claimed", "<green>보관된 보상 {count}개를 받았습니다.</green>"),
        Map.entry("attend-claim-left", "<yellow>가방이 가득 차 {count}개가 남았습니다.</yellow>"),
        Map.entry("attend-board-created", "<green>'{value}' 출석판을 만들었습니다.</green>"),
        Map.entry("attend-board-deleted", "<yellow>'{value}' 출석판을 지웠습니다.</yellow>"),
        Map.entry("attend-closed", "<gray>{value}<gray> 출석판은 지금 열려 있지 않습니다.</gray>"),
        Map.entry("attend-board-copied", "<green>'{value}' 출석판으로 복사했습니다.</green>"),
        Map.entry("attend-board-reset", "<yellow>'{value}' 출석판을 초기화했습니다 — 모든 사람의 기록이 처음부터입니다.</yellow>"),
        Map.entry("attend-player-reset", "<yellow>{player} 의 '{value}' 출석 기록을 초기화했습니다.</yellow>"),
        Map.entry("attend-player-reset-none", "<gray>{player} 은(는) '{value}' 출석 기록이 없습니다.</gray>"),
        Map.entry("attend-no-board", "<red>'{value}' 출석판이 없습니다.</red>"),
        Map.entry("attend-no-player", "<red>'{player}' 은(는) 이 서버에 온 적이 없는 플레이어입니다.</red>"),
        Map.entry("attend-month-invalid", "<red>달은 2026-11 처럼 적으세요(비우면 늘): {value}</red>"),
        Map.entry("help", "<gold>/출석</gold> <gray>- 출석</gray>\n<red>/출석 관리</red> <gray>- 출석판 만들기·고치기·복사·보상·초기화</gray>\n<red>/출석 초기화 <플레이어> <판></red> <gray>- 한 사람의 기록 초기화</gray>\n<red>/출석 리로드</red> <gray>- 설정 다시 읽기</gray>")
    );
}
