package com.poweritem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PowerItemCommand implements TabExecutor {

    private final PowerItem plugin;

    public PowerItemCommand(PowerItem plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            error(player, "Hold the item you want to change in your main hand.");
            return true;
        }

        if (args.length == 0) {
            usage(player, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "damage" -> {
                if (args.length < 2) {
                    error(player, "Usage: /" + label + " damage <amount>   (2 = one heart)");
                    return true;
                }
                double amount;
                try {
                    amount = Double.parseDouble(args[1]);
                } catch (NumberFormatException e) {
                    error(player, "'" + args[1] + "' isn't a number.");
                    return true;
                }
                if (amount < 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
                    error(player, "Damage has to be 0 or more.");
                    return true;
                }
                item.editMeta(meta -> {
                    stampId(meta);
                    meta.getPersistentDataContainer().set(plugin.damageKey(), PersistentDataType.DOUBLE, amount);
                });
                player.getInventory().setItemInMainHand(item);
                ok(player, "This item now does +" + amount + " extra damage (" + (amount / 2) + " hearts).");
            }
            case "mode" -> {
                if (args.length < 2) {
                    error(player, "Usage: /" + label + " mode <normal|kill|pop>");
                    return true;
                }
                Mode mode;
                try {
                    mode = Mode.valueOf(args[1].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    error(player, "Mode must be normal, kill, or pop.");
                    return true;
                }
                item.editMeta(meta -> {
                    stampId(meta);
                    meta.getPersistentDataContainer().set(plugin.modeKey(), PersistentDataType.STRING, mode.name());
                });
                player.getInventory().setItemInMainHand(item);
                String explain = switch (mode) {
                    case NORMAL -> "normal hits.";
                    case KILL -> "every hit kills, totems and armor don't matter.";
                    case POP -> "every hit pops their totem (or kills them if they don't have one).";
                };
                ok(player, "Mode set to " + mode.name().toLowerCase(Locale.ROOT) + ": " + explain);
            }
            case "clear" -> {
                item.editMeta(meta -> {
                    PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    pdc.remove(plugin.idKey());
                    pdc.remove(plugin.damageKey());
                    pdc.remove(plugin.modeKey());
                });
                player.getInventory().setItemInMainHand(item);
                ok(player, "Removed all powers from this item.");
            }
            case "info" -> {
                ItemMeta meta = item.getItemMeta();
                PersistentDataContainer pdc = meta == null ? null : meta.getPersistentDataContainer();
                if (pdc == null || !pdc.has(plugin.idKey(), PersistentDataType.STRING)) {
                    error(player, "This item has no powers.");
                    return true;
                }
                double bonus = pdc.getOrDefault(plugin.damageKey(), PersistentDataType.DOUBLE, 0.0);
                Mode mode = Mode.from(pdc.get(plugin.modeKey(), PersistentDataType.STRING));
                ok(player, "Mode: " + mode.name().toLowerCase(Locale.ROOT) + " | Bonus damage: +" + bonus);
            }
            default -> usage(player, label);
        }
        return true;
    }

    // Gives the item a unique ID so it's its own item and won't stack with normal copies.
    private void stampId(ItemMeta meta) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(plugin.idKey(), PersistentDataType.STRING)) {
            pdc.set(plugin.idKey(), PersistentDataType.STRING, UUID.randomUUID().toString());
        }
    }

    private void usage(Player player, String label) {
        player.sendMessage(Component.text("PowerItem commands:", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/" + label + " damage <amount>", NamedTextColor.YELLOW)
                .append(Component.text(" - extra damage (2 = one heart)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/" + label + " mode <normal|kill|pop>", NamedTextColor.YELLOW)
                .append(Component.text(" - kill or totem-pop on hit", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/" + label + " info", NamedTextColor.YELLOW)
                .append(Component.text(" - see this item's powers", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/" + label + " clear", NamedTextColor.YELLOW)
                .append(Component.text(" - remove all powers", NamedTextColor.GRAY)));
    }

    private void ok(Player player, String msg) {
        player.sendMessage(Component.text(msg, NamedTextColor.GREEN));
    }

    private void error(Player player, String msg) {
        player.sendMessage(Component.text(msg, NamedTextColor.RED));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("damage", "mode", "info", "clear"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            options.addAll(List.of("normal", "kill", "pop"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("damage")) {
            options.addAll(List.of("2", "5", "10", "20"));
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(o -> !o.startsWith(typed));
        return options;
    }
}
