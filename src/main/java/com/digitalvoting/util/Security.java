package com.digitalvoting.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import java.util.HexFormat;

public final class Security {
    private static final SecureRandom RNG = new SecureRandom();
    private static final int ITER = 120_000;

    private Security() {}

    /** PBKDF2 hash in the form iterations:salt:hash (all in one column). */
    public static String hashPassword(String password) {
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        return ITER + ":" + Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(pbkdf2(password, salt, ITER));
    }

    public static boolean checkPassword(String password, String stored) {
        try {
            String[] p = stored.split(":");
            byte[] expected = Base64.getDecoder().decode(p[2]);
            byte[] actual = pbkdf2(password, Base64.getDecoder().decode(p[1]), Integer.parseInt(p[0]));
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iter) {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(password.toCharArray(), salt, iter, 256)).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String randomOtp() { return String.format("%06d", RNG.nextInt(1_000_000)); }

    public static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        RNG.nextBytes(b);
        return HexFormat.of().withUpperCase().formatHex(b);
    }
}
