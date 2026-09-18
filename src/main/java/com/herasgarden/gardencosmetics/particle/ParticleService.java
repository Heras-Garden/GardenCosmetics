package com.herasgarden.gardencosmetics.particle;

import com.herasgarden.gardencore.api.GardenPlatform;
import com.herasgarden.gardencosmetics.CosmeticService;
import org.bukkit.Particle;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class ParticleService {
    private final GardenPlatform platform;
    private final CosmeticService cosmetics;
    private final String entitlementKey;
    private final Set<String> allowedParticles;
    private final Set<String> allowedFormations;
    private final Map<UUID, Map<ParticleTrigger, ParticleSetting>> cache =
            new ConcurrentHashMap<>();

    public ParticleService(
            GardenPlatform platform,
            CosmeticService cosmetics,
            String entitlementKey,
            Set<String> allowedParticles,
            Set<String> allowedFormations
    ) {
        this.platform = platform;
        this.cosmetics = cosmetics;
        this.entitlementKey = entitlementKey;
        this.allowedParticles = allowedParticles.stream()
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        this.allowedFormations = allowedFormations.stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public void refreshAll() throws SQLException {
        Map<UUID, EnumMap<ParticleTrigger, ParticleSetting>> loaded = new LinkedHashMap<>();
        try (Connection connection = platform.storage().connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid, trigger_key, particle_key, color_hex, formation_key, enabled "
                             + "FROM gco_particle_settings");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                ParticleTrigger trigger;
                try {
                    trigger = ParticleTrigger.parse(result.getString("trigger_key"));
                } catch (IllegalArgumentException exception) {
                    continue;
                }
                UUID playerId = UUID.fromString(result.getString("player_uuid"));
                loaded.computeIfAbsent(playerId, ignored -> new EnumMap<>(ParticleTrigger.class))
                        .put(trigger, new ParticleSetting(
                                trigger,
                                result.getString("particle_key"),
                                result.getString("color_hex"),
                                result.getString("formation_key"),
                                result.getBoolean("enabled")
                        ));
            }
        }

        cache.clear();
        loaded.forEach((playerId, settings) ->
                cache.put(playerId, Map.copyOf(settings)));
    }

    public void refresh(UUID playerId) throws SQLException {
        EnumMap<ParticleTrigger, ParticleSetting> loaded =
                new EnumMap<>(ParticleTrigger.class);
        try (Connection connection = platform.storage().connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT trigger_key, particle_key, color_hex, formation_key, enabled "
                             + "FROM gco_particle_settings WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ParticleTrigger trigger;
                    try {
                        trigger = ParticleTrigger.parse(result.getString("trigger_key"));
                    } catch (IllegalArgumentException exception) {
                        continue;
                    }
                    loaded.put(trigger, new ParticleSetting(
                            trigger,
                            result.getString("particle_key"),
                            result.getString("color_hex"),
                            result.getString("formation_key"),
                            result.getBoolean("enabled")
                    ));
                }
            }
        }

        if (loaded.isEmpty()) cache.remove(playerId);
        else cache.put(playerId, Map.copyOf(loaded));
    }

    public Optional<ParticleSetting> setting(UUID playerId, ParticleTrigger trigger) {
        if (!cosmetics.hasEntitlement(playerId, entitlementKey)) {
            return Optional.empty();
        }
        ParticleSetting setting = cache.getOrDefault(playerId, Map.of()).get(trigger);
        return setting == null || !setting.enabled() ? Optional.empty() : Optional.of(setting);
    }

    public Map<ParticleTrigger, ParticleSetting> settings(UUID playerId) {
        return cache.getOrDefault(playerId, Map.of());
    }

    public Set<String> allowedParticles() {
        return allowedParticles;
    }

    public Set<String> allowedFormations() {
        return allowedFormations;
    }

    public void setParticle(UUID playerId, ParticleTrigger trigger, String value) throws SQLException {
        requireEntitlement(playerId);
        ParticleSetting current = current(playerId, trigger);

        if (value.equalsIgnoreCase("off")) {
            persist(playerId, new ParticleSetting(
                    trigger,
                    current.particleKey(),
                    current.colorHex(),
                    current.formationKey(),
                    false
            ));
            refresh(playerId);
            return;
        }

        String particleKey = value.trim().toUpperCase(Locale.ROOT);
        if (!allowedParticles.contains(particleKey)) {
            throw new IllegalArgumentException("That particle is not available in the Garden customizer.");
        }
        try {
            Particle.valueOf(particleKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("That particle is unavailable on this Minecraft version.");
        }

        persist(playerId, new ParticleSetting(
                trigger,
                particleKey,
                current.colorHex(),
                current.formationKey(),
                true
        ));
        refresh(playerId);
    }

    public void setColor(UUID playerId, ParticleTrigger trigger, String value) throws SQLException {
        requireEntitlement(playerId);
        ParticleSetting current = current(playerId, trigger);

        String color = null;
        if (value != null && !value.equalsIgnoreCase("reset")) {
            color = value.trim().toUpperCase(Locale.ROOT);
            if (!color.startsWith("#")) color = "#" + color;
            if (!color.matches("#[0-9A-F]{6}")) {
                throw new IllegalArgumentException("Use a particle color such as #F2A7C3.");
            }
        }

        persist(playerId, new ParticleSetting(
                trigger,
                current.particleKey(),
                color,
                current.formationKey(),
                current.enabled()
        ));
        refresh(playerId);
    }

    public void setFormation(UUID playerId, ParticleTrigger trigger, String formation)
            throws SQLException {
        requireEntitlement(playerId);
        String normalized = formation.trim().toLowerCase(Locale.ROOT);
        if (!allowedFormations.contains(normalized)) {
            throw new IllegalArgumentException("That particle formation is not available.");
        }
        ParticleSetting current = current(playerId, trigger);
        persist(playerId, new ParticleSetting(
                trigger,
                current.particleKey(),
                current.colorHex(),
                normalized,
                current.enabled()
        ));
        refresh(playerId);
    }

    private ParticleSetting current(UUID playerId, ParticleTrigger trigger) {
        return cache.getOrDefault(playerId, Map.of())
                .getOrDefault(trigger, new ParticleSetting(
                        trigger, "HAPPY_VILLAGER", null, "trail", false));
    }

    private void requireEntitlement(UUID playerId) {
        if (!cosmetics.hasEntitlement(playerId, entitlementKey)) {
            throw new IllegalArgumentException(
                    "The custom particle editor is not unlocked on your account.");
        }
    }

    private void persist(UUID playerId, ParticleSetting setting) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection connection = platform.storage().connection()) {
            int changed;
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE gco_particle_settings "
                            + "SET particle_key = ?, color_hex = ?, formation_key = ?, enabled = ?, updated_at = ? "
                            + "WHERE player_uuid = ? AND trigger_key = ?")) {
                update.setString(1, setting.particleKey());
                if (setting.colorHex() == null) update.setNull(2, java.sql.Types.VARCHAR);
                else update.setString(2, setting.colorHex());
                update.setString(3, setting.formationKey());
                update.setBoolean(4, setting.enabled());
                update.setLong(5, now);
                update.setString(6, playerId.toString());
                update.setString(7, setting.trigger().key());
                changed = update.executeUpdate();
            }

            if (changed == 0) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO gco_particle_settings "
                                + "(player_uuid, trigger_key, particle_key, color_hex, formation_key, enabled, updated_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    insert.setString(1, playerId.toString());
                    insert.setString(2, setting.trigger().key());
                    insert.setString(3, setting.particleKey());
                    if (setting.colorHex() == null) insert.setNull(4, java.sql.Types.VARCHAR);
                    else insert.setString(4, setting.colorHex());
                    insert.setString(5, setting.formationKey());
                    insert.setBoolean(6, setting.enabled());
                    insert.setLong(7, now);
                    insert.executeUpdate();
                }
            }
        }
    }
}
