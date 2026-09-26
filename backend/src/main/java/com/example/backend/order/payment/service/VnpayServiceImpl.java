package com.example.backend.order.payment.service;

import com.example.backend.cart.repository.CustomerCartRepository;
import com.example.backend.order.entity.*;
import com.example.backend.order.exception.CommerceException;
import com.example.backend.order.payment.config.VnpayProperties;
import com.example.backend.order.payment.dto.*;
import com.example.backend.order.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.MultiValueMap;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;

@Service
public class VnpayServiceImpl implements VnpayService {
    private static final Logger log = LoggerFactory.getLogger(VnpayServiceImpl.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT);
    private final VnpayProperties config;
    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final CustomerCartRepository customers;
    private final TransactionTemplate transaction;
    private final VnpayQueryService query;

    public VnpayServiceImpl(VnpayProperties config, PaymentRepository payments, OrderRepository orders,
                           CustomerCartRepository customers, PlatformTransactionManager transactionManager, VnpayQueryService query) {
        this.config = config; this.payments = payments; this.orders = orders; this.customers = customers;
        this.transaction = new TransactionTemplate(transactionManager);
        this.query = query;
    }

    @Override public boolean enabled() { return config.isEnabled(); }

    @Override public void validateCheckout(BigDecimal amount) {
        requireEnabled();
        if (amount == null || amount.signum() <= 0 || minorUnits(amount).length() > 12) {
            throw new CommerceException(400, "Số tiền VNPAY phải lớn hơn 0 và trong giới hạn 12 chữ số sau khi nhân 100");
        }
    }

    @Override public VnpayPaymentUrlResponse paymentUrl(Order order, Payment payment, String clientIp) {
        validateCheckout(payment.getAmount());
        if (payment.getPaymentMethod() != PaymentMethod.VNPAY || payment.getStatus() != PaymentStatus.PENDING
                || order.getStatus() != OrderStatus.PENDING || !order.getOrderId().equals(payment.getOrderId())) {
            throw new CommerceException(409, "Chỉ mở thanh toán cho Order VNPAY còn PENDING và chưa có kết quả thanh toán");
        }
        if (payment.getTransactionCode() != null) throw new CommerceException(409, "VNPAY báo giao dịch cần đối soát; không mở thanh toán lại");
        var expires = payment.getPaymentDate().plusMinutes(15);
        if (!OffsetDateTime.now().isBefore(expires)) throw new CommerceException(409, "Phiên VNPAY đã hết hạn; hãy hủy đơn chưa thanh toán trước khi đặt lại");
        var params = new TreeMap<String, String>();
        params.put("vnp_Version", "2.1.0"); params.put("vnp_Command", "pay"); params.put("vnp_CurrCode", "VND");
        params.put("vnp_Locale", "vn"); params.put("vnp_OrderType", "other"); params.put("vnp_TmnCode", config.getTmnCode());
        params.put("vnp_TxnRef", payment.getPaymentId().toString().replace("-", ""));
        params.put("vnp_Amount", minorUnits(payment.getAmount()));
        params.put("vnp_OrderInfo", "Thanh toan don hang " + order.getOrderId().toString().replace("-", ""));
        params.put("vnp_ReturnUrl", config.getReturnUrl());
        params.put("vnp_CreateDate", DATE.format(payment.getPaymentDate().atZoneSameInstant(ZONE)));
        params.put("vnp_ExpireDate", DATE.format(expires.atZoneSameInstant(ZONE)));
        params.put("vnp_IpAddr", clientIp(clientIp));
        String url = VnpayProperties.SANDBOX_URL + "?" + VnpaySignature.canonicalQuery(params)
                + "&vnp_SecureHash=" + VnpaySignature.sign(params, config.getHashSecret());
        return new VnpayPaymentUrlResponse(order.getOrderId(), payment.getPaymentId(), url, expires);
    }

    @Override public VnpayPaymentUrlResponse resume(UUID userId, UUID orderId, String clientIp) {
        requireEnabled();
        return transaction.execute(ignored -> {
            var customer = customers.findByUser_UserId(userId).orElseThrow(() -> new CommerceException(403, "Tài khoản chưa có hồ sơ customer"));
            var order = orders.lockById(orderId).orElseThrow(() -> new CommerceException(404, "Không tìm thấy đơn hàng"));
            if (!order.getCustomerId().equals(customer.getCustomerId())) throw new CommerceException(404, "Không tìm thấy đơn hàng");
            var rows = payments.lockByOrderId(orderId);
            if (rows.size() != 1) throw new CommerceException(409, "Đơn không có đúng một Payment");
            return paymentUrl(order, rows.get(0), clientIp);
        });
    }

    @Override public void synchronize(UUID userId, UUID orderId) {
        requireEnabled();
        var customer = customers.findByUser_UserId(userId).orElseThrow(() -> new CommerceException(403, "Tài khoản chưa có hồ sơ customer"));
        var order = orders.findById(orderId).orElseThrow(() -> new CommerceException(404, "Không tìm thấy đơn hàng"));
        if (!order.getCustomerId().equals(customer.getCustomerId())) throw new CommerceException(404, "Không tìm thấy đơn hàng");
        var rows = payments.findByOrderId(orderId);
        if (rows.size() != 1 || rows.get(0).getPaymentMethod() != PaymentMethod.VNPAY) throw new CommerceException(409, "Đơn không có đúng một Payment VNPAY");
        reconcile(rows.get(0));
    }

    private void reconcile(Payment payment) {
        if (payment.getStatus() == PaymentStatus.PAID) return;
        // Network request outside write transactions; lock and re-read before persisting.
        var result = query.query(payment);
        // Query success is distinct from transaction success. Do not mark pending/unknown results FAILED.
        if (!"00".equals(result.get("vnp_ResponseCode")) || !"00".equals(result.get("vnp_TransactionStatus"))) return;
        if (!"01".equals(result.get("vnp_TransactionType")) || !matchingAmount(payment, result)) {
            throw new CommerceException(409, "Kết quả đối soát VNPAY không khớp thanh toán của đơn");
        }
        var response = transaction.execute(ignored -> applyNotification(payment.getPaymentId(), result));
        if (response == null || !("00".equals(response.getCode()) || "02".equals(response.getCode()))) {
            throw new CommerceException(409, "Chưa ghi nhận được kết quả đối soát VNPAY");
        }
        log.info("VNPAY QueryDr reconciled payment {}: {}", payment.getPaymentId(), response.getCode());
    }

    @Override public VnpayIpnResponse notifyPayment(MultiValueMap<String, String> parameters) {
        var response = handleNotification(parameters);
        log.info("VNPAY IPN handled: RspCode={}", response.getCode());
        return response;
    }

    private VnpayIpnResponse handleNotification(MultiValueMap<String, String> parameters) {
        var values = singleValues(parameters);
        if (!validSignature(values)) return ack("97", "Invalid signature");
        if (!config.getTmnCode().equals(values.get("vnp_TmnCode"))) return ack("01", "Merchant not found");
        UUID paymentId = paymentId(values.get("vnp_TxnRef"));
        if (paymentId == null) return ack("01", "Payment not found");
        if (!validResult(values)) return ack("99", "Invalid notification");
        try {
            // Catch persistence errors after the write transaction has rolled back.
            return transaction.execute(ignored -> applyNotification(paymentId, values));
        } catch (RuntimeException exception) {
            log.warn("VNPAY IPN rolled back: {}", exception.getClass().getSimpleName());
            return ack("99", "Update failed");
        }
    }

    private VnpayIpnResponse applyNotification(UUID id, Map<String, String> values) {
        // Read only the immutable FK before locking; do not cache a stale Payment entity in the persistence context.
        var orderId = payments.findOrderIdByPaymentId(id).orElse(null);
        if (orderId == null) return ack("01", "Payment not found");
        // Same lock order as cancellation and administrative Order transitions.
        var order = orders.lockById(orderId).orElse(null);
        if (order == null) return ack("01", "Order not found");
        var rows = payments.lockByOrderId(order.getOrderId());
        if (rows.size() != 1 || !rows.get(0).getPaymentId().equals(id)) return ack("01", "Invalid payment");
        var payment = rows.get(0);
        if (payment.getPaymentMethod() != PaymentMethod.VNPAY) return ack("01", "Invalid payment method");
        if (!matchingAmount(payment, values)) return ack("04", "Invalid amount");
        if (payment.getStatus() == PaymentStatus.PAID) return ack("02", "Payment already confirmed");

        if (success(values)) {
            String transactionNo = values.get("vnp_TransactionNo");
            if (transactionNo == null || !transactionNo.matches("[1-9][0-9]{0,30}")) return ack("99", "Invalid transaction number");
            OffsetDateTime paidAt;
            try { paidAt = LocalDateTime.parse(values.get("vnp_PayDate"), DATE).atZone(ZONE).toOffsetDateTime(); }
            catch (RuntimeException exception) { return ack("99", "Invalid payment date"); }
            payment.setStatus(PaymentStatus.PAID);
            payment.setTransactionCode("VNPAY-" + transactionNo); payment.setPaymentDate(paidAt);
            payments.saveAndFlush(payment);
            if (order.getStatus() == OrderStatus.CANCELLED) {
                log.warn("VNPAY paid after Order cancellation; manual refund review required for order {}", order.getOrderId());
            }
        } else if ("07".equals(values.get("vnp_ResponseCode"))) {
            // VNPAY reports funds deducted but flagged for review: do not call this a failure or fulfill the Order.
            String transactionNo = values.get("vnp_TransactionNo");
            if (transactionNo == null || !transactionNo.matches("[1-9][0-9]{0,30}")) return ack("99", "Invalid transaction number");
            String reviewCode = "VNPAY-" + transactionNo;
            if (reviewCode.equals(payment.getTransactionCode())) return ack("02", "Review already recorded");
            payment.setTransactionCode(reviewCode); payments.saveAndFlush(payment);
            log.warn("VNPAY response 07 requires manual review for payment {}", id);
        } else {
            if (payment.getStatus() == PaymentStatus.FAILED) return ack("02", "Payment already confirmed");
            payment.setStatus(PaymentStatus.FAILED); payments.saveAndFlush(payment);
        }
        // Payment only. No Order state, inventory, serial or warranty mutations here.
        return ack("00", "Confirm Success");
    }

    @Override public String returnRedirect(MultiValueMap<String, String> parameters) {
        var values = singleValues(parameters);
        String destination = config.getFrontendResultUrl();
        if (!validSignature(values) || !config.getTmnCode().equals(values.get("vnp_TmnCode")) || !validResult(values)) {
            return destination + "?gatewayResult=INVALID";
        }
        UUID id = paymentId(values.get("vnp_TxnRef"));
        var payment = id == null ? null : payments.findById(id).orElse(null);
        if (payment == null || payment.getPaymentMethod() != PaymentMethod.VNPAY || !matchingAmount(payment, values)) {
            return destination + "?gatewayResult=INVALID";
        }
        String result = success(values) ? "SUCCESS" : "07".equals(values.get("vnp_ResponseCode")) ? "REVIEW" : "FAILED";
        if (success(values)) {
            try { reconcile(payment); }
            catch (RuntimeException exception) {
                log.warn("VNPAY return reconciliation pending for payment {}: {}", id, exception.getClass().getSimpleName());
            }
        }
        return destination + "?orderId=" + payment.getOrderId() + "&gatewayResult=" + result + "&responseCode=" + values.get("vnp_ResponseCode");
    }

    private boolean matchingAmount(Payment payment, Map<String, String> values) {
        String amount = values.get("vnp_Amount");
        return amount != null && amount.matches("[0-9]{1,12}")
                && new java.math.BigInteger(amount).equals(new java.math.BigInteger(minorUnits(payment.getAmount())));
    }
    private Map<String, String> singleValues(MultiValueMap<String, String> parameters) {
        Map<String, String> result = new TreeMap<>();
        for (var entry : parameters.entrySet()) {
            if (!entry.getKey().startsWith("vnp_")) continue;
            if (entry.getValue().size() != 1 || entry.getValue().get(0) == null) return null;
            result.put(entry.getKey(), entry.getValue().get(0));
        }
        return result;
    }
    private boolean validSignature(Map<String, String> values) {
        return config.isEnabled() && values != null && VnpaySignature.verify(values, config.getHashSecret());
    }
    private boolean validResult(Map<String, String> values) {
        return values.getOrDefault("vnp_ResponseCode", "").matches("[0-9]{2}")
                && values.getOrDefault("vnp_TransactionStatus", "").matches("[0-9]{2}");
    }
    private boolean success(Map<String, String> values) { return "00".equals(values.get("vnp_ResponseCode")) && "00".equals(values.get("vnp_TransactionStatus")); }
    private UUID paymentId(String ref) {
        if (ref == null || !ref.matches("[a-fA-F0-9]{32}")) return null;
        return UUID.fromString(ref.substring(0, 8) + "-" + ref.substring(8, 12) + "-" + ref.substring(12, 16) + "-" + ref.substring(16, 20) + "-" + ref.substring(20));
    }
    private String minorUnits(BigDecimal amount) {
        try { return amount.movePointRight(2).toBigIntegerExact().toString(); }
        catch (ArithmeticException exception) { throw new CommerceException(400, "Số tiền VNPAY chỉ hỗ trợ tối đa 2 chữ số thập phân"); }
    }
    private String clientIp(String address) {
        if (address == null || !address.matches("[0-9a-fA-F:.]{3,45}")) throw new CommerceException(400, "Không xác định được địa chỉ IP thanh toán");
        return "::1".equals(address) || "0:0:0:0:0:0:0:1".equals(address) ? "127.0.0.1" : address;
    }
    private void requireEnabled() { if (!config.isEnabled()) throw new CommerceException(503, "VNPAY Sandbox chưa được cấu hình hoặc đang tắt"); }
    private VnpayIpnResponse ack(String code, String message) { return new VnpayIpnResponse(code, message); }
}
