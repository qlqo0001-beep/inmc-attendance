package com.inmc.attendance.gui;

import com.inmc.attendance.AttendancePlugin;
import com.inmc.attendance.util.TextUtil;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 값 몇 개를 받는 입력창 — 확인/취소. inmc-core DialogForm의 Java·무코어 이식판.
 * - 콜백은 메인 스레드(그 플레이어의 스케줄러)에서, 한 틱 뒤에 돈다.
 * - 숫자 칸은 글자 입력으로 받아 여기서 읽는다. 못 읽거나 범위 밖이면 친 값을
 *   그대로 둔 채 빨간 줄과 함께 다시 연다.
 * - Esc는 취소와 같다. 창을 열기 전에 열린 상자 화면을 닫는다.
 */
public final class DialogForm {

    public record Option(String id, String display) {
        public static Option of(String id, String display) { return new Option(id, display); }
    }

    private sealed interface Field permits TextField, LongField, ToggleField, ChoiceField {
        String key();
        String label();
    }

    private record TextField(String key, String label, String initial, int maxLength, boolean multiline) implements Field {}
    private record LongField(String key, String label, Long initial, long min, long max) implements Field {}
    private record ToggleField(String key, String label, boolean initial) implements Field {}
    private record ChoiceField(String key, String label, List<Option> options, String initial) implements Field {}

    private static final Set<String> RESERVED_INPUT_KEYS = Set.of("id");
    private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES)
            .lifetime(Duration.ofMinutes(30))
            .build();
    private static final int WIDTH = 240;

    private final String title;
    private final List<String> lines = new ArrayList<>();
    private final List<Field> fields = new ArrayList<>();
    private String submitLabel = "<green>확인</green>";
    private String cancelLabel = "<gray>취소</gray>";

    public DialogForm(String title) { this.title = title; }

    public DialogForm line(String text) { lines.add(text); return this; }

    public DialogForm text(String key, String label, String initial) {
        return text(key, label, initial, 256, false);
    }

    public DialogForm text(String key, String label, String initial, int maxLength, boolean multiline) {
        fields.add(new TextField(key, label, initial == null ? "" : initial,
                Math.max(1, Math.min(32767, maxLength)), multiline));
        return this;
    }

    public DialogForm longValue(String key, String label, Long initial, long min, long max) {
        fields.add(new LongField(key, label, initial, min, max));
        return this;
    }

    public DialogForm toggle(String key, String label, boolean initial) {
        fields.add(new ToggleField(key, label, initial));
        return this;
    }

    public DialogForm choice(String key, String label, List<Option> options, String initial) {
        if (options != null && !options.isEmpty()) fields.add(new ChoiceField(key, label, List.copyOf(options), initial));
        return this;
    }

    /** 받은 값. 숫자 칸은 이미 검사를 통과했다. */
    public static final class Values {
        private final Map<String, String> texts;
        private final Map<String, Boolean> toggles;

        Values(Map<String, String> texts, Map<String, Boolean> toggles) {
            this.texts = texts;
            this.toggles = toggles;
        }

        public String text(String key) { return texts.getOrDefault(key, ""); }
        public Long longValue(String key) { return parseLong(texts.get(key)); }
        public boolean bool(String key) { return toggles.getOrDefault(key, false); }
        public String choice(String key) { return texts.get(key); }
    }

    public void show(AttendancePlugin plugin, Player player,
                     Consumer<Player> onCancel, BiConsumer<Player, Values> onSubmit) {
        show(plugin, player, Map.of(), null, onCancel, onSubmit);
    }

    private void show(AttendancePlugin plugin, Player player,
                      Map<String, String> typed, String error,
                      Consumer<Player> onCancel, BiConsumer<Player, Values> onSubmit) {
        ActionButton submit = button(plugin, submitLabel, (who, view) -> {
            Map<String, String> texts = new HashMap<>();
            Map<String, Boolean> toggles = new HashMap<>();
            for (Field field : fields) {
                String key = inputKey(field.key());
                if (field instanceof ToggleField tf) {
                    Boolean v = view.getBoolean(key);
                    toggles.put(field.key(), v != null ? v : tf.initial());
                } else {
                    texts.put(field.key(), view.getText(key));
                }
            }
            String problem = validate(texts);
            if (problem != null) show(plugin, who, texts, problem, onCancel, onSubmit);
            else onSubmit.accept(who, new Values(texts, toggles));
        });
        ActionButton cancel = button(plugin, cancelLabel, (who, view) -> onCancel.accept(who));

        List<String> bodyLines = new ArrayList<>();
        if (error != null) bodyLines.add("<red>" + error.replace("<", "\\<") + "</red>");
        bodyLines.addAll(lines);

        Dialog dialog = Dialog.create(factory -> {
            factory.empty()
                    .base(DialogBase.builder(TextUtil.renderFlat(title))
                            .canCloseWithEscape(true)
                            .pause(false)
                            .afterAction(DialogBase.DialogAfterAction.CLOSE)
                            .body(body(bodyLines))
                            .inputs(fields.stream().map(f -> input(f, typed)).toList())
                            .build())
                    .type(DialogType.confirmation(submit, cancel));
        });
        player.getScheduler().run(plugin, task -> {
            if (!player.isOnline()) return;
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) player.closeInventory();
            player.showDialog(dialog);
        }, null);
    }

    private String validate(Map<String, String> texts) {
        for (Field field : fields) {
            if (!(field instanceof LongField lf)) continue;
            String raw = texts.getOrDefault(field.key(), "").trim();
            if (raw.isEmpty()) return TextUtil.plain(field.label(), null, null) + ": 값을 넣어 주세요";
            Long value = parseLong(raw);
            if (value == null) return TextUtil.plain(field.label(), null, null) + ": 정수를 넣어 주세요 (" + raw + ")";
            if (value < lf.min() || value > lf.max()) {
                return TextUtil.plain(field.label(), null, null) + ": " + lf.min() + " ~ " + lf.max() + " 사이로 넣어 주세요";
            }
        }
        return null;
    }

    private DialogInput input(Field field, Map<String, String> typed) {
        String key = inputKey(field.key());
        Component label = TextUtil.renderFlat(field.label());
        if (field instanceof TextField tf) {
            var b = DialogInput.text(key, label)
                    .maxLength(tf.maxLength())
                    .width(WIDTH)
                    .initial(typed.getOrDefault(field.key(), tf.initial()));
            if (tf.multiline()) b.multiline(TextDialogInput.MultilineOptions.create(8, 120));
            return b.build();
        }
        if (field instanceof LongField lf) {
            Long initial = lf.initial();
            return DialogInput.text(key, label).maxLength(24).width(WIDTH)
                    .initial(typed.getOrDefault(field.key(),
                            initial != null ? initial.toString() : ""))
                    .build();
        }
        if (field instanceof ToggleField tf) {
            return DialogInput.bool(key, label).initial(tf.initial()).build();
        }
        if (field instanceof ChoiceField cf) {
            String chosen = typed.getOrDefault(field.key(),
                    cf.initial() != null ? cf.initial() : cf.options().get(0).id());
            return DialogInput.singleOption(key, label, cf.options().stream()
                            .map(o -> SingleOptionDialogInput.OptionEntry.create(
                                    o.id(), TextUtil.renderFlat(o.display()), o.id().equals(chosen)))
                            .toList())
                    .width(WIDTH).build();
        }
        throw new IllegalStateException("unknown field: " + field);
    }

    private List<DialogBody> body(List<String> lines) {
        if (lines.isEmpty()) return List.of();
        Component joined = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) joined = joined.append(Component.newline());
            joined = joined.append(TextUtil.renderFlat(lines.get(i)));
        }
        return List.of(DialogBody.plainMessage(joined, 320));
    }

    private ActionButton button(AttendancePlugin plugin, String label,
                               BiConsumer<Player, DialogResponseView> onClick) {
        return ActionButton.builder(TextUtil.renderFlat(label)).width(150).action(
                DialogAction.customClick((DialogResponseView view, Audience audience) -> {
                    if (!(audience instanceof Player player)) return;
                    player.getScheduler().runDelayed(plugin, task -> {
                        if (player.isOnline()) onClick.accept(player, view);
                    }, null, 1L);
                }, CALLBACK_OPTIONS)).build();
    }

    /** Paper가 클릭 콜백의 자기 기록(id)을 입력값과 같은 자리에 둔다. 예약어는 접두사를 붙인다. */
    static String inputKey(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            sb.append(Character.isLetterOrDigit(c) || c == '_' ? c : '_');
        }
        String safe = sb.length() == 0 ? "_" : sb.toString();
        return RESERVED_INPUT_KEYS.contains(safe) ? "in_" + safe : safe;
    }

    static Long parseLong(String raw) {
        if (raw == null) return null;
        try {
            return Long.parseLong(raw.replace(",", "").replace("_", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
