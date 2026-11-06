package com.example.backend.order.scheduling;

import com.example.backend.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cancels orders whose VNPAY session expired without any payment result, so their reserved stock
 * returns to sale. The VNPAY session lasts 15 minutes; the extra grace period leaves time for a late IPN.
 * Orders flagged for VNPAY review (transaction code present) are never touched.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.order.vnpay-expiry.enabled", havingValue = "true", matchIfMissing = true)
public class VnpayExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(VnpayExpiryJob.class);
    private final OrderService orders;
    private final Duration cancelAfter;

    public VnpayExpiryJob(OrderService orders, @Value("${app.order.vnpay-expiry.cancel-after:PT20M}") Duration cancelAfter) {
        this.orders = orders;
        this.cancelAfter = cancelAfter;
    }

    @Scheduled(initialDelayString = "${app.order.vnpay-expiry.initial-delay:PT1M}",
            fixedDelayString = "${app.order.vnpay-expiry.interval:PT5M}")
    public void run() {
        expireNow();
    }

    /** Returns how many orders were cancelled. Each order is handled in its own transaction. */
    public int expireNow() {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(cancelAfter);
        int cancelled = 0;
        for (UUID orderId : orders.unpaidVnpayOrdersBefore(cutoff)) {
            try {
                if (orders.expireUnpaidVnpay(orderId, cutoff)) cancelled++;
            } catch (RuntimeException exception) {
                log.warn("Could not expire unpaid VNPAY order {}: {}", orderId, exception.getMessage());
            }
        }
        if (cancelled > 0) log.info("Cancelled {} unpaid VNPAY order(s) older than {}", cancelled, cutoff);
        return cancelled;
    }
}
