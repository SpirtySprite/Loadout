package com.kirugoldzzzz.loadout.common.command;

import com.kirugoldzzzz.loadout.common.text.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class CommandBase implements TabExecutor {

    private final String permission;
    private final boolean playerOnly;

    protected CommandBase(String permission, boolean playerOnly) {
        this.permission = permission;
        this.playerOnly = playerOnly;
    }

    @Override
    public final boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                   @NotNull String label, @NotNull String[] args) {
        if (permission != null && !sender.hasPermission(permission)) {
            Messages.send(sender, "general.no-permission");
            return true;
        }
        if (playerOnly && !(sender instanceof Player)) {
            Messages.send(sender, "general.players-only");
            return true;
        }
        execute(sender, args);
        return true;
    }

    @Override
    public final List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                            @NotNull String label, @NotNull String[] args) {
        if (permission != null && !sender.hasPermission(permission)) {
            return List.of();
        }
        return complete(sender, args);
    }

    protected abstract void execute(CommandSender sender, String[] args);

    protected List<String> complete(CommandSender sender, String[] args) {
        return List.of();
    }

    protected static Player asPlayer(CommandSender sender) {
        return sender instanceof Player player ? player : null;
    }

    protected static List<String> match(Collection<String> options, String input) {
        String prefix = input == null ? "" : input;
        List<String> matched = new ArrayList<>();
        for (String option : options) {
            if (option.regionMatches(true, 0, prefix, 0, prefix.length())) {
                matched.add(option);
            }
        }
        return matched;
    }
}
