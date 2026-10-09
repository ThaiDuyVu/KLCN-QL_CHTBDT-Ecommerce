package com.example.backend.product.service;

import com.example.backend.product.dto.ProductSearchCandidate;
import com.example.backend.product.exception.InvalidProductPaginationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductAdvancedSearchService {
    private final NamedParameterJdbcTemplate jdbc;

    public ProductAdvancedSearchService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Invalid or missing size strings never satisfy minimum-capacity filters. */
    private static String capacityGb(String column) {
        return "(CASE WHEN upper(trim(" + column + ")) ~ '^[0-9]+(\\.[0-9]+)? *(GB|TB)$' "
                + "THEN regexp_replace(upper(trim(" + column + ")), '[^0-9.]', '', 'g')::numeric "
                + "* CASE WHEN upper(trim(" + column + ")) LIKE '%TB' THEN 1024 ELSE 1 END END)";
    }

    public List<ProductSearchCandidate> search(
            String category, List<String> brands, BigDecimal minPrice, BigDecimal maxPrice,
            Integer minRamGb, Integer minStorageGb, List<String> cpuKeywords,
            boolean inStockOnly, UUID warehouseId, int limit, int offset
    ) {
        if (limit < 1 || limit > 100 || offset < 0 || inStockOnly && warehouseId == null
                || minPrice != null && minPrice.signum() < 0
                || maxPrice != null && maxPrice.signum() < 0
                || minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0
                || minRamGb != null && minRamGb < 1 || minStorageGb != null && minStorageGb < 1) {
            throw new InvalidProductPaginationException("Bộ lọc tìm sản phẩm không hợp lệ");
        }
        var args = new MapSqlParameterSource().addValue("limit", limit).addValue("offset", offset);
        var sql = new StringBuilder("""
                SELECT p.product_id, v.variant_id
                FROM products p
                JOIN product_variants v ON v.product_id = p.product_id
                JOIN categories c ON c.category_id = p.category_id
                JOIN brands b ON b.brand_id = p.brand_id
                LEFT JOIN LATERAL (
                    SELECT pr.discount_type, pr.discount_value
                    FROM promotion_products pp
                    JOIN promotions pr ON pr.promotion_id = pp.promotion_id
                    WHERE pp.product_id = p.product_id AND pr.status = 'ACTIVE'
                      AND pr.start_date <= CURRENT_TIMESTAMP AND pr.end_date >= CURRENT_TIMESTAMP
                    ORDER BY pr.promotion_id LIMIT 1
                ) promo ON true
                WHERE p.status = 'ACTIVE' AND v.status = 'ACTIVE'
                """);
        if (category != null && !category.isBlank()) {
            sql.append(" AND lower(c.category_name) = lower(:category)");
            args.addValue("category", category.trim());
        }
        var names = brands == null ? List.<String>of() : brands.stream().map(String::trim)
                .filter(name -> !name.isEmpty()).map(String::toLowerCase).distinct().toList();
        if (!names.isEmpty()) {
            sql.append(" AND lower(b.brand_name) IN (:brands)");
            args.addValue("brands", names);
        }
        String effectivePrice = "(CASE WHEN promo.discount_type = 'PERCENTAGE' THEN "
                + "v.price - round(v.price * promo.discount_value / 100, 2) "
                + "WHEN promo.discount_type = 'FIXED_AMOUNT' THEN greatest(0, v.price - promo.discount_value) "
                + "ELSE v.price END)";
        if (minPrice != null) {
            sql.append(" AND ").append(effectivePrice).append(" >= :minPrice");
            args.addValue("minPrice", minPrice);
        }
        if (maxPrice != null) {
            sql.append(" AND ").append(effectivePrice).append(" <= :maxPrice");
            args.addValue("maxPrice", maxPrice);
        }
        if (minRamGb != null) {
            sql.append(" AND ").append(capacityGb("v.ram")).append(" >= :minRamGb");
            args.addValue("minRamGb", minRamGb);
        }
        if (minStorageGb != null) {
            sql.append(" AND ").append(capacityGb("v.storage")).append(" >= :minStorageGb");
            args.addValue("minStorageGb", minStorageGb);
        }
        if (cpuKeywords != null) {
            int i = 0;
            for (String keyword : cpuKeywords) {
                if (keyword == null || keyword.isBlank()) continue;
                String param = "cpu" + i++;
                sql.append(" AND EXISTS (SELECT 1 FROM specifications s WHERE s.product_id = p.product_id ")
                        .append("AND lower(s.spec_key) IN ('cpu', 'processor', 'vi xử lý', 'bộ xử lý') ")
                        .append("AND lower(s.spec_value) LIKE :").append(param).append(" ESCAPE '\\')");
                args.addValue(param, "%" + keyword.trim().toLowerCase()
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
            }
        }
        if (inStockOnly) {
            sql.append(" AND EXISTS (SELECT 1 FROM inventory i WHERE i.variant_id = v.variant_id ")
                    .append("AND i.warehouse_id = :warehouseId AND i.quantity > i.reserved_quantity)");
            args.addValue("warehouseId", warehouseId);
        }
        sql.append(" ORDER BY p.product_id, v.variant_id LIMIT :limit OFFSET :offset");
        return jdbc.query(sql.toString(), args, (row, number) -> new ProductSearchCandidate(
                row.getObject("product_id", UUID.class), row.getObject("variant_id", UUID.class)));
    }
}
