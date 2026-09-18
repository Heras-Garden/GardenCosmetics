package com.herasgarden.gardencosmetics.particle;

import java.util.Locale;

public enum ParticleTrigger {
    WALK,
    FLY,
    FALL,
    DAMAGE,
    IDLE,
    TELEPORT;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ParticleTrigger parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException(
                    "Unknown particle trigger. Use walk, fly, fall, damage, idle, or teleport.");
        }
    }
}
