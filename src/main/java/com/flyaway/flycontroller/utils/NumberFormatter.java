package com.flyaway.flycontroller.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class NumberFormatter {

    private static final DecimalFormat FORMATTER;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setGroupingSeparator(' ');

        FORMATTER = new DecimalFormat("#,###.##", symbols);
        FORMATTER.setGroupingSize(3);
        FORMATTER.setGroupingUsed(true);
    }

    public static String format(double number) {
        return FORMATTER.format(number);
    }

    public static String format(double number, int decimalPlaces) {
        if (decimalPlaces == 0) {
            DecimalFormat noDecimalFormatter = new DecimalFormat("#,###", FORMATTER.getDecimalFormatSymbols());
            noDecimalFormatter.setGroupingSize(3);
            return noDecimalFormatter.format(number);
        }

        String pattern = "#,###." + "0".repeat(decimalPlaces);
        DecimalFormat customFormatter = new DecimalFormat(pattern, FORMATTER.getDecimalFormatSymbols());
        customFormatter.setGroupingSize(3);
        return customFormatter.format(number);
    }

    public static String formatWithCurrency(double number, String currencySymbol) {
        return format(number) + currencySymbol;
    }
}
