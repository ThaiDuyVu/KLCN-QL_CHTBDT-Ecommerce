package com.example.backend.customer.repository;

import com.example.backend.auth.entity.UserStatus;
import com.example.backend.customer.dto.*;
import com.example.backend.order.entity.InstallmentStatus;
import com.example.backend.order.entity.OrderStatus;
import com.example.backend.warranty.entity.WarrantyStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerManagementRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public CustomerManagementRepository(DataSource dataSource) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.setQueryTimeout(5);
        jdbc = new NamedParameterJdbcTemplate(template);
    }

    private static final String PROFILE = """
            SELECT c.customer_id, c.user_id, c.full_name, c.address, c.loyalty_point,
                   u.username, u.display_name, u.email, u.phone, u.status, u.created_at
            FROM customers c JOIN users u ON u.user_id = c.user_id
            """;

    private String filter(String keyword, UserStatus status) {
        return " WHERE 1=1" + (status == null ? "" : " AND u.status = :status")
                + (keyword.isEmpty() ? "" : " " + """
                  AND (LOWER(c.full_name) LIKE :term ESCAPE '\\'
                    OR LOWER(u.username) LIKE :term ESCAPE '\\'
                    OR LOWER(u.email) LIKE :term ESCAPE '\\'
                    OR LOWER(u.phone) LIKE :term ESCAPE '\\')
                  """);
    }

    private MapSqlParameterSource parameters(String keyword, UserStatus status) {
        String escaped = keyword.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return new MapSqlParameterSource("term", "%" + escaped + "%")
                .addValue("status", status == null ? null : status.name());
    }

    private UUID id(ResultSet rs, String column) throws SQLException { return rs.getObject(column, UUID.class); }
    private OffsetDateTime date(ResultSet rs, String column) throws SQLException { return rs.getObject(column, OffsetDateTime.class); }

    private CustomerSummaryResponse profile(ResultSet rs) throws SQLException {
        return new CustomerSummaryResponse(id(rs, "customer_id"), id(rs, "user_id"), rs.getString("full_name"),
                rs.getString("address"), rs.getInt("loyalty_point"), rs.getString("username"), rs.getString("display_name"),
                rs.getString("email"), rs.getString("phone"), UserStatus.valueOf(rs.getString("status")),
                date(rs, "created_at"), emptySummary());
    }

    public static CustomerOrderSummaryResponse emptySummary() {
        return new CustomerOrderSummaryResponse(0, 0, 0, BigDecimal.ZERO, null, null, null, null);
    }

    public long countCustomers(String keyword, UserStatus status) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM customers c JOIN users u ON u.user_id = c.user_id"
                + filter(keyword, status), parameters(keyword, status), Long.class);
    }

    public List<CustomerSummaryResponse> customers(String keyword, UserStatus status, int page, int size) {
        return jdbc.query(PROFILE + filter(keyword, status) + " ORDER BY u.created_at DESC, c.customer_id DESC LIMIT :size OFFSET :offset",
                parameters(keyword, status).addValue("size", size).addValue("offset", (long) page * size),
                (rs, row) -> profile(rs));
    }

    public Optional<CustomerSummaryResponse> customer(UUID customerId) {
        return jdbc.query(PROFILE + " WHERE c.customer_id = :id", new MapSqlParameterSource("id", customerId),
                (rs, row) -> profile(rs)).stream().findFirst();
    }

    public boolean exists(UUID customerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM customers WHERE customer_id = :id)",
                new MapSqlParameterSource("id", customerId), Boolean.class));
    }

    public Map<UUID, CustomerOrderSummaryResponse> orderSummaries(Collection<UUID> customerIds) {
        if (customerIds.isEmpty()) return Map.of();
        String sql = """
                WITH totals AS (
                    SELECT customer_id, COUNT(*) AS total,
                           COUNT(*) FILTER (WHERE status = 'DELIVERED') AS delivered,
                           COUNT(*) FILTER (WHERE status = 'CANCELLED') AS cancelled,
                           COALESCE(SUM(total_amount) FILTER (WHERE status = 'DELIVERED'), 0) AS spend,
                           MAX(order_date) AS latest_date
                    FROM orders WHERE customer_id IN (:ids) GROUP BY customer_id
                ), latest AS (
                    SELECT DISTINCT ON (customer_id) customer_id, order_id, order_code, status
                    FROM orders WHERE customer_id IN (:ids)
                    ORDER BY customer_id, order_date DESC, order_id DESC
                )
                SELECT t.*, l.order_id, l.order_code, l.status
                FROM totals t JOIN latest l ON l.customer_id = t.customer_id
                """;
        Map<UUID, CustomerOrderSummaryResponse> result = new HashMap<>();
        jdbc.query(sql, new MapSqlParameterSource("ids", customerIds), (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            result.put(id(rs, "customer_id"), new CustomerOrderSummaryResponse(rs.getLong("total"), rs.getLong("delivered"),
                    rs.getLong("cancelled"), rs.getBigDecimal("spend"), id(rs, "order_id"), rs.getString("order_code"),
                    date(rs, "latest_date"), OrderStatus.valueOf(rs.getString("status"))));
        });
        return result;
    }

    private MapSqlParameterSource page(UUID customerId, int page, int size) {
        return new MapSqlParameterSource("id", customerId).addValue("size", size).addValue("offset", (long) page * size);
    }

    public long countOrders(UUID customerId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE customer_id = :id", page(customerId, 0, 1), Long.class);
    }

    public List<CustomerOrderResponse> orders(UUID customerId, int page, int size) {
        return jdbc.query("""
                SELECT o.order_id, o.order_code, o.order_date, o.status, o.total_amount, o.warehouse_id, w.warehouse_name
                FROM orders o JOIN warehouses w ON w.warehouse_id = o.warehouse_id
                WHERE o.customer_id = :id ORDER BY o.order_date DESC, o.order_id DESC LIMIT :size OFFSET :offset
                """, page(customerId, page, size), (rs, row) -> new CustomerOrderResponse(id(rs, "order_id"),
                        rs.getString("order_code"), date(rs, "order_date"), OrderStatus.valueOf(rs.getString("status")),
                        rs.getBigDecimal("total_amount"), id(rs, "warehouse_id"), rs.getString("warehouse_name")));
    }

    public long countWarranties(UUID customerId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM warranties WHERE customer_id = :id", page(customerId, 0, 1), Long.class);
    }

    public List<CustomerWarrantyResponse> warranties(UUID customerId, int page, int size) {
        return jdbc.query("""
                SELECT w.warranty_id, w.serial_id, s.serial_number, v.sku, p.product_name,
                       o.order_id, o.order_code, w.start_date, w.end_date, w.status
                FROM warranties w JOIN serial_numbers s ON s.serial_id = w.serial_id
                JOIN product_variants v ON v.variant_id = s.variant_id JOIN products p ON p.product_id = v.product_id
                JOIN order_items i ON i.order_item_id = w.order_item_id JOIN orders o ON o.order_id = i.order_id
                WHERE w.customer_id = :id ORDER BY w.start_date DESC, w.warranty_id DESC LIMIT :size OFFSET :offset
                """, page(customerId, page, size), (rs, row) -> new CustomerWarrantyResponse(id(rs, "warranty_id"),
                        id(rs, "serial_id"), rs.getString("serial_number"), rs.getString("sku"), rs.getString("product_name"),
                        id(rs, "order_id"), rs.getString("order_code"), rs.getObject("start_date", LocalDate.class),
                        rs.getObject("end_date", LocalDate.class), WarrantyStatus.valueOf(rs.getString("status")), List.of()));
    }

    public Map<UUID, List<String>> imeis(Collection<UUID> serialIds) {
        if (serialIds.isEmpty()) return Map.of();
        Map<UUID, List<String>> result = new HashMap<>();
        jdbc.query("SELECT serial_id, imei_number FROM imei WHERE serial_id IN (:ids) ORDER BY serial_id, imei_number",
                new MapSqlParameterSource("ids", serialIds), (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        result.computeIfAbsent(id(rs, "serial_id"), ignored -> new ArrayList<>()).add(rs.getString("imei_number")));
        return result;
    }

    public long countInstallments(UUID customerId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM installment_payments i JOIN payments p ON p.payment_id = i.payment_id
                JOIN orders o ON o.order_id = p.order_id WHERE o.customer_id = :id
                """, page(customerId, 0, 1), Long.class);
    }

    public List<CustomerInstallmentResponse> installments(UUID customerId, int page, int size) {
        return jdbc.query("""
                SELECT i.installment_id, o.order_id, o.order_code, o.order_date, o.status AS order_status,
                       pr.provider_name, i.total_amount, i.down_payment, i.remaining_amount, i.term_months, i.status
                FROM installment_payments i JOIN payments p ON p.payment_id = i.payment_id
                JOIN orders o ON o.order_id = p.order_id JOIN installment_providers pr ON pr.provider_id = i.provider_id
                WHERE o.customer_id = :id ORDER BY o.order_date DESC, i.installment_id DESC LIMIT :size OFFSET :offset
                """, page(customerId, page, size), (rs, row) -> new CustomerInstallmentResponse(id(rs, "installment_id"),
                        id(rs, "order_id"), rs.getString("order_code"), date(rs, "order_date"),
                        OrderStatus.valueOf(rs.getString("order_status")), rs.getString("provider_name"),
                        rs.getBigDecimal("total_amount"), rs.getBigDecimal("down_payment"), rs.getBigDecimal("remaining_amount"),
                        rs.getInt("term_months"), InstallmentStatus.valueOf(rs.getString("status"))));
    }
}
