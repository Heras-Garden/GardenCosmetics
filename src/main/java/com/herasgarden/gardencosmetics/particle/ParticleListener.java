package com.herasgarden.gardencosmetics.particle;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ParticleListener implements Listener {
    private final JavaPlugin plugin;
    private final ParticleService particles;
    private final Map<UUID, Long> lastMoveAt = new ConcurrentHashMap<>();
    private final Map<ThrottleKey, Long> lastEmitAt = new ConcurrentHashMap<>();
    private BukkitTask idleTask;

    public ParticleListener(JavaPlugin plugin, ParticleService particles) {
        this.plugin = plugin;
        this.particles = particles;
    }

    public void start() {
        if (idleTask != null) return;
        idleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::renderIdle, 40L, 40L);
    }

    public void stop() {
        if (idleTask != null) idleTask.cancel();
        idleTask = null;
        lastMoveAt.clear();
        lastEmitAt.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!moved(event.getFrom(), event.getTo())) return;

        Player player = event.getPlayer();
        lastMoveAt.put(player.getUniqueId(), System.currentTimeMillis());

        ParticleTrigger trigger;
        if (player.isFlying() || player.isGliding()) {
            trigger = ParticleTrigger.FLY;
        } else if (!player.isOnGround() && player.getVelocity().getY() < -0.08D) {
            trigger = ParticleTrigger.FALL;
        } else if (player.isOnGround()) {
            trigger = ParticleTrigger.WALK;
        } else {
            return;
        }

        emit(player, trigger, player.getLocation().add(0.0D, 0.15D, 0.0D));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        emit(player, ParticleTrigger.DAMAGE, player.getLocation().add(0.0D, 1.0D, 0.0D));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () ->
                emit(player, ParticleTrigger.TELEPORT, player.getLocation().add(0.0D, 0.8D, 0.0D)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastMoveAt.remove(playerId);
        lastEmitAt.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    private void renderIdle() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            long lastMove = lastMoveAt.getOrDefault(player.getUniqueId(), now);
            if (now - lastMove < 1500L) continue;
            emit(player, ParticleTrigger.IDLE, player.getLocation().add(0.0D, 0.2D, 0.0D));
        }
    }

    private void emit(Player player, ParticleTrigger trigger, Location base) {
        ParticleSetting setting = particles.setting(player.getUniqueId(), trigger).orElse(null);
        if (setting == null) return;

        long now = System.currentTimeMillis();
        ThrottleKey key = new ThrottleKey(player.getUniqueId(), trigger);
        long interval = switch (trigger) {
            case WALK, FLY, FALL -> 125L;
            case DAMAGE -> 250L;
            case TELEPORT -> 500L;
            case IDLE -> 1500L;
        };
        long previous = lastEmitAt.getOrDefault(key, 0L);
        if (now - previous < interval) return;
        lastEmitAt.put(key, now);

        Particle particle;
        try {
            particle = Particle.valueOf(setting.particleKey());
        } catch (IllegalArgumentException exception) {
            return;
        }

        switch (setting.formationKey().toLowerCase(java.util.Locale.ROOT)) {
            case "ring" -> ring(base, particle, setting, 0.55D, 0.2D);
            case "spiral" -> spiral(base, particle, setting);
            case "halo" -> ring(base, particle, setting, 0.55D, 1.75D);
            case "burst" -> burst(base, particle, setting);
            case "rain" -> rain(base, particle, setting);
            case "point", "trail" -> spawn(base, particle, setting);
            default -> spawn(base, particle, setting);
        }
    }

    private void ring(
            Location base,
            Particle particle,
            ParticleSetting setting,
            double radius,
            double yOffset
    ) {
        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2.0D * i) / 8.0D;
            spawn(base.clone().add(
                    Math.cos(angle) * radius,
                    yOffset,
                    Math.sin(angle) * radius
            ), particle, setting);
        }
    }

    private void spiral(Location base, Particle particle, ParticleSetting setting) {
        double phase = (System.currentTimeMillis() % 2000L) / 2000.0D * Math.PI * 2.0D;
        for (int i = 0; i < 5; i++) {
            double angle = phase + (Math.PI * 2.0D * i / 5.0D);
            spawn(base.clone().add(
                    Math.cos(angle) * 0.45D,
                    0.25D + i * 0.28D,
                    Math.sin(angle) * 0.45D
            ), particle, setting);
        }
    }

    private void burst(Location base, Particle particle, ParticleSetting setting) {
        for (int i = 0; i < 10; i++) {
            double angle = (Math.PI * 2.0D * i) / 10.0D;
            double radius = i % 2 == 0 ? 0.35D : 0.6D;
            spawn(base.clone().add(
                    Math.cos(angle) * radius,
                    (i % 3) * 0.22D,
                    Math.sin(angle) * radius
            ), particle, setting);
        }
    }

    private void rain(Location base, Particle particle, ParticleSetting setting) {
        for (int i = 0; i < 7; i++) {
            double angle = (Math.PI * 2.0D * i) / 7.0D;
            double radius = 0.25D + (i % 3) * 0.18D;
            spawn(base.clone().add(
                    Math.cos(angle) * radius,
                    1.25D + (i % 2) * 0.35D,
                    Math.sin(angle) * radius
            ), particle, setting);
        }
    }

    private void spawn(Location location, Particle particle, ParticleSetting setting) {
        World world = location.getWorld();
        if (world == null) return;

        if (particle.getDataType() == Particle.DustOptions.class) {
            Color color = color(setting.colorHex());
            Particle.DustOptions dust = new Particle.DustOptions(color, 1.0F);
            world.spawnParticle(particle, location, 1, 0.0D, 0.0D, 0.0D, 0.0D, dust);
            return;
        }

        if (particle.getDataType() == Void.class) {
            world.spawnParticle(particle, location, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private Color color(String hex) {
        if (hex != null) {
            String clean = hex.startsWith("#") ? hex.substring(1) : hex;
            try {
                if (clean.matches("[0-9A-Fa-f]{6}")) {
                    return Color.fromRGB(Integer.parseInt(clean, 16));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return Color.fromRGB(242, 167, 195);
    }

    private boolean moved(Location from, Location to) {
        if (to == null || from.getWorld() != to.getWorld()) return true;
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        return dx * dx + dy * dy + dz * dz > 0.0025D;
    }

    private record ThrottleKey(UUID playerId, ParticleTrigger trigger) {
    }
}
