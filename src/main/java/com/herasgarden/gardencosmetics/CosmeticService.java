package com.herasgarden.gardencosmetics;

import com.herasgarden.gardencore.api.GardenPlatform;
import com.herasgarden.gardencore.api.cosmetic.CosmeticChatProfile;
import com.herasgarden.gardencore.api.cosmetic.CosmeticProfileService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CosmeticService implements CosmeticProfileService {
    private final GardenPlatform platform;
    private final String donorKey;
    private final String chatHexKey;
    private final String chatFontKey;
    private final Set<String> allowedFonts;
    private final Map<UUID, Set<String>> entitlements = new ConcurrentHashMap<>();
    private final Map<UUID, CosmeticChatProfile> chatProfiles = new ConcurrentHashMap<>();

    public CosmeticService(
            GardenPlatform platform,
            String donorKey,
            String chatHexKey,
            String chatFontKey,
            Set<String> allowedFonts
    ) {
        this.platform = platform;
        this.donorKey = donorKey;
        this.chatHexKey = chatHexKey;
        this.chatFontKey = chatFontKey;
        this.allowedFonts = Set.copyOf(allowedFonts);
    }

    public void refreshAll() throws SQLException {
        Map<UUID, Set<String>> loadedEntitlements = new HashMap<>();
        try (Connection connection = platform.storage().connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid, entitlement_key FROM gco_entitlements WHERE revoked_at IS NULL");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                UUID playerId = UUID.fromString(result.getString("player_uuid"));
                loadedEntitlements.computeIfAbsent(playerId, ignored -> new HashSet<>())
                        .add(result.getString("entitlement_key"));
            }
        }

        Map<UUID, Preference> preferences = new HashMap<>();
        try (Connection connection = platform.storage().connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid, donor_tag_enabled, chat_hex, chat_font FROM gco_preferences");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                preferences.put(
                        UUID.fromString(result.getString("player_uuid")),
                        new Preference(
                                result.getBoolean("donor_tag_enabled"),
                                result.getString("chat_hex"),
                                result.getString("chat_font")
                        )
                );
            }
        }

        entitlements.clear();
        chatProfiles.clear();
        loadedEntitlements.forEach((playerId, keys) ->
                entitlements.put(playerId, Set.copyOf(keys)));

        Set<UUID> players = new HashSet<>(loadedEntitlements.keySet());
        players.addAll(preferences.keySet());
        for (UUID playerId : players) {
            chatProfiles.put(playerId, buildProfile(
                    loadedEntitlements.getOrDefault(playerId, Set.of()),
                    preferences.getOrDefault(playerId, Preference.DEFAULT)
            ));
        }
    }

    public void refresh(UUID playerId) throws SQLException {
        Set<String> keys = new HashSet<>();
        try (Connection connection = platform.storage().connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT entitlement_key FROM gco_entitlements "
                             + "WHERE player_uuid = ? AND revoked_at IS NULL")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) keys.add(result.getString("entitlement_key"));
            }
        }

        Preference preference = Preference.DEFAULT;
        try (Connection connection = platform.storage().connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT donor_tag_enabled, chat_hex, chat_font "
                             + "FROM gco_preferences WHERE player_uuid = ? LIMIT 1")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    preference = new Preference(
                            result.getBoolean("donor_tag_enabled"),
                            result.getString("chat_hex"),
                            result.getString("chat_font")
                    );
                }
            }
        }

        if (keys.isEmpty()) entitlements.remove(playerId);
        else entitlements.put(playerId, Set.copyOf(keys));
        chatProfiles.put(playerId, buildProfile(keys, preference));
    }

    public void grant(UUID playerId, String entitlementKey, String sourceRef) throws SQLException {
        String key = cleanEntitlement(entitlementKey);
        String reference = cleanSourceRef(sourceRef);
        long now = System.currentTimeMillis();

        try (Connection connection = platform.storage().connection()) {
            int changed;
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE gco_entitlements SET revoked_at = NULL, granted_at = ? "
                            + "WHERE player_uuid = ? AND entitlement_key = ? "
                            + "AND source_kind = 'TEBEX' AND source_ref = ?")) {
                update.setLong(1, now);
                update.setString(2, playerId.toString());
                update.setString(3, key);
                update.setString(4, reference);
                changed = update.executeUpdate();
            }
            if (changed == 0) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO gco_entitlements "
                                + "(player_uuid, entitlement_key, source_kind, source_ref, granted_at, revoked_at) "
                                + "VALUES (?, ?, 'TEBEX', ?, ?, NULL)")) {
                    insert.setString(1, playerId.toString());
                    insert.setString(2, key);
                    insert.setString(3, reference);
                    insert.setLong(4, now);
                    insert.executeUpdate();
                }
            }
        }
        refresh(playerId);
    }

    public void revoke(UUID playerId, String entitlementKey, String sourceRef) throws SQLException {
        String key = cleanEntitlement(entitlementKey);
        String reference = cleanSourceRef(sourceRef);
        try (Connection connection = platform.storage().connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE gco_entitlements SET revoked_at = ? "
                             + "WHERE player_uuid = ? AND entitlement_key = ? "
                             + "AND source_kind = 'TEBEX' AND source_ref = ? AND revoked_at IS NULL")) {
            statement.setLong(1, System.currentTimeMillis());
            statement.setString(2, playerId.toString());
            statement.setString(3, key);
            statement.setString(4, reference);
            statement.executeUpdate();
        }
        refresh(playerId);
    }

    public void setDonorTag(UUID playerId, boolean enabled) throws SQLException {
        if (!hasEntitlement(playerId, donorKey)) {
            throw new IllegalArgumentException("You do not have the Donor cosmetic.");
        }
        updatePreference(playerId, "donor_tag_enabled", enabled ? 1 : 0);
        refresh(playerId);
    }

    public void setChatHex(UUID playerId, String value) throws SQLException {
        if (value == null) {
            updatePreference(playerId, "chat_hex", null);
            refresh(playerId);
            return;
        }
        if (!hasEntitlement(playerId, chatHexKey)) {
            throw new IllegalArgumentException("Hex chat color is not unlocked on your account.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("#")) normalized = "#" + normalized;
        if (!normalized.matches("#[0-9A-F]{6}")) {
            throw new IllegalArgumentException("Use a hex color such as #D98FB3.");
        }
        updatePreference(playerId, "chat_hex", normalized);
        refresh(playerId);
    }

    public void setChatFont(UUID playerId, String font) throws SQLException {
        if (font == null) {
            updatePreference(playerId, "chat_font", null);
            refresh(playerId);
            return;
        }
        if (!hasEntitlement(playerId, chatFontKey)) {
            throw new IllegalArgumentException("Chat fonts are not unlocked on your account.");
        }
        String normalized = font.trim().toLowerCase(Locale.ROOT);
        if (!allowedFonts.contains(normalized)) {
            throw new IllegalArgumentException("That Garden chat font is not available.");
        }
        updatePreference(playerId, "chat_font", normalized);
        refresh(playerId);
    }

    public Optional<UUID> resolveKnownPlayer(String input) throws SQLException {
        String token = input == null ? "" : input.trim();
        if (token.isBlank()) return Optional.empty();

        try {
            return Optional.of(UUID.fromString(token));
        } catch (IllegalArgumentException ignored) {
        }

        Player online = Bukkit.getPlayerExact(token);
        if (online != null) return Optional.of(online.getUniqueId());

        try (Connection connection = platform.storage().connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid FROM gm_player_directory "
                             + "WHERE LOWER(last_name) = LOWER(?) ORDER BY last_seen_at DESC LIMIT 1")) {
            statement.setString(1, token);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) return Optional.of(UUID.fromString(result.getString("player_uuid")));
            }
        } catch (SQLException exception) {
            if (!isMissingTable(exception)) throw exception;
        }

        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() != null && player.getName().equalsIgnoreCase(token)) {
                return Optional.of(player.getUniqueId());
            }
        }
        return Optional.empty();
    }

    public String displayName(UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) return online.getName();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        return offline.getName() == null
                ? playerId.toString().substring(0, 8)
                : offline.getName();
    }

    @Override
    public CosmeticChatProfile chatProfile(UUID playerId) {
        return chatProfiles.getOrDefault(playerId, CosmeticChatProfile.empty());
    }

    @Override
    public boolean hasEntitlement(UUID playerId, String entitlementKey) {
        return entitlements.getOrDefault(playerId, Set.of()).contains(entitlementKey);
    }

    public List<String> activeEntitlements(UUID playerId) {
        List<String> values = new ArrayList<>(
                entitlements.getOrDefault(playerId, Set.of()));
        values.sort(String::compareToIgnoreCase);
        return List.copyOf(values);
    }

    private CosmeticChatProfile buildProfile(Set<String> keys, Preference preference) {
        boolean donor = keys.contains(donorKey) && preference.donorTagEnabled();
        String hex = keys.contains(chatHexKey) ? preference.chatHex() : null;
        String font = keys.contains(chatFontKey) ? preference.chatFont() : null;
        return new CosmeticChatProfile(donor, hex, font);
    }

    private void updatePreference(UUID playerId, String column, Object value) throws SQLException {
        if (!Set.of("donor_tag_enabled", "chat_hex", "chat_font").contains(column)) {
            throw new IllegalArgumentException("Unsupported cosmetic preference.");
        }
        long now = System.currentTimeMillis();
        try (Connection connection = platform.storage().connection()) {
            int changed;
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE gco_preferences SET " + column + " = ?, updated_at = ? WHERE player_uuid = ?")) {
                if (value == null) update.setNull(1, java.sql.Types.VARCHAR);
                else if (value instanceof Integer number) update.setInt(1, number);
                else update.setString(1, String.valueOf(value));
                update.setLong(2, now);
                update.setString(3, playerId.toString());
                changed = update.executeUpdate();
            }
            if (changed == 0) {
                boolean donorEnabled = !"donor_tag_enabled".equals(column) || Integer.valueOf(1).equals(value);
                String chatHex = "chat_hex".equals(column) ? (String) value : null;
                String chatFont = "chat_font".equals(column) ? (String) value : null;
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO gco_preferences "
                                + "(player_uuid, donor_tag_enabled, chat_hex, chat_font, updated_at) "
                                + "VALUES (?, ?, ?, ?, ?)")) {
                    insert.setString(1, playerId.toString());
                    insert.setBoolean(2, donorEnabled);
                    if (chatHex == null) insert.setNull(3, java.sql.Types.VARCHAR);
                    else insert.setString(3, chatHex);
                    if (chatFont == null) insert.setNull(4, java.sql.Types.VARCHAR);
                    else insert.setString(4, chatFont);
                    insert.setLong(5, now);
                    insert.executeUpdate();
                }
            }
        }
    }

    private String cleanEntitlement(String value) {
        String clean = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!clean.matches("[a-z0-9._-]{1,96}")) {
            throw new IllegalArgumentException("Invalid entitlement key.");
        }
        return clean;
    }

    private String cleanSourceRef(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isBlank() || clean.length() > 128) {
            throw new IllegalArgumentException("Invalid Tebex transaction reference.");
        }
        return clean;
    }

    private boolean isMissingTable(SQLException exception) {
        String message = exception.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("no such table");
    }

    private record Preference(boolean donorTagEnabled, String chatHex, String chatFont) {
        private static final Preference DEFAULT = new Preference(true, null, null);
    }
}
