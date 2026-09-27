package com.example.backend.product.repository;

import com.example.backend.product.entity.ProductStatus;
import com.example.backend.product.entity.Product;
import com.example.backend.product.entity.ProductVariant;
import com.example.backend.product.entity.ProductVariantStatus;
import com.example.backend.promotion.entity.PromotionProduct;
import com.example.backend.promotion.entity.PromotionStatus;
import com.example.backend.promotion.entity.DiscountType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> onSale(OffsetDateTime now) {
        return (root, query, builder) -> {
            var eligible = query.subquery(Integer.class);
            var association = eligible.from(PromotionProduct.class);
            var variant = eligible.from(ProductVariant.class);
            var promotion = association.join("promotion");
            // Percentage must produce a positive per-unit discount after HALF_UP to 2 decimals.
            var positiveDiscount = builder.or(
                    builder.equal(promotion.get("discountType"), DiscountType.FIXED_AMOUNT),
                    builder.and(builder.equal(promotion.get("discountType"), DiscountType.PERCENTAGE),
                            builder.ge(builder.prod(variant.<BigDecimal>get("price"),
                                    promotion.<BigDecimal>get("discountValue")), new BigDecimal("0.5")))
            );
            eligible.select(builder.literal(1)).where(
                    builder.equal(association.get("product"), root), builder.equal(variant.get("product"), root),
                    builder.equal(variant.get("status"), ProductVariantStatus.ACTIVE),
                    builder.greaterThan(variant.get("price"), BigDecimal.ZERO),
                    builder.equal(promotion.get("status"), PromotionStatus.ACTIVE),
                    builder.lessThanOrEqualTo(promotion.get("startDate"), now),
                    builder.greaterThanOrEqualTo(promotion.get("endDate"), now),
                    builder.greaterThan(promotion.get("discountValue"), BigDecimal.ZERO), positiveDiscount
            );
            return builder.and(builder.equal(root.get("status"), ProductStatus.ACTIVE), builder.exists(eligible));
        };
    }

    public static Specification<Product> withFilters(String keyword, UUID categoryId, UUID brandId, ProductStatus status) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null) {
                String escaped = keyword.toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                String pattern = "%" + escaped + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("productName")), pattern, '\\'),
                        builder.like(builder.lower(root.get("description")), pattern, '\\')
                ));
            }
            if (categoryId != null) {
                predicates.add(builder.equal(root.get("category").get("categoryId"), categoryId));
            }
            if (brandId != null) {
                predicates.add(builder.equal(root.get("brand").get("brandId"), brandId));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
