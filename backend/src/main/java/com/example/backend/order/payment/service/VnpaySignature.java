package com.example.backend.order.payment.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class VnpaySignature {
    private VnpaySignature() {}
    public static String canonicalQuery(Map<String, String> values) {
        return new TreeMap<>(values).entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .filter(entry -> !entry.getKey().equals("vnp_SecureHash") && !entry.getKey().equals("vnp_SecureHashType"))
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }
    public static String sign(Map<String, String> values, String secret) {
        return signText(canonicalQuery(values), secret);
    }
    public static String signText(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot generate VNPAY signature", exception);
        }
    }
    public static boolean verify(Map<String, String> values, String secret) {
        String supplied = values.get("vnp_SecureHash");
        if (supplied == null || !supplied.matches("[a-fA-F0-9]{128}")) return false;
        return MessageDigest.isEqual(HexFormat.of().parseHex(supplied), HexFormat.of().parseHex(sign(values, secret)));
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
