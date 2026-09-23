package com.kirugoldzzzz.loadout;

import com.kirugoldzzzz.loadout.common.gui.Guis;
import com.kirugoldzzzz.loadout.common.gui.ChatPrompts;
import com.kirugoldzzzz.loadout.common.text.Messages;
import com.kirugoldzzzz.loadout.common.text.Mini;
import com.kirugoldzzzz.loadout.common.text.Numbers;
import org.bukkit.entity.Player;

import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

final class KitPrompts {

    private KitPrompts() {
    }

    static void text(Player player, String label, boolean long3, Consumer<String> apply, Runnable reopen) {
        Consumer<String> handler = typed -> {
            if (typed == null || typed.isBlank()) {
                reopen.run();
                return;
            }
            apply.accept(typed.trim());
            Guis.success(player);
            reopen.run();
        };
        ChatPrompts.open(player, label, handler, reopen);
    }

    static void duration(Player player, String label, LongConsumer apply, Runnable reopen) {
        ChatPrompts.open(player, label, typed -> {
            if (typed == null || typed.isBlank()) {
                reopen.run();
                return;
            }
            OptionalLong parsed = KitDurations.parse(typed);
            if (parsed.isEmpty()) {
                invalid(player, typed);
            } else {
                apply.accept(parsed.getAsLong());
                Guis.success(player);
            }
            reopen.run();
        });
    }

    static void integer(Player player, String label, int minimum, int maximum, IntConsumer apply, Runnable reopen) {
        ChatPrompts.open(player, label, typed -> {
            if (typed == null || typed.isBlank()) {
                reopen.run();
                return;
            }
            int value;
            try {
                value = Integer.parseInt(typed.trim().replace(" ", ""));
            } catch (NumberFormatException failure) {
                value = Integer.MIN_VALUE;
            }
            if (value < minimum || value > maximum) {
                invalid(player, typed);
            } else {
                apply.accept(value);
                Guis.success(player);
            }
            reopen.run();
        });
    }

    static void amount(Player player, String label, DoubleConsumer apply, Runnable reopen) {
        ChatPrompts.open(player, label, typed -> {
            if (typed == null || typed.isBlank()) {
                reopen.run();
                return;
            }
            OptionalDouble parsed = typed.trim().equals("0") ? OptionalDouble.of(0.0D) : Numbers.parseAmount(typed);
            if (parsed.isEmpty() || parsed.getAsDouble() < 0.0D) {
                invalid(player, typed);
            } else {
                apply.accept(parsed.getAsDouble());
                Guis.success(player);
            }
            reopen.run();
        });
    }

    static void invalid(Player player, String typed) {
        Guis.deny(player);
        Messages.send(player, "kits.invalid-value", Mini.value("input", typed));
    }
}
