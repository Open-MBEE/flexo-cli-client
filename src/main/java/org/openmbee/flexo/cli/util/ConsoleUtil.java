package org.openmbee.flexo.cli.util;

/**
 * Utility class for console output formatting
 */
public class ConsoleUtil {

    // ANSI color codes
    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String CYAN = "\u001B[36m";
    public static final String BOLD = "\u001B[1m";

    private static boolean colorEnabled = true;

    public static void setColorEnabled(boolean enabled) {
        colorEnabled = enabled;
    }

    public static void info(String message) {
        System.out.println(message);
    }

    public static void success(String message) {
        if (colorEnabled) {
            System.out.println(GREEN + message + RESET);
        } else {
            System.out.println(message);
        }
    }

    public static void error(String message) {
        if (colorEnabled) {
            System.err.println(RED + "Error: " + message + RESET);
        } else {
            System.err.println("Error: " + message);
        }
    }

    public static void warn(String message) {
        if (colorEnabled) {
            System.out.println(YELLOW + "Warning: " + message + RESET);
        } else {
            System.out.println("Warning: " + message);
        }
    }

    public static void debug(String message) {
        if (colorEnabled) {
            System.out.println(CYAN + message + RESET);
        } else {
            System.out.println(message);
        }
    }

    public static void header(String message) {
        if (colorEnabled) {
            System.out.println(BOLD + message + RESET);
        } else {
            System.out.println(message);
        }
    }

    public static void printTable(String[] headers, String[][] rows) {
        if (rows.length == 0) {
            return;
        }

        // Calculate column widths
        int[] widths = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            widths[i] = headers[i].length();
        }
        for (String[] row : rows) {
            for (int i = 0; i < row.length && i < widths.length; i++) {
                if (row[i] != null) {
                    widths[i] = Math.max(widths[i], row[i].length());
                }
            }
        }

        // Print headers
        StringBuilder headerLine = new StringBuilder();
        for (int i = 0; i < headers.length; i++) {
            headerLine.append(padRight(headers[i], widths[i]));
            if (i < headers.length - 1) {
                headerLine.append("  ");
            }
        }
        header(headerLine.toString());

        // Print separator
        StringBuilder separator = new StringBuilder();
        for (int i = 0; i < headers.length; i++) {
            separator.append("-".repeat(widths[i]));
            if (i < headers.length - 1) {
                separator.append("  ");
            }
        }
        info(separator.toString());

        // Print rows
        for (String[] row : rows) {
            StringBuilder rowLine = new StringBuilder();
            for (int i = 0; i < headers.length; i++) {
                String cell = i < row.length && row[i] != null ? row[i] : "";
                rowLine.append(padRight(cell, widths[i]));
                if (i < headers.length - 1) {
                    rowLine.append("  ");
                }
            }
            info(rowLine.toString());
        }
    }

    private static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }
}
