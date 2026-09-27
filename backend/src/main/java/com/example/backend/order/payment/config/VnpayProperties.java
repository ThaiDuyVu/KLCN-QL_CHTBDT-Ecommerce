package com.example.backend.order.payment.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import java.net.URI;

@Component
@Validated
@ConfigurationProperties(prefix = "app.payment.vnpay")
public class VnpayProperties {
    private boolean enabled;
    private String tmnCode = "";
    private String hashSecret = "";
    private String returnUrl = "http://localhost:8080/api/payments/vnpay/return";
    private String frontendResultUrl = "http://localhost:5173/payments/vnpay/result";
    public static final String SANDBOX_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";

    @AssertTrue(message = "VNPAY enabled requires sandbox merchant credentials and valid return/result URLs")
    public boolean isConfigurationValid() {
        return !enabled || (tmnCode != null && tmnCode.matches("[A-Za-z0-9]{8}")
                && hashSecret != null && !hashSecret.isBlank() && validUrl(returnUrl) && validUrl(frontendResultUrl));
    }
    private boolean validUrl(String value) {
        try {
            var uri = URI.create(value);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null && uri.getFragment() == null && uri.getQuery() == null;
        } catch (RuntimeException exception) { return false; }
    }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getTmnCode() { return tmnCode; }
    public void setTmnCode(String value) { tmnCode = value; }
    public String getHashSecret() { return hashSecret; }
    public void setHashSecret(String value) { hashSecret = value; }
    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String value) { returnUrl = value; }
    public String getFrontendResultUrl() { return frontendResultUrl; }
    public void setFrontendResultUrl(String value) { frontendResultUrl = value; }
}
