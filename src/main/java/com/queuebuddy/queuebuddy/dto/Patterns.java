package com.queuebuddy.queuebuddy.dto;

public final class Patterns {
    private Patterns() {}
    // Accepts 08031234567, 2348031234567 or +2348031234567 (no spaces)
    public static final String NG_PHONE = "^(?:\\+234|234|0)[789][01]\\d{8}$";
}