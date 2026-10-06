package com.inmc.attendance.item;

/** 보상 아이템을 살린 채로 둘지, 등록한 그대로 굳힐지. urb/core와 같은 뜻. */
public enum StorageMode {
    /** 살아있는 정의에서 다시 만든다 — MMOItems 원본을 고치면 따라간다. */
    REFERENCE,
    /** 등록 때 스냅샷 그대로 — 원본이 바뀌거나 지워져도 그 모습으로 나간다. */
    SNAPSHOT;

    public StorageMode toggle() {
        return this == REFERENCE ? SNAPSHOT : REFERENCE;
    }

    public static StorageMode parse(String raw) {
        if (raw == null) return REFERENCE;
        for (StorageMode m : values()) {
            if (m.name().equalsIgnoreCase(raw.trim())) return m;
        }
        return REFERENCE;
    }
}
