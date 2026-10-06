package com.inmc.attendance;

import com.inmc.attendance.item.StorageMode;
import com.inmc.attendance.item.StoredItem;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** StoredItem 저장 호환 — inmc-menu 시절 파일도 읽힌다. 서버 없이 돈다. */
class StoredItemTest {

    private static YamlConfiguration toYaml(String text) {
        YamlConfiguration y = new YamlConfiguration();
        try { y.loadFromString(text); } catch (Exception e) { throw new RuntimeException(e); }
        return y;
    }

    @Test
    void vanillaRoundTrip() {
        StoredItem item = new StoredItem("minecraft:diamond", Material.DIAMOND, null, null);
        YamlConfiguration saved = new YamlConfiguration();
        item.save(saved.createSection("i"));
        StoredItem back = StoredItem.load(toYaml(saved.saveToString()).getConfigurationSection("i"));
        assertNotNull(back);
        assertEquals("minecraft:diamond", back.ref());
        assertEquals(Material.DIAMOND, back.material());
        assertFalse(back.isMmoRef());
    }

    @Test
    void mmoRefRoundTripKeepsLiveLink() {
        StoredItem item = new StoredItem("mmoitems:SWORD:EXCALIBUR", Material.DIAMOND_SWORD, "QUJD", "엑스칼리버");
        YamlConfiguration saved = new YamlConfiguration();
        item.save(saved.createSection("i"));
        StoredItem back = StoredItem.load(toYaml(saved.saveToString()).getConfigurationSection("i"));
        assertNotNull(back);
        assertTrue(back.isMmoRef());
        assertEquals("mmoitems:SWORD:EXCALIBUR", back.ref());
        assertEquals(Material.DIAMOND_SWORD, back.material());
    }

    @Test
    void legacyMenuFileLoads() {
        // inmc-menu 시절 보상 칸 형식 그대로
        YamlConfiguration y = toYaml("""
                item: minecraft:diamond
                mode: REFERENCE
                material: minecraft:diamond
                """);
        StoredItem back = StoredItem.load(y);
        assertNotNull(back);
        assertEquals("minecraft:diamond", back.ref());
    }

    @Test
    void modeRoundTrip() {
        for (StorageMode mode : StorageMode.values()) {
            StoredItem item = new StoredItem("mmoitems:SWORD:EXCALIBUR", Material.DIAMOND_SWORD, mode, "QUJD", null);
            YamlConfiguration saved = new YamlConfiguration();
            item.save(saved.createSection("i"));
            StoredItem back = StoredItem.load(toYaml(saved.saveToString()).getConfigurationSection("i"));
            assertNotNull(back);
            assertEquals(mode, back.mode());
            assertEquals(mode.toggle(), back.withMode(mode.toggle()).mode());
        }
        assertEquals(StorageMode.SNAPSHOT, StorageMode.REFERENCE.toggle());
        assertEquals(StorageMode.REFERENCE, StorageMode.SNAPSHOT.toggle());
    }

    @Test
    void missingModeDefaultsToReference() {
        // 구 파일(키 없음)은 참조로 — MMO 살아있는 연결이 자동으로 붙는다
        YamlConfiguration y = toYaml("item: mmoitems:SWORD:EXCALIBUR\nmaterial: minecraft:diamond_sword\nsnapshot: QUJD\n");
        StoredItem back = StoredItem.load(y);
        assertNotNull(back);
        assertEquals(StorageMode.REFERENCE, back.mode());
        assertEquals(StorageMode.REFERENCE, StorageMode.parse(null));
        assertEquals(StorageMode.REFERENCE, StorageMode.parse("???"));
        assertEquals(StorageMode.SNAPSHOT, StorageMode.parse("snapshot"));
    }

    @Test
    void garbageLoadsNull() {
        YamlConfiguration y = toYaml("item: snapshot\n");
        assertNull(StoredItem.load(y));
        assertNull(StoredItem.load(null));
    }
}
