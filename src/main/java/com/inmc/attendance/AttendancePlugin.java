package com.inmc.attendance;

import com.inmc.attendance.attend.AttendanceService;
import com.inmc.attendance.command.AttendanceCommand;
import com.inmc.attendance.config.AttendanceConfig;
import com.inmc.attendance.config.Messages;
import com.inmc.attendance.economy.Economy;
import com.inmc.attendance.gui.AttendanceGui;
import com.inmc.attendance.item.MMOItemsHook;
import com.inmc.attendance.listener.AttendanceListener;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * inmc-attendance — 출석체크 단독 플러그인.
 * inmc-core/menu 의존 없음. Paper 26.1+.
 *
 * inmc-menu에서 옮겨올 때: inmc-menu/attendance/ 와 inmc-menu/attendance-data/ 를
 * 이 플러그인 데이터 폴더(plugins/inmc-attendance/)에 그대로 복사하면 판·기록이 이어진다.
 */
public final class AttendancePlugin extends JavaPlugin {

    public static final String USE = "inmcattendance.use";
    public static final String ADMIN = "inmcattendance.admin";
    /** 구 권한 (inmc-menu 시절). 새 권한과 둘 중 하나만 있어도 된다. */
    public static final String LEGACY_USE = "inmcmenu.attendance";
    public static final String LEGACY_ADMIN = "inmcmenu.admin";

    private AttendanceConfig config = AttendanceConfig.defaults();
    private Messages messages = Messages.load(new YamlConfiguration());
    private Economy economy;
    private MMOItemsHook mmoItems;
    private AttendanceService service;
    private AttendanceGui gui;

    public AttendanceConfig config() { return config; }
    public Messages messages() { return messages; }
    public Economy economy() { return economy; }
    public MMOItemsHook mmoItems() { return mmoItems; }
    public AttendanceService service() { return service; }
    public AttendanceGui gui() { return gui; }

    public boolean hasUse(Player player) {
        return player.hasPermission(USE) || player.hasPermission(LEGACY_USE);
    }

    public boolean hasAdmin(Player player) {
        return player.hasPermission(ADMIN) || player.hasPermission(LEGACY_ADMIN);
    }

    @Override
    public void onEnable() {
        saveDefaultConfigFiles();
        reloadLocal();
        economy = new Economy(getLogger());
        economy.setup();
        mmoItems = new MMOItemsHook(getLogger());
        mmoItems.setup();
        service = new AttendanceService(this);
        gui = new AttendanceGui(this);
        service.loadBoards();

        Bukkit.getPluginManager().registerEvents(new AttendanceListener(this), this);
        new AttendanceCommand(this).register(this);

        // 1분마다: 접속 분 누적 + 자동 출석 + 자정 롤오버 안내
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            try { service.tick(); }
            catch (Throwable t) { getLogger().warning("출석 틱 실패: " + t.getMessage()); }
        }, 1200L, 1200L);

        service.loadOnline();
        getLogger().info("inmc-attendance 활성화 - 출석판 " + service.boards().size() + "개");
    }

    @Override
    public void onDisable() {
        if (service != null) service.shutdown();
    }

    private void saveDefaultConfigFiles() {
        saveResource("config.yml", false);
        saveResource("messages.yml", false);
    }

    /** 설정·메시지·출석판을 다시 읽는다. 열린 출석 화면은 닫는다 (버려진 판 편집 방지). */
    public void reloadLocal() {
        YamlConfiguration configYml = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));
        config = AttendanceConfig.from(configYml);
        YamlConfiguration msgYml = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        messages = Messages.load(msgYml);
        if (service != null) service.loadBoards();
    }

    public void closeMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof com.inmc.attendance.gui.Menu) {
                player.closeInventory();
            }
        }
    }

    public void updateConfig(AttendanceConfig next) {
        config = next;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try { next.toYaml().save(new File(getDataFolder(), "config.yml")); }
            catch (Exception e) { getLogger().severe("config.yml 저장 실패: " + e.getMessage()); }
        });
    }
}
