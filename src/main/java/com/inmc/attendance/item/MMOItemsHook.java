package com.inmc.attendance.item;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * MMOItems 연동. 전부 리플렉션이라 컴파일 의존이 없고,
 * MMOItems가 없는 서버에서도 이 클래스는 그냥 조용히 비활성으로 돈다.
 * 식별·생성은 버전마다 옮겨다닌 NBT를 직접 읽지 않고 MMOItems 자신의 API로 한다.
 */
public final class MMOItemsHook {

    private final Logger logger;
    private boolean enabled;
    private Object pluginInstance;
    private Method getItem;
    private Constructor<?> liveCtor;
    private Method mmoGetType;
    private Method mmoGetId;
    private Method typeGetId;

    public MMOItemsHook(Logger logger) { this.logger = logger; }

    public boolean isEnabled() { return enabled; }

    public void setup() {
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            logger.info("MMOItems 미설치 - mmoitems: 참조는 스냅샷으로 대체됩니다");
            return;
        }
        try {
            Class<?> mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
            pluginInstance = mmoItemsClass.getField("plugin").get(null);
            getItem = mmoItemsClass.getMethod("getItem", String.class, String.class);

            Class<?> liveClass = Class.forName("net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem");
            liveCtor = liveClass.getConstructor(ItemStack.class);

            Class<?> mmoItemClass = Class.forName("net.Indyuce.mmoitems.api.item.mmoitem.MMOItem");
            mmoGetType = mmoItemClass.getMethod("getType");
            mmoGetId = mmoItemClass.getMethod("getId");
            typeGetId = mmoGetType.getReturnType().getMethod("getId");

            enabled = true;
            logger.info("MMOItems 연동 활성화");
        } catch (Throwable t) {
            enabled = false;
            logger.warning("MMOItems 연동 실패 (버전 불일치일 수 있습니다): "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    /** stack이 MMOItems 아이템이면 "mmoitems:TYPE:ID", 아니면 null. */
    public String identify(ItemStack stack) {
        if (!enabled) return null;
        try {
            Object mmoItem = liveCtor.newInstance(stack);
            Object type = mmoGetType.invoke(mmoItem);
            if (type == null) return null;
            Object typeId = typeGetId.invoke(type);
            Object id = mmoGetId.invoke(mmoItem);
            if (!(typeId instanceof String ts) || ts.isBlank()) return null;
            if (!(id instanceof String is) || is.isBlank()) return null;
            return "mmoitems:" + ts.toUpperCase() + ":" + is.toUpperCase();
        } catch (Throwable ignored) {
            // 바닐라 등 — LiveMMOItem이 던진다.
            return null;
        }
    }

    /** 살아있는 정의에서 만든다. 없으면 null (부르는 쪽이 스냅샷으로 대체). */
    public ItemStack create(String type, String id, int amount) {
        if (!enabled) return null;
        try {
            Object stack = getItem.invoke(pluginInstance, type, id);
            if (!(stack instanceof ItemStack out) || out.getType().isAir()) return null;
            out.setAmount(Math.max(1, Math.min(amount, Math.max(1, out.getMaxStackSize()))));
            return out;
        } catch (Throwable t) {
            logger.warning("MMOItems 아이템 생성 실패 (mmoitems:" + type + ":" + id + "): " + t.getMessage());
            return null;
        }
    }
}
