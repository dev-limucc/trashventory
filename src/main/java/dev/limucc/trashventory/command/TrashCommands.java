package dev.limucc.trashventory.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.limucc.trashventory.config.PlayerSettings;
import dev.limucc.trashventory.config.PlayerSettingsStore;
import dev.limucc.trashventory.config.TrashConfig;
import dev.limucc.trashventory.config.TrashConfigManager;
import dev.limucc.trashventory.trash.TrashManager;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/**
 * /trash — open the bin (any player), plus per-player settings sub-commands so players can fully
 * customise without the GUI (works on vanilla clients too). /trash admin ... is op-only.
 * Every player change writes the server-authoritative store and pushes the new state back to the client,
 * so commands and the GUI always agree.
 */
public final class TrashCommands {

    private TrashCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext build) {
        LiteralCommandNode<CommandSourceStack> trash = dispatcher.register(
                Commands.literal("trash")
                        .executes(ctx -> {
                            TrashManager.open(ctx.getSource().getPlayerOrException());
                            return 1;
                        })
                        // ── player settings ───────────────────────────────────────────
                        .then(Commands.literal("timer")
                                .then(Commands.literal("default").executes(ctx -> setTimer(ctx, -1)))
                                .then(Commands.literal("off").executes(ctx -> setTimer(ctx, 0)))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 86400))
                                        .executes(ctx -> setTimer(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))))
                        .then(Commands.literal("message")
                                .then(Commands.literal("on").executes(ctx -> setMessage(ctx, true)))
                                .then(Commands.literal("off").executes(ctx -> setMessage(ctx, false))))
                        .then(Commands.literal("blacklist")
                                .then(Commands.literal("add").then(Commands.argument("item", ItemArgument.item(build))
                                        .executes(TrashCommands::blacklistAdd)))
                                .then(Commands.literal("remove").then(Commands.argument("item", ItemArgument.item(build))
                                        .executes(TrashCommands::blacklistRemove)))
                                .then(Commands.literal("clear").executes(TrashCommands::blacklistClear))
                                .then(Commands.literal("list").executes(TrashCommands::blacklistList)))
                        .then(Commands.literal("settings").executes(TrashCommands::showSettings))
                        // ── admin (op / gamemaster level) ──────────────────────────────
                        .then(Commands.literal("admin")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.literal("size")
                                        .then(Commands.literal("single").executes(ctx -> setDouble(ctx, false)))
                                        .then(Commands.literal("double").executes(ctx -> setDouble(ctx, true))))
                                .then(Commands.literal("timer")
                                        .then(Commands.literal("off").executes(TrashCommands::adminTimerOff))
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 86400))
                                                .executes(TrashCommands::adminTimerSeconds)))
                                .then(Commands.literal("message")
                                        .then(Commands.literal("on").executes(ctx -> adminMessage(ctx, true)))
                                        .then(Commands.literal("off").executes(ctx -> adminMessage(ctx, false))))
                                .then(Commands.literal("clientoverride")
                                        .then(Commands.literal("on").executes(ctx -> adminOverride(ctx, true)))
                                        .then(Commands.literal("off").executes(ctx -> adminOverride(ctx, false))))
                                .then(Commands.literal("reload").executes(TrashCommands::adminReload))
                                .then(Commands.literal("clear")
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .executes(TrashCommands::adminClear)))));

        dispatcher.register(Commands.literal("tr").redirect(trash));
    }

    // ── player handlers ──────────────────────────────────────────────────────────
    private static int setTimer(CommandContext<CommandSourceStack> ctx, int value) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PlayerSettingsStore.setTimer(p.getUUID(), value);
        TrashManager.sendSettings(p);
        int server = TrashConfigManager.get().removeDelaySeconds;
        String msg = value < 0 ? "Your trash timer now follows the server default (" + server + "s)."
                : value == 0 ? "Your trash timer is OFF — items are deleted instantly (no message)."
                : "Your trash timer set to " + Math.min(value, server) + "s"
                        + (value > server ? " (clamped to the server's " + server + "s max)." : ".");
        return personal(ctx, msg);
    }

    private static int setMessage(CommandContext<CommandSourceStack> ctx, boolean on) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PlayerSettingsStore.setMessage(p.getUUID(), on);
        TrashManager.sendSettings(p);
        return personal(ctx, "Your trash close-message is now " + (on ? "ON" : "OFF") + ".");
    }

    private static int blacklistAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Item item = ItemArgument.getItem(ctx, "item").item().value();
        String key = PlayerSettingsStore.canonical(item);
        boolean added = PlayerSettingsStore.blacklistAdd(p.getUUID(), key);
        TrashManager.sendSettings(p);
        return personal(ctx, added ? "Added §f" + key + "§7 to your auto-delete blacklist."
                : "§f" + key + "§7 is already on your blacklist.");
    }

    private static int blacklistRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Item item = ItemArgument.getItem(ctx, "item").item().value();
        String key = PlayerSettingsStore.canonical(item);
        boolean removed = PlayerSettingsStore.blacklistRemove(p.getUUID(), key);
        TrashManager.sendSettings(p);
        return personal(ctx, removed ? "Removed §f" + key + "§7 from your blacklist."
                : "§f" + key + "§7 was not on your blacklist.");
    }

    private static int blacklistClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PlayerSettingsStore.blacklistClear(p.getUUID());
        TrashManager.sendSettings(p);
        return personal(ctx, "Cleared your auto-delete blacklist.");
    }

    private static int blacklistList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PlayerSettings s = PlayerSettingsStore.get(p.getUUID());
        return personal(ctx, s.blacklist.isEmpty() ? "Your blacklist is empty."
                : "Blacklist (" + s.blacklist.size() + "): §f" + String.join("§7, §f", s.blacklist));
    }

    private static int showSettings(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PlayerSettings s = PlayerSettingsStore.get(p.getUUID());
        int server = TrashConfigManager.get().removeDelaySeconds;
        String timer = s.personalTimer < 0 ? "server default (" + server + "s)"
                : s.personalTimer == 0 ? "OFF (instant)"
                : s.personalTimer + "s (effective " + Math.min(s.personalTimer, server) + "s)";
        return personal(ctx, "§7Trash settings — timer: §f" + timer
                + "§7, message: §f" + (s.showMessage ? "on" : "off")
                + "§7, blacklist: §f" + s.blacklist.size() + " item(s)§7. Edit with §a/trash timer|message|blacklist§7.");
    }

    // ── admin handlers ───────────────────────────────────────────────────────────
    private static int setDouble(CommandContext<CommandSourceStack> ctx, boolean dbl) {
        TrashConfigManager.get().doubleChest = dbl;
        TrashConfigManager.save();
        return admin(ctx, "Trash chest size set to " + (dbl ? "DOUBLE (54 slots)" : "SINGLE (27 slots)")
                + ". Applies to bins opened from now on.");
    }

    private static int adminTimerOff(CommandContext<CommandSourceStack> ctx) {
        TrashConfigManager.get().countdownEnabled = false;
        TrashConfigManager.save();
        return admin(ctx, "Server trash timer OFF — items are deleted instantly on close for everyone.");
    }

    private static int adminTimerSeconds(CommandContext<CommandSourceStack> ctx) {
        int sec = IntegerArgumentType.getInteger(ctx, "seconds");
        TrashConfig c = TrashConfigManager.get();
        c.removeDelaySeconds = sec;
        c.countdownEnabled = sec > 0;
        TrashConfigManager.save();
        return admin(ctx, sec > 0 ? "Server trash deletion delay set to " + sec + "s."
                : "Server trash timer set to 0 — instant deletion on close.");
    }

    private static int adminMessage(CommandContext<CommandSourceStack> ctx, boolean on) {
        TrashConfigManager.get().showCloseMessage = on;
        TrashConfigManager.save();
        return admin(ctx, "Trash close message " + (on ? "ENABLED" : "DISABLED") + " for all players.");
    }

    private static int adminOverride(CommandContext<CommandSourceStack> ctx, boolean on) {
        TrashConfigManager.get().allowClientOverride = on;
        TrashConfigManager.save();
        return admin(ctx, "Player personal-timer override " + (on ? "ALLOWED" : "DISALLOWED") + ".");
    }

    private static int adminReload(CommandContext<CommandSourceStack> ctx) {
        TrashConfigManager.load();
        return admin(ctx, "Trashventory config reloaded from disk.");
    }

    private static int adminClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        TrashManager.clear(target);
        return admin(ctx, "Cleared " + target.getName().getString() + "'s trash bin.");
    }

    // ── feedback helpers ─────────────────────────────────────────────────────────
    private static int personal(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal("§7" + msg), false);
        return 1;
    }

    private static int admin(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }
}
