package com.inmc.attendance.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** 자작 인벤토리 메뉴 베이스. 슬롯별 클릭 액션 + 닫기/드래그 제어. */
public abstract class Menu implements InventoryHolder {

    protected final AttendanceGui gui;
    protected final Player viewer;
    private final Inventory inv;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected Menu(AttendanceGui gui, Player viewer, int size, Component title) {
        this.gui = gui;
        this.viewer = viewer;
        this.inv = Bukkit.createInventory(this, size, title);
    }

    protected Menu(AttendanceGui gui, Player viewer, int size, String title) {
        this(gui, viewer, size, gui.text(title, viewer));
    }

    @Override
    public final Inventory getInventory() { return inv; }

    public int size() { return inv.getSize(); }

    public abstract void draw();

    public void onClose(InventoryCloseEvent event) {}

    public boolean isSlotEditable(int slot) { return false; }

    public boolean acceptsShiftInsert() { return false; }

    public void onDrag(InventoryDragEvent event) {
        boolean touchesLocked = event.getRawSlots().stream()
                .anyMatch(s -> s < size() && !isSlotEditable(s));
        if (touchesLocked) event.setCancelled(true);
    }

    public void handleClick(InventoryClickEvent event) {
        int raw = event.getRawSlot();
        boolean inTop = raw >= 0 && raw < size();
        if (!inTop) {
            if (event.isShiftClick() && !acceptsShiftInsert()) event.setCancelled(true);
            return;
        }
        if (!isSlotEditable(raw)) event.setCancelled(true);
        Consumer<InventoryClickEvent> action = actions.get(raw);
        if (action != null) action.accept(event);
    }

    public void open() {
        draw();
        viewer.openInventory(inv);
    }

    public void refresh() { draw(); }

    /** 입력창을 띄운다. 확인하면 onSubmit 뒤에 이 화면을 다시 연다(그 안에서 다른 화면을 열면 그쪽이 이긴다). */
    protected void ask(DialogForm form, Runnable reopen, BiConsumer<Player, DialogForm.Values> onSubmit) {
        form.show(gui.plugin(), viewer, p -> reopen.run(), (p, values) -> {
            onSubmit.accept(p, values);
            if (!(viewer.getOpenInventory().getTopInventory().getHolder() instanceof Menu)) reopen.run();
        });
    }

    protected void clear() {
        inv.clear();
        actions.clear();
    }

    protected void set(int slot, ItemStack stack) { set(slot, stack, null); }

    protected void set(int slot, ItemStack stack, Consumer<InventoryClickEvent> onClick) {
        if (slot < 0 || slot >= size()) return;
        inv.setItem(slot, stack);
        if (onClick != null) actions.put(slot, onClick);
        else actions.remove(slot);
    }

    protected void fillEmpty(ItemStack stack) {
        for (int i = 0; i < size(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, stack);
        }
    }

    /** 뒤로/닫기 버튼. back이 null이면 뒤로 없음. */
    protected void navigation(Runnable back) {
        navigation(back, 45, 53);
    }

    protected void navigation(Runnable back, int backSlot, int closeSlot) {
        if (back != null && backSlot >= 0) set(backSlot, Icons.back(), e -> back.run());
        if (closeSlot >= 0) set(closeSlot, Icons.close(), e -> viewer.closeInventory());
    }
}
