package com.herasgarden.gardencosmetics.particle;

public record ParticleSetting(
        ParticleTrigger trigger,
        String particleKey,
        String colorHex,
        String formationKey,
        boolean enabled
) {
}
