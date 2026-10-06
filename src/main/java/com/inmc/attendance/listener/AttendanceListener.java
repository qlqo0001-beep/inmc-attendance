package com.inmc.attendance.listener;

import com.inmc.attendance.AttendancePlugin;
import com.inmc.attendance.gui.Menu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** 접속 기록 적재 + 메뉴 클릭 라우팅. */
public final class AttendanceListener implements Listener {

    private final AttendancePlugin plugin;

    public AttendanceListener(AttendancePlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.service().onJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.service().onQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // 남의 화면을 건드리지 않는다 — 보는 사람 본인의 클릭만 받는다.
        if (!menu.getInventory().getViewers().contains(player)) return;
        try {
            menu.handleClick(event);
        } catch (Throwable t) {
            event.setCancelled(true);
            plugin.getLogger().warning("출석 메뉴 클릭 실패: " + t.getMessage());
            player.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Menu menu) {
            menu.onDrag(event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Menu menu) {
            try { menu.onClose(event); }
            catch (Throwable t) { plugin.getLogger().warning("출석 메뉴 닫기 저장 실패: " + t.getMessage()); }
        }
    }
}
