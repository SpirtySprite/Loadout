package com.kirugoldzzzz.loadout;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record KitCondition(String placeholder, Operator operator, String expected, String label) {

    private static final Pattern SHAPE = Pattern.compile("^\\s*(%[^%\\s]+%)\\s*(>=|<=|!=|==|=|>|<|contains)\\s*(.+?)\\s*$");

    public enum Operator {
        EQUAL("="),
        NOT_EQUAL("≠"),
        GREATER(">"),
        GREATER_OR_EQUAL("≥"),
        LESS("<"),
        LESS_OR_EQUAL("≤"),
        CONTAINS("∋");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }

        static Operator of(String raw) {
            return switch (raw) {
                case "=", "==" -> EQUAL;
                case "!=" -> NOT_EQUAL;
                case ">" -> GREATER;
                case ">=" -> GREATER_OR_EQUAL;
                case "<" -> LESS;
                case "<=" -> LESS_OR_EQUAL;
                default -> CONTAINS;
            };
        }
    }

    public static KitCondition parse(String raw, String label) {
        if (raw == null) {
            return null;
        }
        Matcher matcher = SHAPE.matcher(raw);
        if (!matcher.matches()) {
            return null;
        }
        String placeholder = matcher.group(1);
        Operator operator = Operator.of(matcher.group(2));
        String expected = unquote(matcher.group(3));
        String name = label == null || label.isBlank() ? readable(placeholder) : label.strip();
        return new KitCondition(placeholder, operator, expected, name);
    }

    public boolean test(String actual) {
        String value = actual == null ? "" : actual.strip();
        Double left = number(value);
        Double right = number(expected);
        if (left != null && right != null) {
            int order = Double.compare(left, right);
            return switch (operator) {
                case EQUAL -> order == 0;
                case NOT_EQUAL -> order != 0;
                case GREATER -> order > 0;
                case GREATER_OR_EQUAL -> order >= 0;
                case LESS -> order < 0;
                case LESS_OR_EQUAL -> order <= 0;
                case CONTAINS -> value.contains(expected);
            };
        }
        return switch (operator) {
            case EQUAL -> value.equalsIgnoreCase(expected);
            case NOT_EQUAL -> !value.equalsIgnoreCase(expected);
            case CONTAINS -> value.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
            default -> false;
        };
    }

    public String describe() {
        return operator.symbol() + " " + expected;
    }

    private static Double number(String raw) {
        String cleaned = raw.replace(",", "").replace("_", "").strip();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static String unquote(String raw) {
        String value = raw.strip();
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String readable(String placeholder) {
        String inner = placeholder.substring(1, placeholder.length() - 1).replace('_', ' ');
        return inner.isEmpty() ? placeholder : Character.toUpperCase(inner.charAt(0)) + inner.substring(1);
    }
}
