package com.digitalvoting.util;

import java.time.LocalDate;
import java.time.Period;
import java.util.regex.Pattern;

public final class Validators {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern MOBILE = Pattern.compile("^[6-9][0-9]{9}$");

    private Validators() {}

    public static boolean email(String s) { return s != null && EMAIL.matcher(s).matches(); }
    public static boolean mobile(String s) { return s != null && MOBILE.matcher(s).matches(); }
    public static boolean strongPassword(String s) {
        return s != null && s.length() >= 8 && s.chars().anyMatch(Character::isLetter) && s.chars().anyMatch(Character::isDigit);
    }
    public static boolean adult(LocalDate dob) { return dob != null && Period.between(dob, LocalDate.now()).getYears() >= 18; }

    public static String maskEmail(String e) {
        int at = e.indexOf('@');
        return at <= 2 ? e : e.substring(0, 2) + "****" + e.substring(at);
    }
    public static String maskMobile(String m) { return m.length() < 4 ? m : "******" + m.substring(m.length() - 4); }
}
