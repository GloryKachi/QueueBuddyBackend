package com.queuebuddy.queuebuddy.util;

public final class Phones {

    private Phones() {}

    // 08031234567 / 2348031234567 / +2348031234567  ->  +2348031234567
    public static String normalize(String raw) {
        String p = raw.trim().replaceAll("[\\s()-]", "");
        if (p.startsWith("+234")) return p;
        if (p.startsWith("234")) return "+" + p;
        if (p.startsWith("0")) return "+234" + p.substring(1);
        return "+234" + p;
    }

    public static String normalizeOrNull(String raw) {
        return raw == null || raw.isBlank() ? null : normalize(raw);
    }

    // +2348031234567 -> "0803 *** 4567"
    public static String mask(String e164) {
        if (e164 == null) return null;
        String national = e164.startsWith("+234") ? "0" + e164.substring(4) : e164;
        if (national.length() < 8) return national;
        return national.substring(0, 4) + " *** " + national.substring(national.length() - 4);
    }
}