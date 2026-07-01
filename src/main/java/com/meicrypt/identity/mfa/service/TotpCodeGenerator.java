package com.meicrypt.identity.mfa.service;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * Pure RFC 6238 TOTP generator + verifier (Module 9.1).
 * <p>Uses HMAC-SHA1/256/512 exactly as prescribed by section 5.2. Codes are
 * emitted zero-padded to the configured digit count.  A ±N step verification
 * window is supported to absorb device clock drift.
 * <p>Base32 encoding follows RFC 4648 §6 (no padding on emit).
 */
@Component
public class TotpCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int[] DIGIT_POWER = {
            1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000, 1_000_000_000
    };

    /**
     * Generate a fresh 20-byte (SHA-1 preferred, RFC 6238 §5.1) shared secret
     * and return it Base32-encoded (no padding).
     */
    public String newSecretBase32() {
        byte[] key = new byte[20];
        RANDOM.nextBytes(key);
        return encodeBase32(key);
    }

    /**
     * Build the canonical {@code otpauth://totp/…} URI consumed by every major
     * authenticator app (Google Authenticator, Authy, 1Password, Bitwarden).
     */
    public String otpAuthUri(String issuer, String accountLabel, String secretBase32,
                             String algorithm, int digits, int periodSeconds) {
        String label = urlEncode(issuer) + ":" + urlEncode(accountLabel);
        return "otpauth://totp/" + label
                + "?secret=" + secretBase32
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=" + algorithm
                + "&digits=" + digits
                + "&period=" + periodSeconds;
    }

    /**
     * Return {@code true} iff {@code code} is a valid TOTP for {@code secretBase32}
     * at the current instant, allowing ±{@code allowedSkewSteps} 30-second windows.
     * The {@code lastUsedCounter} check prevents replay: a token from a
     * counter that is already recorded is refused.
     */
    public VerificationResult verify(String secretBase32,
                                     String code,
                                     String algorithm,
                                     int digits,
                                     int periodSeconds,
                                     int allowedSkewSteps,
                                     Long lastUsedCounter) {
        if (code == null || code.isBlank()) return VerificationResult.invalid();
        long nowSteps = Instant.now().getEpochSecond() / periodSeconds;
        byte[] key = decodeBase32(secretBase32);
        for (int i = -allowedSkewSteps; i <= allowedSkewSteps; i++) {
            long counter = nowSteps + i;
            if (lastUsedCounter != null && counter <= lastUsedCounter) continue;
            String candidate = computeCode(key, counter, algorithm, digits);
            if (constantTimeEquals(candidate, code)) {
                return VerificationResult.ok(counter);
            }
        }
        return VerificationResult.invalid();
    }

    private String computeCode(byte[] key, long counter, String algorithm, int digits) {
        try {
            String hmacAlg = switch (algorithm.toUpperCase()) {
                case "SHA256" -> "HmacSHA256";
                case "SHA512" -> "HmacSHA512";
                default       -> "HmacSHA1";
            };
            Mac mac = Mac.getInstance(hmacAlg);
            mac.init(new SecretKeySpec(key, hmacAlg));
            byte[] counterBytes = ByteBuffer.allocate(Long.BYTES).putLong(counter).array();
            byte[] hash = mac.doFinal(counterBytes);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary =
                    ((hash[offset]     & 0x7F) << 24) |
                    ((hash[offset + 1] & 0xFF) << 16) |
                    ((hash[offset + 2] & 0xFF) <<  8) |
                    ( hash[offset + 3] & 0xFF);
            int otp = binary % DIGIT_POWER[digits];
            String s = Integer.toString(otp);
            while (s.length() < digits) s = "0" + s;
            return s;
        } catch (Exception ex) {
            throw new IllegalStateException("TOTP HMAC computation failed", ex);
        }
    }

    // ------------------------------------------------------------------
    // Base32 (RFC 4648 §6) - no padding on emit; accepts padded input.
    // ------------------------------------------------------------------
    private static String encodeBase32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int idx = (buffer >> (bitsLeft - 5)) & 0x1F;
                sb.append(BASE32_ALPHABET.charAt(idx));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            int idx = (buffer << (5 - bitsLeft)) & 0x1F;
            sb.append(BASE32_ALPHABET.charAt(idx));
        }
        return sb.toString();
    }

    private static byte[] decodeBase32(String s) {
        String clean = s.replaceAll("[^A-Z2-7]", "").toUpperCase();
        int outLen = clean.length() * 5 / 8;
        byte[] out = new byte[outLen];
        int buffer = 0, bitsLeft = 0, outIdx = 0;
        for (char c : clean.toCharArray()) {
            int idx = BASE32_ALPHABET.indexOf(c);
            if (idx < 0) continue;
            buffer = (buffer << 5) | idx;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out[outIdx++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    public record VerificationResult(boolean valid, Long acceptedCounter) {
        public static VerificationResult ok(long counter) { return new VerificationResult(true, counter); }
        public static VerificationResult invalid() { return new VerificationResult(false, null); }
    }
}
