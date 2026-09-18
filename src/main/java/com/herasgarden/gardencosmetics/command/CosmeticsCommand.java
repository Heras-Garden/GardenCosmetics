package com.herasgarden.gardencosmetics.command;

import com.herasgarden.gardencore.api.cosmetic.CosmeticChatProfile;
import com.herasgarden.gardencore.api.ui.GardenMessages;
import com.herasgarden.gardencosmetics.CosmeticService;
import com.herasgarden.gardencosmetics.particle.ParticleService;
import com.herasgarden.gardencosmetics.particle.ParticleSetting;
import com.herasgarden.gardencosmetics.particle.ParticleTrigger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CosmeticsCommand implements CommandExecutor, TabCompleter {
    private final CosmeticService cosmetics;
    private final ParticleService particles;
    private final Set<String> fonts;
    private final List<String> commonEntitlements;

    public CosmeticsCommand(
            CosmeticService cosmetics,
            ParticleService particles,
            Set<String> fonts,
            String donorKey,
            String chatHexKey,
            String chatFontKey,
            String particleKey
    ) {
        this.cosmetics = cosmetics;
        this.particles = particles;
        this.fonts = Set.copyOf(fonts);
        this.commonEntitlements = List.of(donorKey, chatHexKey, chatFontKey, particleKey);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
                return status(sender);
            }
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "donor" -> donor(sender, args);
                case "color" -> color(sender, args);
                case "font" -> font(sender, args);
                case "particles" -> particles(sender, args);
                case "grant" -> grant(sender, args);
                case "revoke" -> revoke(sender, args);
                default -> {
                    help(sender);
                    yield true;
                }
            };
        } catch (IllegalArgumentException exception) {
            GardenMessages.send(sender, exception.getMessage());
        } catch (SQLException exception) {
            GardenMessages.send(sender, "Cosmetics could not be updated right now.");
        }
        return true;
    }

    private boolean status(CommandSender sender) {
        Player player = player(sender);
        if (player == null) return true;

        CosmeticChatProfile profile = cosmetics.chatProfile(player.getUniqueId());
        List<String> active = cosmetics.activeEntitlements(player.getUniqueId());
        GardenMessages.send(player, active.isEmpty()
                ? "You do not have any Garden cosmetics unlocked yet."
                : "Unlocked: " + String.join(", ", active) + ".");
        GardenMessages.send(player, "Donor tag: "
                + (profile.donorTagEnabled() ? "on" : "off")
                + " | Name color: " + (profile.nameHex() == null ? "default" : profile.nameHex())
                + " | Font: " + (profile.fontKey() == null ? "default" : profile.fontKey()) + ".");
        return true;
    }

    private boolean donor(CommandSender sender, String[] args) throws SQLException {
        Player player = player(sender);
        if (player == null) return true;
        if (args.length < 2
                || (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))) {
            GardenMessages.send(player, "Use /cosmetics donor <on|off>.");
            return true;
        }
        boolean enabled = args[1].equalsIgnoreCase("on");
        cosmetics.setDonorTag(player.getUniqueId(), enabled);
        GardenMessages.send(player, "Donor tag " + (enabled ? "enabled." : "hidden."));
        return true;
    }

    private boolean color(CommandSender sender, String[] args) throws SQLException {
        Player player = player(sender);
        if (player == null) return true;
        if (args.length < 2) {
            GardenMessages.send(player, "Use /cosmetics color <#RRGGBB|reset>.");
            return true;
        }
        if (args[1].equalsIgnoreCase("reset")) {
            cosmetics.setChatHex(player.getUniqueId(), null);
            GardenMessages.send(player, "Chat name color reset.");
            return true;
        }
        cosmetics.setChatHex(player.getUniqueId(), args[1]);
        GardenMessages.send(player, "Chat name color set to " + args[1].toUpperCase(Locale.ROOT) + ".");
        return true;
    }

    private boolean font(CommandSender sender, String[] args) throws SQLException {
        Player player = player(sender);
        if (player == null) return true;
        if (args.length < 2) {
            GardenMessages.send(player, "Use /cosmetics font <font|reset>.");
            return true;
        }
        if (args[1].equalsIgnoreCase("reset")) {
            cosmetics.setChatFont(player.getUniqueId(), null);
            GardenMessages.send(player, "Chat font reset.");
            return true;
        }
        cosmetics.setChatFont(player.getUniqueId(), args[1]);
        GardenMessages.send(player, "Chat font set to " + args[1].toLowerCase(Locale.ROOT) + ".");
        return true;
    }

    private boolean particles(CommandSender sender, String[] args) throws SQLException {
        Player player = player(sender);
        if (player == null) return true;

        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            particleStatus(player);
            return true;
        }

        ParticleTrigger trigger = ParticleTrigger.parse(args[1]);
        if (args.length < 3) {
            ParticleSetting setting = particles.settings(player.getUniqueId()).get(trigger);
            GardenMessages.send(player, setting == null
                    ? trigger.key() + ": off."
                    : trigger.key() + ": " + (setting.enabled() ? setting.particleKey() : "off")
                    + " | formation " + setting.formationKey()
                    + " | color " + (setting.colorHex() == null ? "default" : setting.colorHex()) + ".");
            return true;
        }

        if (args[2].equalsIgnoreCase("color")) {
            if (args.length < 4) {
                GardenMessages.send(player,
                        "Use /cosmetics particles " + trigger.key() + " color <#RRGGBB|reset>.");
                return true;
            }
            particles.setColor(player.getUniqueId(), trigger, args[3]);
            GardenMessages.send(player, trigger.key() + " particle color updated.");
            return true;
        }

        if (args[2].equalsIgnoreCase("formation")) {
            if (args.length < 4) {
                GardenMessages.send(player,
                        "Use /cosmetics particles " + trigger.key() + " formation <name>.");
                return true;
            }
            particles.setFormation(player.getUniqueId(), trigger, args[3]);
            GardenMessages.send(player,
                    trigger.key() + " formation set to " + args[3].toLowerCase(Locale.ROOT) + ".");
            return true;
        }

        particles.setParticle(player.getUniqueId(), trigger, args[2]);
        GardenMessages.send(player, trigger.key() + " particle "
                + (args[2].equalsIgnoreCase("off")
                ? "disabled."
                : "set to " + args[2].toUpperCase(Locale.ROOT) + "."));
        return true;
    }

    private void particleStatus(Player player) {
        Map<ParticleTrigger, ParticleSetting> values = particles.settings(player.getUniqueId());
        if (values.isEmpty()) {
            GardenMessages.send(player, "No particle triggers are configured.");
            return;
        }
        for (ParticleTrigger trigger : ParticleTrigger.values()) {
            ParticleSetting setting = values.get(trigger);
            if (setting == null) continue;
            GardenMessages.send(player,
                    trigger.key() + ": " + (setting.enabled() ? setting.particleKey() : "off")
                            + " | " + setting.formationKey()
                            + " | " + (setting.colorHex() == null ? "default color" : setting.colorHex()) + ".");
        }
    }

    private boolean grant(CommandSender sender, String[] args) throws SQLException {
        if (!sender.hasPermission("gardencosmetics.admin")) {
            GardenMessages.send(sender, "You do not have permission to grant cosmetics.");
            return true;
        }
        if (args.length < 4) {
            GardenMessages.send(sender,
                    "Use /cosmetics grant <player> <entitlement> <transaction-ref>.");
            return true;
        }

        UUID playerId = cosmetics.resolveKnownPlayer(args[1])
                .orElseThrow(() -> new IllegalArgumentException(
                        "That player is not known yet. They need to join the Garden SMP first."));
        cosmetics.grant(playerId, args[2], args[3]);
        GardenMessages.send(sender, "Granted " + args[2] + " to "
                + cosmetics.displayName(playerId) + " from transaction " + args[3] + ".");
        return true;
    }

    private boolean revoke(CommandSender sender, String[] args) throws SQLException {
        if (!sender.hasPermission("gardencosmetics.admin")) {
            GardenMessages.send(sender, "You do not have permission to revoke cosmetics.");
            return true;
        }
        if (args.length < 4) {
            GardenMessages.send(sender,
                    "Use /cosmetics revoke <player> <entitlement> <transaction-ref>.");
            return true;
        }

        UUID playerId = cosmetics.resolveKnownPlayer(args[1])
                .orElseThrow(() -> new IllegalArgumentException(
                        "That player is not known to the Garden SMP."));
        cosmetics.revoke(playerId, args[2], args[3]);
        GardenMessages.send(sender, "Revoked " + args[2] + " from "
                + cosmetics.displayName(playerId) + " for transaction " + args[3] + ".");
        return true;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) return player;
        GardenMessages.send(sender, "That cosmetic setting must be changed in-game.");
        return null;
    }

    private void help(CommandSender sender) {
        GardenMessages.send(sender,
                "/cosmetics <status|donor <on|off>|color <#RRGGBB|reset>|font <font|reset>|particles <...>>.");
        if (sender.hasPermission("gardencosmetics.admin")) {
            GardenMessages.send(sender,
                    "Admin/Tebex: /cosmetics <grant|revoke> <player> <entitlement> <transaction-ref>.");
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(
                    List.of("status", "donor", "color", "font", "particles"));
            if (sender.hasPermission("gardencosmetics.admin")) {
                values.add("grant");
                values.add("revoke");
            }
            return match(args[0], values);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("particles")) {
            List<String> values = new ArrayList<>(List.of("status"));
            for (ParticleTrigger trigger : ParticleTrigger.values()) values.add(trigger.key());
            return match(args[1], values);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("particles")
                && !args[1].equalsIgnoreCase("status")) {
            List<String> values = new ArrayList<>();
            values.add("off");
            values.add("color");
            values.add("formation");
            particles.allowedParticles().stream()
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .forEach(values::add);
            return match(args[2], values);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("particles")
                && args[2].equalsIgnoreCase("color")) {
            return match(args[3], List.of("reset"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("particles")
                && args[2].equalsIgnoreCase("formation")) {
            return match(args[3], new ArrayList<>(particles.allowedFormations()));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("donor")) {
            return match(args[1], List.of("on", "off"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("font")) {
            List<String> values = new ArrayList<>(fonts);
            values.add("reset");
            return match(args[1], values);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("color")) {
            return match(args[1], List.of("reset"));
        }
        if (args.length == 3
                && (args[0].equalsIgnoreCase("grant") || args[0].equalsIgnoreCase("revoke"))) {
            return match(args[2], commonEntitlements);
        }
        return List.of();
    }

    private List<String> match(String prefix, List<String> values) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted()
                .toList();
    }
}
