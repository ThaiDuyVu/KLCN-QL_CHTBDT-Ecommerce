package com.example.backend.order.payment.service;

import com.example.backend.order.entity.Payment;
import com.example.backend.order.exception.CommerceException;
import com.example.backend.order.payment.config.VnpayProperties;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class VnpayQueryService {
    private static final Logger log = LoggerFactory.getLogger(VnpayQueryService.class);
    private static final String URL = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");
    private static final List<String> REQUEST_FIELDS = List.of("vnp_RequestId", "vnp_Version", "vnp_Command", "vnp_TmnCode",
            "vnp_TxnRef", "vnp_TransactionDate", "vnp_CreateDate", "vnp_IpAddr", "vnp_OrderInfo");
    private static final List<String> RESPONSE_FIELDS = List.of("vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message",
            "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo",
            "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo", "vnp_PromotionCode", "vnp_PromotionAmount");
    private final VnpayProperties config;
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public VnpayQueryService(VnpayProperties config) { this.config = config; }

    public Map<String, String> query(Payment payment) {
        var values = new LinkedHashMap<String, String>();
        values.put("vnp_RequestId", UUID.randomUUID().toString().replace("-", ""));
        values.put("vnp_Version", "2.1.0"); values.put("vnp_Command", "querydr");
        values.put("vnp_TmnCode", config.getTmnCode());
        values.put("vnp_TxnRef", payment.getPaymentId().toString().replace("-", ""));
        values.put("vnp_TransactionDate", DATE.format(payment.getPaymentDate().atZoneSameInstant(ZONE)));
        values.put("vnp_CreateDate", DATE.format(ZonedDateTime.now(ZONE)));
        values.put("vnp_IpAddr", "127.0.0.1");
        values.put("vnp_OrderInfo", "Kiem tra ket qua thanh toan");
        values.put("vnp_SecureHash", VnpaySignature.signText(join(values, REQUEST_FIELDS), config.getHashSecret()));
        try {
            var request = HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(values))).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("VNPAY QueryDr HTTP status {}", response.statusCode()); throw unavailable();
            }
            Map<?, ?> raw = json.readValue(response.body(), Map.class);
            Map<String, String> result = new LinkedHashMap<>();
            raw.forEach((key, value) -> result.put(String.valueOf(key), value == null ? "" : String.valueOf(value)));
            // Error responses can omit the signature. They never authorize a Payment update.
            if ("94".equals(result.get("vnp_ResponseCode"))) {
                throw new CommerceException(429, "VNPAY giới hạn truy vấn lặp lại. Vui lòng chờ vài phút rồi kiểm tra lại; không thanh toán lại nếu đã trừ tiền.");
            }
            if (!"00".equals(result.get("vnp_ResponseCode"))) throw unavailable();
            String signature = result.getOrDefault("vnp_SecureHash", "");
            String expected = VnpaySignature.signText(join(result, RESPONSE_FIELDS), config.getHashSecret());
            if (!signature.matches("[a-fA-F0-9]{128}") || !MessageDigest.isEqual(
                    HexFormat.of().parseHex(signature), HexFormat.of().parseHex(expected))
                    || !config.getTmnCode().equals(result.get("vnp_TmnCode"))
                    || !values.get("vnp_TxnRef").equals(result.get("vnp_TxnRef"))
                    || (!result.getOrDefault("vnp_Command", "").isEmpty() && !"querydr".equals(result.get("vnp_Command")))) {
                log.warn("VNPAY QueryDr response validation failed; response code {}", result.get("vnp_ResponseCode"));
                throw unavailable();
            }
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw unavailable();
        } catch (java.io.IOException | tools.jackson.core.JacksonException exception) {
            log.warn("VNPAY QueryDr request failed: {}", exception.getClass().getSimpleName());
            throw unavailable();
        }
    }

    private String join(Map<String, String> values, List<String> fields) {
        return String.join("|", fields.stream().map(field -> values.getOrDefault(field, "")).toList());
    }
    private CommerceException unavailable() {
        return new CommerceException(503, "Chưa đối soát được kết quả với VNPAY. Vui lòng thử kiểm tra lại; không thanh toán lại nếu đã trừ tiền.");
    }
}
