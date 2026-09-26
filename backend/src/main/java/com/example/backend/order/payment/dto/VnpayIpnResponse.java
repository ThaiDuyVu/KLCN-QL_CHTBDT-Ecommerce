package com.example.backend.order.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VnpayIpnResponse {
    private final String code;
    private final String message;
    public VnpayIpnResponse(String code, String message) { this.code = code; this.message = message; }
    @JsonProperty("RspCode") public String getCode() { return code; }
    @JsonProperty("Message") public String getMessage() { return message; }
}
