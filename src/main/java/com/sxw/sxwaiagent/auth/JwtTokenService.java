package com.sxw.sxwaiagent.auth;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
public class JwtTokenService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final AuthProperties properties;

    public JwtTokenService(AuthProperties properties) {
        this.properties = properties;
    }

    public String generateToken(String username) {
        long expiresAt = Instant.now().plusSeconds(properties.getJwtExpirationMinutes() * 60).getEpochSecond();
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        JSONObject payload = new JSONObject();
        payload.set("sub", username);
        payload.set("exp", expiresAt);
        String unsigned = encode(header) + "." + encode(payload.toString());
        return unsigned + "." + sign(unsigned);
    }

    public boolean validateToken(String token) {
        try {
            String username = resolveUsername(token);
            return username != null && !username.isBlank();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public String resolveUsername(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("invalid token");
        }
        String unsigned = parts[0] + "." + parts[1];
        if (!constantTimeEquals(sign(unsigned), parts[2])) {
            throw new IllegalArgumentException("invalid token signature");
        }
        String payload = new String(URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
        JSONObject claims = JSONUtil.parseObj(payload);
        long exp = claims.getLong("exp", 0L);
        if (Instant.now().getEpochSecond() > exp) {
            throw new IllegalArgumentException("token expired");
        }
        return claims.getStr("sub");
    }

    private String sign(String unsigned) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return URL_ENCODER.encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("Failed to sign token", e);
        }
    }

    private static String encode(String value) {
        return URL_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }
}
