package com.inmc.attendance.economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.logging.Logger;

/**
 * Vault가 있으면 쓰고 없으면 조용히 넘긴다.
 * 컴파일 타임 의존은 compileOnly라 런타임에 Vault가 없어도 산다.
 * 공급자는 부를 때마다 찾는다 (적재 순서가 보장되지 않으므로).
 */
public final class Economy {

    private final Logger logger;
    private Object cached;
    private boolean logged;

    public Economy(Logger logger) { this.logger = logger; }

    public void setup() {
        cached = null;
        if (find() == null) logger.info("경제 플러그인이 아직 없습니다 - 켜지면 그때부터 씁니다");
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (amount <= 0) return true;
        Object eco = find();
        if (eco == null) return false;
        try {
            Object r = eco.getClass().getMethod("depositPlayer", OfflinePlayer.class, double.class)
                    .invoke(eco, player, amount);
            return (boolean) r.getClass().getMethod("transactionSuccess").invoke(r);
        } catch (Throwable t) {
            return false;
        }
    }

    public String format(double amount) {
        Object eco = find();
        if (eco != null) {
            try {
                Object s = eco.getClass().getMethod("format", double.class).invoke(eco, amount);
                if (s instanceof String str && !str.isBlank()) return str;
            } catch (Throwable ignored) {}
        }
        if (amount == Math.floor(amount)) return String.format("%,d원", (long) amount);
        return String.format("%,.1f원", amount);
    }

    @SuppressWarnings("unchecked")
    private Object find() {
        if (cached != null) return cached;
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) return null;
            Class<?> ecoClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object reg = Bukkit.getServicesManager().getRegistration((Class) ecoClass);
            if (reg == null) return null;
            Object provider = reg.getClass().getMethod("getProvider").invoke(reg);
            if (provider != null) {
                cached = provider;
                if (!logged) {
                    logged = true;
                    try {
                        String name = (String) provider.getClass().getMethod("getName").invoke(provider);
                        logger.info("Vault 연동 활성화 (" + name + ")");
                    } catch (Throwable ignored) {
                        logger.info("Vault 연동 활성화");
                    }
                }
            }
            return provider;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
