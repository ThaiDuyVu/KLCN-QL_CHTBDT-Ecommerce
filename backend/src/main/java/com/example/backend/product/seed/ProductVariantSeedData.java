package com.example.backend.product.seed;

import com.example.backend.common.seed.DevSeedData;
import com.example.backend.product.entity.ProductVariantStatus;
import com.example.backend.product.entity.ProductTrackingType;
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
        created += createIfMissing(DevSeedData.IPHONE_15_BLACK_128, "iPhone 15", "Apple", "Điện thoại",
                "Đen", "128GB", "6GB", "17990000", "15500000", ProductTrackingType.IMEI);
        created += createIfMissing(DevSeedData.IPHONE_15_BLUE_256, "iPhone 15", "Apple", "Điện thoại",
                "Xanh dương", "256GB", "6GB", "20990000", "18100000", ProductTrackingType.IMEI, 12);
        created += createIfMissing(DevSeedData.GALAXY_S24_GRAY_256, "Samsung Galaxy S24", "Samsung", "Điện thoại",
                "Xám", "256GB", "8GB", "18990000", "16100000", ProductTrackingType.IMEI, 12);
        created += createIfMissing(DevSeedData.GALAXY_S24_BLACK_256, "Samsung Galaxy S24", "Samsung", "Điện thoại",
                "Đen", "256GB", "8GB", "18990000", "16100000", ProductTrackingType.IMEI, 12);
        created += createIfMissing(DevSeedData.REDMI_NOTE_13_BLACK_128, "Xiaomi Redmi Note 13", "Xiaomi", "Điện thoại",
                "Đen", "128GB", "6GB", "4490000", "3700000", ProductTrackingType.IMEI, 12);
        created += createIfMissing(DevSeedData.MACBOOK_AIR_M2_256, "MacBook Air M2", "Apple", "Laptop",
                "Bạc", "256GB", "8GB", "22990000", "19900000", ProductTrackingType.SERIAL, 12);
        created += createIfMissing(DevSeedData.DELL_INSPIRON_15_512, "Dell Inspiron 15", "Dell", "Laptop",
                "Bạc", "512GB", "16GB", "15990000", "13500000", ProductTrackingType.SERIAL, 24);
        created += createIfMissing(DevSeedData.ASUS_VIVOBOOK_15_512, "ASUS Vivobook 15", "ASUS", "Laptop",
                "Xanh", "512GB", "8GB", "12990000", "10800000", ProductTrackingType.SERIAL, 24);

        created += createIfMissing("DEV-IP16-BLK-128", "iPhone 16", "Apple", "Điện thoại", "Đen", "128GB", "8GB", "21990000", "19000000", ProductTrackingType.IMEI, 12);
        created += createIfMissing("DEV-IP16-BLU-256", "iPhone 16", "Apple", "Điện thoại", "Xanh lưu ly", "256GB", "8GB", "24990000", "21800000", ProductTrackingType.IMEI, 12);
        created += createIfMissing("DEV-IP16-PNK-512", "iPhone 16", "Apple", "Điện thoại", "Hồng", "512GB", "8GB", "30990000", "27200000", ProductTrackingType.IMEI, 12);
        created += createIfMissing("DEV-IP14-BLK-128", "iPhone 14", "Apple", "Điện thoại", "Đen", "128GB", "6GB", "15990000", "13800000", ProductTrackingType.IMEI, 12);
        created += createIfMissing("DEV-IP14-BLU-256", "iPhone 14", "Apple", "Điện thoại", "Xanh dương", "256GB", "6GB", "18990000", "16500000", ProductTrackingType.IMEI, 12);
        created += createIfMissing("DEV-IP14-RED-256", "iPhone 14", "Apple", "Điện thoại", "Đỏ", "256GB", "6GB", "18990000", "16500000", ProductTrackingType.IMEI, 12);
        created += createIfMissing("DEV-A55-NVY-128", "Samsung Galaxy A55", "Samsung", "Điện thoại", "Xanh đen", "128GB", "8GB", "8490000", "7100000", ProductTrackingType.IMEI, 12);
        created += createIfMissing("DEV-A55-LIL-256", "Samsung Galaxy A55", "Samsung", "Điện thoại", "Tím", "256GB", "8GB", "9490000", "7950000", ProductTrackingType.IMEI, 12);
        created += createIfMissing("DEV-A55-ICE-256", "Samsung Galaxy A55", "Samsung", "Điện thoại", "Xanh băng", "256GB", "12GB", "10490000", "8800000", ProductTrackingType.IMEI, 12);
        created += createIfMissing("DEV-ZF6-GRY-256", "Samsung Galaxy Z Flip6", "Samsung", "Điện thoại", "Xám", "256GB", "12GB", "24990000", "21600000", ProductTrackingType.IMEI, 12);
        created += createIfMissing("DEV-ZF6-YLW-256", "Samsung Galaxy Z Flip6", "Samsung", "Điện thoại", "Vàng", "256GB", "12GB", "24990000", "21600000", ProductTrackingType.IMEI, 12);
        created += createIfMissing("DEV-ZF6-BLU-512", "Samsung Galaxy Z Flip6", "Samsung", "Điện thoại", "Xanh", "512GB", "12GB", "28990000", "25000000", ProductTrackingType.IMEI, 12);
        created += createIfMissing("DEV-X14TP-BLK-256", "Xiaomi 14T Pro", "Xiaomi", "Điện thoại", "Đen", "256GB", "12GB", "16990000", "14300000", ProductTrackingType.IMEI, 12);
        created += createIfMissing("DEV-X14TP-BLU-512", "Xiaomi 14T Pro", "Xiaomi", "Điện thoại", "Xanh", "512GB", "12GB", "18990000", "16000000", ProductTrackingType.IMEI, 12);
        created += createIfMissing("DEV-X14TP-GRY-512", "Xiaomi 14T Pro", "Xiaomi", "Điện thoại", "Xám", "512GB", "16GB", "19990000", "16800000", ProductTrackingType.IMEI, 12);
        created += createIfMissing("DEV-S23-BLK-128", "Samsung Galaxy S23", "Samsung", "Điện thoại", "Đen", "128GB", "8GB", "13990000", "11800000", ProductTrackingType.IMEI, 12);
        created += createIfMissing("DEV-S23-GRN-256", "Samsung Galaxy S23", "Samsung", "Điện thoại", "Xanh lá", "256GB", "8GB", "15490000", "13000000", ProductTrackingType.IMEI, 12);
        created += createIfMissing("DEV-S23-CRM-256", "Samsung Galaxy S23", "Samsung", "Điện thoại", "Kem", "256GB", "8GB", "15490000", "13000000", ProductTrackingType.IMEI, 12);
        created += createIfMissing("DEV-X15-BLK-256", "Xiaomi 15", "Xiaomi", "Điện thoại", "Đen", "256GB", "12GB", "18990000", "16000000", ProductTrackingType.IMEI, 12);
        created += createIfMissing("DEV-X15-WHT-512", "Xiaomi 15", "Xiaomi", "Điện thoại", "Trắng", "512GB", "12GB", "20990000", "17700000", ProductTrackingType.IMEI, 12);
        created += createIfMissing("DEV-X15-GRN-512", "Xiaomi 15", "Xiaomi", "Điện thoại", "Xanh lá", "512GB", "16GB", "21990000", "18500000", ProductTrackingType.IMEI, 12);
        created += createIfMissing("DEV-MBP14-M3-SG-512", "MacBook Pro 14 M3", "Apple", "Laptop", "Xám không gian", "512GB", "8GB", "39990000", "35000000", ProductTrackingType.SERIAL, 12);
        created += createIfMissing("DEV-MBP14-M3-SL-512", "MacBook Pro 14 M3", "Apple", "Laptop", "Bạc", "512GB", "16GB", "44990000", "39500000", ProductTrackingType.SERIAL, 12);
        created += createIfMissing("DEV-MBP14-M3-SG-1TB", "MacBook Pro 14 M3", "Apple", "Laptop", "Xám không gian", "1TB", "16GB", "49990000", "44000000", ProductTrackingType.SERIAL, 12);
        created += createIfMissing("DEV-XPS13-SL-512", "Dell XPS 13", "Dell", "Laptop", "Bạc", "512GB", "16GB", "32990000", "28200000", ProductTrackingType.SERIAL, 24);
        created += createIfMissing("DEV-XPS13-GR-1TB", "Dell XPS 13", "Dell", "Laptop", "Xám", "1TB", "16GB", "36990000", "31700000", ProductTrackingType.SERIAL, 24);
        created += createIfMissing("DEV-XPS13-SL-1TB", "Dell XPS 13", "Dell", "Laptop", "Bạc", "1TB", "32GB", "41990000", "36000000", ProductTrackingType.SERIAL, 24);
        created += createIfMissing("DEV-ROGG14-GR-512", "ASUS ROG Zephyrus G14", "ASUS", "Laptop", "Xám", "512GB", "16GB", "39990000", "34200000", ProductTrackingType.SERIAL, 24);
        created += createIfMissing("DEV-ROGG14-WH-1TB", "ASUS ROG Zephyrus G14", "ASUS", "Laptop", "Trắng", "1TB", "32GB", "49990000", "43000000", ProductTrackingType.SERIAL, 24);
        created += createIfMissing("DEV-ROGG14-GR-1TB", "ASUS ROG Zephyrus G14", "ASUS", "Laptop", "Xám", "1TB", "32GB", "52990000", "45500000", ProductTrackingType.SERIAL, 24);
        log.info("Development product variant seed finished: {} new variants", created);
    }

    private int createIfMissing(String sku, String productName, String brandName, String categoryName,
                                String color, String storage, String ram, String price, String costPrice,
                                ProductTrackingType trackingType) {
        return createIfMissing(sku, productName, brandName, categoryName, color, storage, ram,
                price, costPrice, trackingType, 12);
    }

    private int createIfMissing(String sku, String productName, String brandName, String categoryName,
                                String color, String storage, String ram, String price, String costPrice,
                                ProductTrackingType trackingType, int warrantyMonths) {
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
        // Never overwrite variant business data or SKU assignments. Seeded rows created before
        // tracking_type existed may be upgraded from the migration default NONE.
        ProductVariant existing = variantRepository.findBySku(sku).orElse(null);
        if (existing != null) {
            if (!existing.getProduct().getProductId().equals(product.getProductId())) {
                log.warn("Variant seed skipped for SKU {}: already assigned to a different product", sku);
            } else {
                boolean changed = false;
                if (existing.getTrackingType() == ProductTrackingType.NONE) {
                    existing.setTrackingType(trackingType);
                    changed = true;
                } else if (existing.getTrackingType() != trackingType) {
                    log.warn("Variant seed kept trackingType {} for SKU {}; expected {}",
                            existing.getTrackingType(), sku, trackingType);
                }
                if (existing.getWarrantyMonths() == 0) {
                    existing.setWarrantyMonths(warrantyMonths);
                    changed = true;
                } else if (existing.getWarrantyMonths() != warrantyMonths) {
                    log.warn("Variant seed kept warrantyMonths {} for SKU {}; expected {}",
                            existing.getWarrantyMonths(), sku, warrantyMonths);
                }
                if (changed) variantRepository.save(existing);
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
        request.setTrackingType(trackingType);
        request.setWarrantyMonths(warrantyMonths);
        variantService.createVariant(request);
        return 1;
    }
}
