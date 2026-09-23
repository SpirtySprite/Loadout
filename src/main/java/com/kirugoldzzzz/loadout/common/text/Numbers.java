package com.kirugoldzzzz.loadout.common.text;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class Numbers {

    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.US);
    private static final NavigableMap<Double, String> SUFFIXES = new TreeMap<>();
    private static final Pattern DECIMAL =
            Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

    static {
        SUFFIXES.put(1_000.0D, "k");
        SUFFIXES.put(1_000_000.0D, "M");
        SUFFIXES.put(1_000_000_000.0D, "B");
        SUFFIXES.put(1_000_000_000_000.0D, "T");
        SUFFIXES.put(1_000_000_000_000_000.0D, "Q");
    }

    private static volatile String pattern = "#,##0.00";
    private static volatile String currency = Palette.COIN;

    private static final ThreadLocal<DecimalFormat> EXACT =
            ThreadLocal.withInitial(() -> new DecimalFormat(pattern, SYMBOLS));
    private static final ThreadLocal<DecimalFormat> SHORT =
            ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.##", SYMBOLS));
    private static final ThreadLocal<DecimalFormat> COUNT =
            ThreadLocal.withInitial(() -> new DecimalFormat("#,##0", SYMBOLS));

    private Numbers() {
    }

    public static void configure(String currencySymbol, String formatPattern) {
        currency = currencySymbol;
        pattern = formatPattern;
        EXACT.remove();
    }

    public static String money(double amount) {
        return currency + shorten(amount);
    }

    public static String count(long value) {
        return COUNT.get().format(value);
    }

    public static String compact(long value) {
        return Math.abs(value) < 1_000L ? String.valueOf(value) : shorten(value);
    }

    public static String plain(double amount) {
        DecimalFormat format = EXACT.get();
        if (!format.toPattern().equals(pattern)) {
            format.applyPattern(pattern);
        }
        return format.format(amount);
    }

    private static String shorten(double amount) {
        if (amount < 0.0D) {
            return "-" + shorten(-amount);
        }
        Map.Entry<Double, String> tier = SUFFIXES.floorEntry(amount);
        if (tier == null) {
            return plain(amount);
        }
        Map.Entry<Double, String> higher = SUFFIXES.higherEntry(tier.getKey());
        if (higher != null && Math.round(amount / tier.getKey() * 100.0D) >= 100_000L) {
            tier = higher;
        }
        return SHORT.get().format(amount / tier.getKey()) + tier.getValue();
    }

    public static String duration(long millis) {
        if (millis <= 0L) {
            return "0s";
        }
        long totalSeconds = (millis + 999L) / 1000L;
        long days = totalSeconds / 86400L;
        long hours = totalSeconds % 86400L / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        StringBuilder builder = new StringBuilder();
        if (days > 0L) {
            builder.append(days).append("j ");
        }
        if (hours > 0L) {
            builder.append(hours).append("h ");
        }
        if (minutes > 0L) {
            builder.append(minutes).append("m ");
        }
        if (seconds > 0L || builder.isEmpty()) {
            builder.append(seconds).append('s');
        }
        return builder.toString().trim();
    }

    public static double round(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public static OptionalDouble parseAmount(String input) {
        if (input == null || input.isBlank()) {
            return OptionalDouble.empty();
        }
        String cleaned = input.trim().replace("_", "").replace(" ", "")
                .replace("\u00A0", "").replace("\u202F", "");
        if (!currency.isEmpty()) {
            cleaned = cleaned.replace(currency, "");
        }
        if (cleaned.isBlank()) {
            return OptionalDouble.empty();
        }
        double multiplier = 1.0D;
        int suffix = "kmbtq".indexOf(Character.toLowerCase(cleaned.charAt(cleaned.length() - 1)));
        if (suffix >= 0) {
            multiplier = Math.pow(1000.0D, suffix + 1.0D);
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        cleaned = separators(cleaned);
        if (!DECIMAL.matcher(cleaned).matches()) {
            return OptionalDouble.empty();
        }
        try {
            double parsed = Double.parseDouble(cleaned) * multiplier;
            return Double.isFinite(parsed) ? OptionalDouble.of(round(parsed)) : OptionalDouble.empty();
        } catch (NumberFormatException exception) {
            return OptionalDouble.empty();
        }
    }

    static String separators(String value) {
        int comma = value.indexOf(',');
        if (comma < 0) {
            return value;
        }
        String integer = value.substring(0, comma).replace("-", "").replace("+", "");
        boolean grouped = value.indexOf('.') >= 0 || comma != value.lastIndexOf(',')
                || value.length() - comma - 1 == 3 && !integer.isEmpty() && integer.chars().anyMatch(digit -> digit != '0');
        return grouped ? value.replace(",", "") : value.replace(',', '.');
    }

    public static OptionalInt parseCount(String input) {
        OptionalDouble parsed = parseAmount(input);
        if (parsed.isEmpty()) {
            return OptionalInt.empty();
        }
        double value = parsed.getAsDouble();
        if (value != Math.rint(value) || value < 0.0D || value > Integer.MAX_VALUE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) value);
    }

    public static OptionalDouble parseMoney(String input) {
        OptionalDouble parsed = parsePositive(input);
        return parsed.isPresent() && round(parsed.getAsDouble()) > 0.0D ? OptionalDouble.of(round(parsed.getAsDouble()))
                : OptionalDouble.empty();
    }

    public static OptionalDouble parsePositive(String input) {
        OptionalDouble parsed = parseAmount(input);
        return parsed.isPresent() && parsed.getAsDouble() > 0.0D ? parsed : OptionalDouble.empty();
    }

    public static int parseInt(String input, int fallback) {
        if (input == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
