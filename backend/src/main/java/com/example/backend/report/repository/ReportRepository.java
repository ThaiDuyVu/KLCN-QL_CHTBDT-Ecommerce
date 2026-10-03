package com.example.backend.report.repository;

import com.example.backend.report.dto.*;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Aggregate-only queries: no entity hydration, row locks or writes. */
@Repository
public class ReportRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final ZoneId zone = ZoneId.systemDefault();

    public ReportRepository(DataSource dataSource) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        // Bounds each report query without changing the shared application JDBC template.
        template.setQueryTimeout(5);
        this.jdbc = new NamedParameterJdbcTemplate(template);
    }

    private MapSqlParameterSource parameters(ReportFilter filter) {
        return new MapSqlParameterSource()
                .addValue("from", java.sql.Timestamp.from(filter.getFrom().atStartOfDay(zone).toInstant()))
                .addValue("until", java.sql.Timestamp.from(filter.getTo().plusDays(1).atStartOfDay(zone).toInstant()))
                .addValue("warehouseId", filter.getWarehouseId())
                .addValue("timezone", zone.getId());
    }

    // Build only trusted predicates; every external value remains a bound parameter.
    private String period(String alias, String date, ReportFilter filter) {
        return alias + "." + date + " >= :from AND " + alias + "." + date + " < :until"
                + warehouse(alias, filter);
    }

    private String warehouse(String alias, ReportFilter filter) {
        return filter.getWarehouseId() == null ? "" : " AND " + alias + ".warehouse_id = :warehouseId";
    }

    private UUID id(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    public boolean warehouseExists(UUID warehouseId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM warehouses WHERE warehouse_id = :id)",
                new MapSqlParameterSource("id", warehouseId), Boolean.class));
    }

    public ReportSummaryResponse summary(ReportFilter filter) {
        String sql = """
                SELECT COALESCE(SUM(o.total_amount) FILTER (WHERE o.status = 'DELIVERED'), 0) AS revenue,
                       COALESCE(SUM(o.discount_amount) FILTER (WHERE o.status = 'DELIVERED'), 0) AS discount,
                       COUNT(*) AS total_orders,
                       COUNT(*) FILTER (WHERE o.status = 'DELIVERED') AS completed,
                       COUNT(*) FILTER (WHERE o.status = 'CANCELLED') AS cancelled,
                       COALESCE(AVG(o.total_amount) FILTER (WHERE o.status = 'DELIVERED'), 0) AS average
                FROM orders o WHERE
                """ + period("o", "order_date", filter);
        // Separate aggregate prevents Order totals being multiplied by the item join.
        BigDecimal profit = jdbc.queryForObject("""
                SELECT COALESCE(SUM((i.final_unit_price - i.cost_price) * i.quantity), 0)
                FROM order_items i JOIN orders o ON o.order_id = i.order_id
                WHERE o.status = 'DELIVERED' AND
                """ + period("o", "order_date", filter), parameters(filter), BigDecimal.class);
        return jdbc.queryForObject(sql, parameters(filter), (rs, row) -> new ReportSummaryResponse(
                filter.getFrom(), filter.getTo(), zone.getId(), filter.getWarehouseId(),
                rs.getBigDecimal("revenue"), profit, rs.getBigDecimal("discount"),
                rs.getLong("total_orders"), rs.getLong("completed"), rs.getLong("cancelled"),
                rs.getBigDecimal("average")));
    }

    public List<RevenuePoint> revenue(ReportFilter filter, boolean monthly) {
        String bucket = monthly ? "month" : "day";
        String sql = "SELECT date_trunc('" + bucket + "', o.order_date AT TIME ZONE :timezone)::date AS bucket, "
                + "SUM(o.total_amount) AS revenue, COUNT(*) AS completed FROM orders o "
                + "WHERE o.status = 'DELIVERED' AND " + period("o", "order_date", filter)
                + " GROUP BY bucket ORDER BY bucket LIMIT 91";
        return jdbc.query(sql, parameters(filter), (rs, row) -> new RevenuePoint(
                rs.getObject("bucket", LocalDate.class), rs.getBigDecimal("revenue"), rs.getLong("completed")));
    }

    public List<OrderStatusRow> orderStatuses(ReportFilter filter) {
        return jdbc.query("SELECT o.status, COUNT(*) AS count FROM orders o WHERE "
                + period("o", "order_date", filter) + " GROUP BY o.status ORDER BY o.status LIMIT 20",
                parameters(filter), (rs, row) -> new OrderStatusRow(rs.getString("status"), rs.getLong("count")));
    }

    public List<TopProductRow> topProducts(ReportFilter filter, int limit) {
        return jdbc.query("""
                SELECT p.product_id, p.product_name, SUM(i.quantity) AS quantity,
                       SUM(i.final_unit_price * i.quantity) AS revenue
                FROM order_items i JOIN orders o ON o.order_id = i.order_id
                JOIN product_variants v ON v.variant_id = i.variant_id
                JOIN products p ON p.product_id = v.product_id
                WHERE o.status = 'DELIVERED' AND
                """ + period("o", "order_date", filter)
                + " GROUP BY p.product_id, p.product_name ORDER BY quantity DESC, p.product_id LIMIT :limit",
                parameters(filter).addValue("limit", limit), (rs, row) -> new TopProductRow(
                        id(rs, "product_id"), rs.getString("product_name"), rs.getLong("quantity"), rs.getBigDecimal("revenue")));
    }

    public List<WarehouseReportRow> warehouses(ReportFilter filter) {
        return jdbc.query("""
                SELECT w.warehouse_id, w.warehouse_name, COUNT(*) AS count, SUM(o.total_amount) AS amount
                FROM orders o JOIN warehouses w ON w.warehouse_id = o.warehouse_id
                WHERE o.status = 'DELIVERED' AND
                """ + period("o", "order_date", filter)
                + " GROUP BY w.warehouse_id, w.warehouse_name ORDER BY amount DESC, w.warehouse_id LIMIT 100",
                parameters(filter), (rs, row) -> warehouseRow(rs));
    }

    private WarehouseReportRow warehouseRow(ResultSet rs) throws SQLException {
        return new WarehouseReportRow(id(rs, "warehouse_id"), rs.getString("warehouse_name"),
                rs.getLong("count"), rs.getBigDecimal("amount"));
    }

    public List<PaymentReportRow> payments(ReportFilter filter) {
        // Cohort is orders placed in the selected period, not paymentDate or paid-only revenue.
        return jdbc.query("""
                SELECT p.payment_method, p.status, COUNT(*) AS count, SUM(p.amount) AS amount
                FROM payments p JOIN orders o ON o.order_id = p.order_id WHERE
                """ + period("o", "order_date", filter)
                + " GROUP BY p.payment_method, p.status ORDER BY p.payment_method, p.status LIMIT 100",
                parameters(filter), (rs, row) -> new PaymentReportRow(rs.getString("payment_method"),
                        rs.getString("status"), rs.getLong("count"), rs.getBigDecimal("amount")));
    }

    public GoodsReceiptReportResponse goodsReceipts(ReportFilter filter) {
        String where = "g.status = 'CONFIRMED' AND " + period("g", "receipt_date", filter);
        List<WarehouseReportRow> groups = jdbc.query("""
                SELECT w.warehouse_id, w.warehouse_name, COUNT(*) AS count, SUM(g.total_amount) AS amount
                FROM goods_receipts g JOIN warehouses w ON w.warehouse_id = g.warehouse_id WHERE
                """ + where + " GROUP BY w.warehouse_id, w.warehouse_name ORDER BY amount DESC, w.warehouse_id LIMIT 100",
                parameters(filter), (rs, row) -> warehouseRow(rs));
        return jdbc.queryForObject("SELECT COUNT(*) AS count, COALESCE(SUM(g.total_amount), 0) AS amount "
                + "FROM goods_receipts g WHERE " + where, parameters(filter),
                (rs, row) -> new GoodsReceiptReportResponse(rs.getLong("count"), rs.getBigDecimal("amount"), groups));
    }

    public List<LowStockRow> lowStock(ReportFilter filter, int threshold, int limit) {
        return jdbc.query("""
                SELECT i.inventory_id, i.warehouse_id, w.warehouse_name, i.variant_id, v.sku, p.product_name,
                       i.quantity, i.reserved_quantity, i.quantity - i.reserved_quantity AS available
                FROM inventory i JOIN warehouses w ON w.warehouse_id = i.warehouse_id
                JOIN product_variants v ON v.variant_id = i.variant_id
                JOIN products p ON p.product_id = v.product_id
                WHERE i.quantity - i.reserved_quantity <= :threshold
                """ + warehouse("i", filter) + " ORDER BY available, i.warehouse_id, i.variant_id LIMIT :limit",
                parameters(filter).addValue("threshold", threshold).addValue("limit", limit),
                (rs, row) -> new LowStockRow(id(rs, "inventory_id"), id(rs, "warehouse_id"), rs.getString("warehouse_name"),
                        id(rs, "variant_id"), rs.getString("sku"), rs.getString("product_name"),
                        rs.getInt("quantity"), rs.getInt("reserved_quantity"), rs.getInt("available")));
    }
}
