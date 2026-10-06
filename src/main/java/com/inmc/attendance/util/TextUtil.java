package com.inmc.attendance.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MiniMessage 렌더링 + {토큰} 치환. inmc-core Text/TokenBag의 단독 이식판.
 * PlaceholderAPI가 있으면 %...% 를 풀고, 없어도 그대로 둔다 (리플렉션이라 하드 의존 없음).
 */
public final class TextUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private TextUtil() {}

    /** 토큰 주머니. put으로 쌓고 apply로 치환한다. */
    public static final class Tokens {
        private final Map<String, String> values = new LinkedHashMap<>();

        public Tokens player(String v) { return put("player", v); }
        public Tokens value(String v) { return put("value", v); }
        public Tokens count(int v) { return put("count", Integer.toString(v)); }
        public Tokens count(String v) { return put("count", v); }
        public Tokens amount(String v) { return put("amount", v); }
        public Tokens raw(String token, String v) { return put(token, v); }

        private Tokens put(String token, String v) {
            values.put(token, v);
            return this;
        }

        public String apply(String raw) {
            if (raw == null || raw.isEmpty() || values.isEmpty()) return raw == null ? "" : raw;
            String out = raw;
            for (Map.Entry<String, String> e : values.entrySet()) {
                for (String alias : aliases(e.getKey())) {
                    if (out.contains(alias)) out = out.replace(alias, e.getValue());
                }
            }
            return out;
        }

        private static List<String> aliases(String token) {
            return switch (token) {
                case "player" -> List.of("{플레이어네임}", "{플레이어}", "{player}");
                case "value" -> List.of("{값}", "{value}");
                case "count" -> List.of("{개수}", "{count}");
                case "amount" -> List.of("{금액}", "{amount}");
                default -> List.of("{" + token + "}");
            };
        }
    }

    public static Tokens tokens() { return new Tokens(); }

    public static Component render(String raw, Tokens tokens, Player viewer) {
        String text = prepare(raw, tokens, viewer);
        if (text.isEmpty()) return Component.empty();
        return MM.deserialize(text);
    }

    public static Component render(String raw) { return render(raw, null, null); }

    public static Component renderFlat(String raw, Tokens tokens, Player viewer) {
        return render(raw, tokens, viewer).decoration(TextDecoration.ITALIC, false);
    }

    public static Component renderFlat(String raw) { return renderFlat(raw, null, null); }

    public static List<Component> renderLore(List<String> lines, Tokens tokens, Player viewer) {
        return lines.stream().map(l -> renderFlat(l, tokens, viewer)).toList();
    }

    public static String plain(String raw, Tokens tokens, Player viewer) {
        return PLAIN.serialize(render(raw, tokens, viewer));
    }

    public static String plain(Component component) { return PLAIN.serialize(component); }

    /** 명령어 템플릿용: MiniMessage 해석 없이 토큰·PAPI만 치환한다. */
    public static String substituteOnly(String raw, Tokens tokens, Player viewer) {
        if (raw == null || raw.isEmpty()) return "";
        String text = raw;
        if (tokens != null) text = tokens.apply(text);
        if (text.indexOf('%') >= 0) text = applyPapi(viewer, text);
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(Character.isISOControl(c) ? ' ' : c);
        }
        return sb.toString();
    }

    private static String prepare(String raw, Tokens tokens, Player viewer) {
        if (raw == null || raw.isEmpty()) return "";
        String text = raw;
        if (tokens != null) text = tokens.apply(text);
        if (text.indexOf('%') >= 0) text = applyPapi(viewer, text);
        return legacyToMiniMessage(text);
    }

    /** PlaceholderAPI가 켜져 있으면 풀고, 아니면 그대로. 리플렉션이라 없어도 NoClassDefFoundError 없음. */
    private static String applyPapi(Player viewer, String text) {
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return text;
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Object out = api.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class)
                    .invoke(null, viewer, text);
            return out instanceof String s ? s : text;
        } catch (Throwable ignored) {
            return text;
        }
    }

    private static final char SECTION = '§';
    private static final char AMP = '&';

    private static String legacyToMiniMessage(String input) {
        if (input.indexOf(AMP) < 0 && input.indexOf(SECTION) < 0) return input;
        StringBuilder out = new StringBuilder(input.length() + 16);
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if ((c == AMP || c == SECTION) && i + 1 < input.length()) {
                char next = input.charAt(i + 1);
                if (next == '#' && i + 8 <= input.length()) {
                    String hex = input.substring(i + 2, i + 8);
                    if (hex.chars().allMatch(TextUtil::isHex)) {
                        out.append("<#").append(hex).append('>');
                        i += 8;
                        continue;
                    }
                }
                if (Character.toLowerCase(next) == 'r') {
                    out.append("<white><!bold><!italic><!underlined><!strikethrough><!obfuscated>");
                    i += 2;
                    continue;
                }
                String tag = legacyTag(Character.toLowerCase(next));
                if (tag != null) {
                    out.append('<').append(tag).append('>');
                    i += 2;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static String legacyTag(char c) {
        return switch (c) {
            case '0' -> "black";
            case '1' -> "dark_blue";
            case '2' -> "dark_green";
            case '3' -> "dark_aqua";
            case '4' -> "dark_red";
            case '5' -> "dark_purple";
            case '6' -> "gold";
            case '7' -> "gray";
            case '8' -> "dark_gray";
            case '9' -> "blue";
            case 'a' -> "green";
            case 'b' -> "aqua";
            case 'c' -> "red";
            case 'd' -> "light_purple";
            case 'e' -> "yellow";
            case 'f' -> "white";
            case 'k' -> "obfuscated";
            case 'l' -> "bold";
            case 'm' -> "strikethrough";
            case 'n' -> "underlined";
            case 'o' -> "italic";
            default -> null;
        };
    }

    private static boolean isHex(int c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
