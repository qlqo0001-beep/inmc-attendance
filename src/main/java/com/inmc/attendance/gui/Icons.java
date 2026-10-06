package com.inmc.attendance.gui;

import com.inmc.attendance.util.TextUtil;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** 메뉴 장식 아이템. */
public final class Icons {

    private Icons() {}

    public static ItemStack filler() { return blank(Material.GRAY_STAINED_GLASS_PANE); }
    public static ItemStack edge() { return blank(Material.BLACK_STAINED_GLASS_PANE); }

    public static ItemStack blank(Material material) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> meta.displayName(TextUtil.renderFlat(" ")));
        return stack;
    }

    public static ItemStack of(Material material, String name, List<String> lore, Player viewer) {
        ItemStack stack = new ItemStack(material);
        decorate(stack, name, lore, viewer);
        return stack;
    }

    public static ItemStack of(Material material, String name, Player viewer, String... lore) {
        return of(material, name, List.of(lore), viewer);
    }

    public static ItemStack named(ItemStack stack, String name, List<String> lore, Player viewer) {
        stack.editMeta(meta -> {
            meta.displayName(TextUtil.renderFlat(name, null, viewer)
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            if (lore == null || lore.isEmpty()) meta.lore(null);
            else meta.lore(TextUtil.renderLore(lore, null, viewer).stream()
                    .map(c -> c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)).toList());
        });
        return stack;
    }

    private static void decorate(ItemStack stack, String name, List<String> lore, Player viewer) {
        stack.editMeta(meta -> {
            if (name != null) meta.displayName(TextUtil.renderFlat(name, null, viewer));
            if (lore != null && !lore.isEmpty()) meta.lore(TextUtil.renderLore(lore, null, viewer));
        });
    }

    public static String toggle(boolean value) {
        return value ? "<green>켜짐</green>" : "<red>꺼짐</red>";
    }

    public static Material toggleMaterial(boolean value) {
        return value ? Material.LIME_DYE : Material.GRAY_DYE;
    }

    public static ItemStack back() { return of(Material.ARROW, "<gray>◀ 뒤로</gray>", List.of(), null); }
    public static ItemStack close() { return of(Material.BARRIER, "<red>✖ 닫기</red>", List.of(), null); }

    public static ItemStack confirm(Player viewer) {
        return of(Material.LIME_CONCRETE, "<green>✔ 진행합니다</green>",
                List.of("<gray>되돌릴 수 없습니다.</gray>"), viewer);
    }

    public static ItemStack cancel(Player viewer) {
        return of(Material.RED_CONCRETE, "<red>✖ 취소</red>", List.of(), viewer);
    }

    /** 아이템에 메타를 덧씌우지 않고 이름/로어를 합친다 (실제 보상 미리보기용이 아니라 아이콘용). */
    public static void hideAttributes(ItemMeta meta) {}
}
