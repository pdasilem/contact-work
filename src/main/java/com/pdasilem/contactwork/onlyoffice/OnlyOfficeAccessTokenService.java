package com.pdasilem.contactwork.onlyoffice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OnlyOfficeAccessTokenService {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec secretKey;

    public OnlyOfficeAccessTokenService(
            @Value("${app.onlyoffice.access-secret:dev-onlyoffice-secret}") String accessSecret
    ) {
        this.secretKey = new SecretKeySpec(requiredSecret(accessSecret).getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String documentToken(UUID projectId) {
        return token("document", projectId);
    }

    public boolean isValidDocumentToken(UUID projectId, String token) {
        return isValid("document", projectId, token);
    }

    public String callbackToken(UUID projectId) {
        return token("callback", projectId);
    }

    public boolean isValidCallbackToken(UUID projectId, String token) {
        return isValid("callback", projectId, token);
    }

    public String conversionToken(UUID fileId) {
        return token("conversion", fileId);
    }

    public boolean isValidConversionToken(UUID fileId, String token) {
        return isValid("conversion", fileId, token);
    }

    private String token(String scope, UUID id) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secretKey);
            byte[] digest = mac.doFinal((scope + ":" + id).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign ONLYOFFICE access token", ex);
        }
    }

    private boolean isValid(String scope, UUID id, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(token(scope, id).getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    private String requiredSecret(String accessSecret) {
        if (accessSecret == null || accessSecret.isBlank()) {
            throw new IllegalStateException("app.onlyoffice.access-secret must not be blank");
        }
        return accessSecret;
    }
}
