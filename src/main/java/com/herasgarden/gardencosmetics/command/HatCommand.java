package com.herasgarden.gardencosmetics.command;

import com.herasgarden.gardencore.api.ui.GardenMessages;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class HatCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            GardenMessages.send(sender, "This command must be used in-game.");
            return true;
        }
        if (!player.hasPermission("gardencosmetics.hat")) {
            GardenMessages.send(player, "You do not have permission to use /hat.");
            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("off"))) {
            return remove(player);
        }
        if (args.length > 0) {
            GardenMessages.send(player, "Use /hat or /hat remove.");
            return true;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            GardenMessages.send(player, "Hold the item you want to wear in your main hand.");
            return true;
        }

        ItemStack previous = player.getInventory().getHelmet();
        if (bindingLocked(player, previous)) {
            GardenMessages.send(player, "Your current helmet has Curse of Binding and cannot be replaced.");
            return true;
        }

        player.getInventory().setHelmet(hand.clone());
        player.getInventory().setItemInMainHand(previous == null
                ? new ItemStack(Material.AIR)
                : previous);
        GardenMessages.send(player, "Hat equipped.");
        return true;
    }

    private boolean remove(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null || helmet.getType().isAir()) {
            GardenMessages.send(player, "You are not wearing a hat.");
            return true;
        }
        if (bindingLocked(player, helmet)) {
            GardenMessages.send(player, "That helmet has Curse of Binding and cannot be removed.");
            return true;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        player.getInventory().setHelmet(null);
        if (hand == null || hand.getType().isAir()) {
            player.getInventory().setItemInMainHand(helmet);
        } else {
            player.getInventory().addItem(helmet).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        GardenMessages.send(player, "Hat removed.");
        return true;
    }

    private boolean bindingLocked(Player player, ItemStack item) {
        return item != null
                && item.containsEnchantment(Enchantment.BINDING_CURSE)
                && !player.hasPermission("gardencosmetics.hat.bypass-binding");
    }
}
