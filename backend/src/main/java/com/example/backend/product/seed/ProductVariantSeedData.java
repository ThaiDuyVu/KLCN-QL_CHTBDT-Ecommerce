package com.example.backend.product.seed;

import com.example.backend.product.entity.ProductVariantStatus;
import com.example.backend.product.repository.ProductRepository;
import com.example.backend.product.dto.ProductVariantRequest;
import com.example.backend.product.entity.Product;
import com.example.backend.product.entity.ProductVariant;
import com.example.backend.product.repository.ProductVariantRepository;
import com.example.backend.product.service.ProductVariantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@Order(40)
@Profile("dev")
@ConditionalOnProperty(name = "app.seed.product-variant.enabled", havingValue = "true")
public class ProductVariantSeedData implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductVariantSeedData.class);
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductVariantService variantService;

    public ProductVariantSeedData(ProductRepository productRepository, ProductVariantRepository variantRepository,
                                  ProductVariantService variantService) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.variantService = variantService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int created = 0;
        created += createIfMissing("DEV-IP15-BLK-128", "iPhone 15", "Apple", "Điện thoại",
                "Đen", "128GB", "6GB", "17990000", "15500000");
        created += createIfMissing("DEV-IP15-BLU-256", "iPhone 15", "Apple", "Điện thoại",
                "Xanh dương", "256GB", "6GB", "20990000", "18100000");
        created += createIfMissing("DEV-S24-GRY-256", "Samsung Galaxy S24", "Samsung", "Điện thoại",
                "Xám", "256GB", "8GB", "18990000", "16100000");
        created += createIfMissing("DEV-S24-BLK-256", "Samsung Galaxy S24", "Samsung", "Điện thoại",
                "Đen", "256GB", "8GB", "18990000", "16100000");
        created += createIfMissing("DEV-RN13-BLK-128", "Xiaomi Redmi Note 13", "Xiaomi", "Điện thoại",
                "Đen", "128GB", "6GB", "4490000", "3700000");
        created += createIfMissing("DEV-MBA-M2-256", "MacBook Air M2", "Apple", "Laptop",
                "Bạc", "256GB", "8GB", "22990000", "19900000");
        created += createIfMissing("DEV-DELL-I15-512", "Dell Inspiron 15", "Dell", "Laptop",
                "Bạc", "512GB", "16GB", "15990000", "13500000");
        created += createIfMissing("DEV-ASUS-V15-512", "ASUS Vivobook 15", "ASUS", "Laptop",
                "Xanh", "512GB", "8GB", "12990000", "10800000");
        log.info("Development product variant seed finished: {} new variants", created);
    }

    private int createIfMissing(String sku, String productName, String brandName, String categoryName,
                                String color, String storage, String ram, String price, String costPrice) {
        Product product = productRepository
                .findFirstByProductNameAndBrand_BrandNameAndCategory_CategoryNameAndCategory_Parent_CategoryNameAndCategory_Parent_ParentIsNullOrderByProductIdAsc(
                        productName, brandName, categoryName,
                        "Điện thoại".equals(categoryName) ? "Điện thoại & Máy tính bảng" : "Máy tính & Laptop"
                ).orElse(null);
        if (product == null) {
            log.warn("Variant seed skipped for SKU {}: required product '{}' is missing. "
                    + "Enable app.seed.product.enabled and its Category/Brand seeds in dev first", sku, productName);
            return 0;
        }
        // Never overwrite variants, prices or SKU assignments; disclose a mismatched dependency.
        ProductVariant existing = variantRepository.findBySku(sku).orElse(null);
        if (existing != null) {
            if (!existing.getProduct().getProductId().equals(product.getProductId())) {
                log.warn("Variant seed skipped for SKU {}: already assigned to a different product", sku);
            }
            return 0;
        }
        ProductVariantRequest request = new ProductVariantRequest();
        request.setProductId(product.getProductId());
        request.setSku(sku);
        request.setPrice(new BigDecimal(price));
        request.setCostPrice(new BigDecimal(costPrice));
        request.setColor(color);
        request.setStorage(storage);
        request.setRam(ram);
        request.setStatus(ProductVariantStatus.ACTIVE);
        variantService.createVariant(request);
        return 1;
    }
}
