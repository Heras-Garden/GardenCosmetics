package com.herasgarden.gardencosmetics;

import com.herasgarden.gardencore.api.GardenPlatform;
import com.herasgarden.gardencore.api.cosmetic.CosmeticProfileService;
import com.herasgarden.gardencosmetics.command.CosmeticsCommand;
import com.herasgarden.gardencosmetics.command.DonateCommand;
import com.herasgarden.gardencosmetics.particle.ParticleListener;
import com.herasgarden.gardencosmetics.particle.ParticleService;
import com.herasgarden.gardencosmetics.storage.CosmeticsSchema;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class GardenCosmetics extends JavaPlugin {
    private CosmeticService cosmetics;
    private ParticleListener particleListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        RegisteredServiceProvider<GardenPlatform> registration =
                getServer().getServicesManager().getRegistration(GardenPlatform.class);
        if (registration == null || registration.getProvider() == null) {
            getLogger().severe("GardenCore platform service is unavailable.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        GardenPlatform platform = registration.getProvider();

        try {
            CosmeticsSchema.ensure(platform.storage());

            String donorKey = getConfig().getString("entitlements.donor", "support.donor")
                    .trim().toLowerCase(Locale.ROOT);
            String chatHexKey = getConfig().getString("entitlements.chat-hex", "chat.hex")
                    .trim().toLowerCase(Locale.ROOT);
            String chatFontKey = getConfig().getString("entitlements.chat-font", "chat.font")
                    .trim().toLowerCase(Locale.ROOT);
            String particleKey = getConfig().getString("entitlements.particles-custom", "particles.custom")
                    .trim().toLowerCase(Locale.ROOT);
            Set<String> fonts = getConfig().getStringList("fonts.allowed").stream()
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toUnmodifiableSet());

            cosmetics = new CosmeticService(
                    platform, donorKey, chatHexKey, chatFontKey, fonts);
            cosmetics.refreshAll();

            Set<String> allowedParticles = getConfig().getStringList("particles.allowed").stream()
                    .map(value -> value.trim().toUpperCase(Locale.ROOT))
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toUnmodifiableSet());
            Set<String> allowedFormations = getConfig().getStringList("particles.formations").stream()
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toUnmodifiableSet());
            ParticleService particles = new ParticleService(
                    platform, cosmetics, particleKey, allowedParticles, allowedFormations);
            particles.refreshAll();

            getServer().getServicesManager().register(
                    CosmeticProfileService.class, cosmetics, this, ServicePriority.Normal);

            CosmeticsCommand command = new CosmeticsCommand(
                    cosmetics, particles, fonts, donorKey, chatHexKey, chatFontKey, particleKey);
            PluginCommand cosmeticsCommand = getCommand("cosmetics");
            if (cosmeticsCommand != null) {
                cosmeticsCommand.setExecutor(command);
                cosmeticsCommand.setTabCompleter(command);
            }

            PluginCommand donate = getCommand("donate");
            if (donate != null) {
                donate.setExecutor(new DonateCommand(
                        getConfig().getString("tebex.store-url", "")));
            }

            particleListener = new ParticleListener(this, particles);
            getServer().getPluginManager().registerEvents(particleListener, this);
            particleListener.start();

            if (getConfig().getString("tebex.store-url", "").isBlank()) {
                getLogger().warning(
                        "Tebex store URL is not configured yet; /donate will show a setup message.");
            }
        } catch (SQLException | RuntimeException exception) {
            getLogger().severe("GardenCosmetics could not start: " + exception.getMessage());
            exception.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info(
                "GardenCosmetics enabled. Tebex entitlements, chat cosmetics, and particle profiles are active.");
    }

    @Override
    public void onDisable() {
        if (particleListener != null) {
            particleListener.stop();
        }
        getServer().getServicesManager().unregisterAll(this);
    }
}
