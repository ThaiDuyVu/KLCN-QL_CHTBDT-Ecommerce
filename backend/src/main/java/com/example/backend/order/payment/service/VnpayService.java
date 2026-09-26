package com.example.backend.order.payment.service;

import com.example.backend.order.entity.Order;
import com.example.backend.order.entity.Payment;
import com.example.backend.order.payment.dto.*;
import org.springframework.util.MultiValueMap;
import java.math.BigDecimal;
import java.util.UUID;

public interface VnpayService {
    boolean enabled();
    void validateCheckout(BigDecimal amount);
    VnpayPaymentUrlResponse paymentUrl(Order order, Payment payment, String clientIp);
    VnpayPaymentUrlResponse resume(UUID userId, UUID orderId, String clientIp);
    void synchronize(UUID userId, UUID orderId);
    VnpayIpnResponse notifyPayment(MultiValueMap<String, String> parameters);
    String returnRedirect(MultiValueMap<String, String> parameters);
}
