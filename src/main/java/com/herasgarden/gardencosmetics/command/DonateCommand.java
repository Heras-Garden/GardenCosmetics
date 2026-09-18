package com.herasgarden.gardencosmetics.command;

import com.herasgarden.gardencore.api.ui.GardenMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class DonateCommand implements CommandExecutor {
    private final String storeUrl;

    public DonateCommand(String storeUrl) {
        this.storeUrl = storeUrl == null ? "" : storeUrl.trim();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (storeUrl.isBlank()) {
            GardenMessages.send(sender, "The Garden support store is still being configured.");
            return true;
        }

        sender.sendMessage(
                GardenMessages.prefix()
                        .append(Component.text("Support The Garden SMP", NamedTextColor.WHITE))
                        .append(Component.newline())
                        .append(Component.text(
                                "Purchases are cosmetic/supporter-only and do not provide gameplay advantages.",
                                NamedTextColor.GRAY))
                        .append(Component.newline())
                        .append(Component.text("[Open the Garden Store]", NamedTextColor.LIGHT_PURPLE)
                                .clickEvent(ClickEvent.openUrl(storeUrl))
                                .hoverEvent(HoverEvent.showText(
                                        Component.text(storeUrl, NamedTextColor.GRAY))))
        );
        return true;
    }
}
