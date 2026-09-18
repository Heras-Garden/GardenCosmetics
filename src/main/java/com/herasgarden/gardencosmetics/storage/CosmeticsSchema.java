package com.herasgarden.gardencosmetics.storage;

import com.herasgarden.gardencore.api.storage.GardenStorage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class CosmeticsSchema {
    private CosmeticsSchema() {
    }

    public static void ensure(GardenStorage storage) throws SQLException {
        try (Connection connection = storage.connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gco_entitlements ("
                    + "player_uuid VARCHAR(36) NOT NULL,"
                    + "entitlement_key VARCHAR(96) NOT NULL,"
                    + "source_kind VARCHAR(24) NOT NULL,"
                    + "source_ref VARCHAR(128) NOT NULL,"
                    + "granted_at BIGINT NOT NULL,"
                    + "revoked_at BIGINT NULL,"
                    + "PRIMARY KEY (player_uuid, entitlement_key, source_kind, source_ref))");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_gco_entitlement_active "
                    + "ON gco_entitlements (player_uuid, entitlement_key, revoked_at)");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gco_preferences ("
                    + "player_uuid VARCHAR(36) PRIMARY KEY,"
                    + "donor_tag_enabled BOOLEAN NOT NULL DEFAULT TRUE,"
                    + "chat_hex VARCHAR(7) NULL,"
                    + "chat_font VARCHAR(96) NULL,"
                    + "updated_at BIGINT NOT NULL)");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gco_particle_settings ("
                    + "player_uuid VARCHAR(36) NOT NULL,"
                    + "trigger_key VARCHAR(24) NOT NULL,"
                    + "particle_key VARCHAR(64) NOT NULL,"
                    + "color_hex VARCHAR(7) NULL,"
                    + "formation_key VARCHAR(24) NOT NULL,"
                    + "enabled BOOLEAN NOT NULL DEFAULT FALSE,"
                    + "updated_at BIGINT NOT NULL,"
                    + "PRIMARY KEY (player_uuid, trigger_key))");
        }
    }
}
