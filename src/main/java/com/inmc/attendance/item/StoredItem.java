package com.inmc.attendance.item;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;

/**
 * 보상 아이템 1종의 저장형. inmc-menu 시절 파일과 호환된다.
 * - snapshot(Base64)이 있으면 그것을 그대로 복원 (커스텀 이름·로어·인챈트·외부 아이템 보존)
 * - 없으면 minecraft:재료 참조로 새로 만든다
 */
public final class StoredItem {

    private final String ref;
    private final Material material;
    private final StorageMode mode;
    private final String snapshot;
    private final String displayName;

    public StoredItem(String ref, Material material, String snapshot, String displayName) {
        this(ref, material, StorageMode.REFERENCE, snapshot, displayName);
    }

    public StoredItem(String ref, Material material, StorageMode mode, String snapshot, String displayName) {
        this.ref = ref == null || ref.isBlank() ? "snapshot" : ref.trim();
        this.material = material == null ? Material.CHEST : material;
        this.mode = mode == null ? StorageMode.REFERENCE : mode;
        this.snapshot = snapshot == null || snapshot.isBlank() ? null : snapshot;
        this.displayName = displayName == null || displayName.isBlank() ? null : displayName;
    }

    public String ref() { return ref; }
    public Material material() { return material; }
    public StorageMode mode() { return mode; }
    public String displayName() { return displayName; }

    public StoredItem withMode(StorageMode mode) {
        return new StoredItem(ref, material, mode, snapshot, displayName);
    }

    public String label() {
        if (displayName != null) return displayName;
        if (ref.startsWith("minecraft:")) return ref;
        return ref;
    }

    public void save(ConfigurationSection section) {
        section.set("item", ref);
        section.set("mode", mode.name());
        section.set("material", material.getKey().toString());
        if (displayName != null) section.set("name", displayName);
        if (snapshot != null) section.set("snapshot", snapshot);
    }

    public static StoredItem load(ConfigurationSection section) {
        if (section == null) return null;
        String ref = section.getString("item", "snapshot");
        String snapshot = section.getString("snapshot");
        Material material = Material.matchMaterial(section.getString("material", ""));
        if (material == null) {
            material = matchMaterialLoose(ref);
            if (material == null) material = Material.CHEST;
        }
        if ((ref == null || ref.isBlank() || ref.equalsIgnoreCase("snapshot")) && (snapshot == null || snapshot.isBlank())) {
            return null;
        }
        return new StoredItem(ref, material, StorageMode.parse(section.getString("mode")), snapshot, section.getString("name"));
    }

    /** 스냅샷 우선으로 되살린다. 실패하면 null. */
    public ItemStack create(int amount) {
        return create(amount, null);
    }

    /**
     * urb와 같은 약속.
     * - SNAPSHOT: 등록한 그대로(스냅샷). 스냅샷이 없으면 재료로.
     * - REFERENCE: 살아있는 정의(MMOItems면 지금 정의, 바닐라면 새 재료) — 없으면 스냅샷, 그것도 없으면 재료.
     */
    public ItemStack create(int amount, MMOItemsHook mmo) {
        int safe = Math.max(1, amount);
        if (mode == StorageMode.SNAPSHOT) {
            ItemStack snap = fromSnapshot(safe);
            return snap != null ? snap : freshMaterial(safe);
        }
        if (isMmoRef() && mmo != null && mmo.isEnabled()) {
            String[] parts = ref.split(":", 3);
            if (parts.length == 3) {
                ItemStack live = mmo.create(parts[1], parts[2], safe);
                if (live != null) return live;
            }
        } else if (isVanillaRef()) {
            ItemStack fresh = freshMaterial(safe);
            if (fresh != null) return fresh;
        }
        ItemStack snap = fromSnapshot(safe);
        return snap != null ? snap : freshMaterial(safe);
    }

    private ItemStack fromSnapshot(int safe) {
        if (snapshot == null) return null;
        try {
            ItemStack stack = ItemStack.deserializeBytes(Base64.getDecoder().decode(snapshot));
            stack.setAmount(Math.min(safe, Math.max(1, stack.getMaxStackSize())));
            return stack;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ItemStack freshMaterial(int safe) {
        Material mat = matchMaterialLoose(ref);
        if (mat == null || mat.isAir()) mat = material;
        if (mat == null || mat.isAir()) return null;
        try {
            ItemStack stack = new ItemStack(mat);
            stack.setAmount(Math.min(safe, Math.max(1, stack.getMaxStackSize())));
            return stack;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isVanillaRef() {
        return ref.regionMatches(true, 0, "minecraft:", 0, 10);
    }

    public ItemStack icon() {
        ItemStack stack = create(1);
        return stack == null ? new ItemStack(Material.BARRIER) : stack;
    }

    public boolean isMmoRef() {
        return ref.regionMatches(true, 0, "mmoitems:", 0, 9);
    }

    /** 관리자가 GUI에 올린 아이템을 저장형으로. 스냅샷을 항상 남겨 모양 그대로 준다. */
    public static StoredItem capture(ItemStack stack) {
        return capture(stack, null);
    }

    /**
     * MMOItems 훅이 있으면 참조(mmoitems:TYPE:ID)까지 적어 살아있는 정의와 잇는다.
     * 스냅샷도 같이 남기니 MMOItems가 꺼지거나 ID가 지워져도 보상은 나간다.
     * 겉만 손본 바닐라(커스텀 이름·로어·인챈트)는 참조로 살릴 수 없어 스냅샷으로 굳힌다(core와 같은 약속).
     */
    public static StoredItem capture(ItemStack stack, MMOItemsHook mmo) {
        ItemStack single = stack.clone();
        single.setAmount(1);
        String snap = null;
        try {
            snap = Base64.getEncoder().encodeToString(single.serializeAsBytes());
        } catch (Throwable ignored) {}
        String name = plainName(single);
        if (mmo != null) {
            String mmoRef = mmo.identify(single);
            if (mmoRef != null) return new StoredItem(mmoRef, single.getType(), StorageMode.REFERENCE, snap, name);
        }
        if (isPlainVanilla(single)) {
            String ref = "minecraft:" + single.getType().getKey().getKey().toLowerCase();
            return new StoredItem(ref, single.getType(), StorageMode.REFERENCE, snap, name);
        }
        return new StoredItem("snapshot", single.getType(), StorageMode.SNAPSHOT, snap, name);
    }

    /** 갓 만든 `ItemStack(재료)`와 구분이 안 되면 plain이다 — 참조로 살려도 모양이 같다. */
    static boolean isPlainVanilla(ItemStack stack) {
        try {
            return new ItemStack(stack.getType()).isSimilar(stack);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String plainName(ItemStack stack) {
        try {
            if (stack.getItemMeta() == null || !stack.getItemMeta().hasDisplayName()) return null;
            var comp = stack.getItemMeta().displayName();
            if (comp == null) return null;
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(comp);
            return plain.isBlank() ? null : plain;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Material matchMaterialLoose(String ref) {
        if (ref == null) return null;
        String text = ref.trim();
        if (text.isEmpty()) return null;
        Material m = Material.matchMaterial(text);
        if (m != null) return m;
        int colon = text.indexOf(':');
        if (colon >= 0 && colon + 1 < text.length()) {
            m = Material.matchMaterial(text.substring(colon + 1));
            if (m != null) return m;
            m = Material.matchMaterial("minecraft:" + text.substring(colon + 1).toLowerCase());
            if (m != null) return m;
        }
        return null;
    }
}
