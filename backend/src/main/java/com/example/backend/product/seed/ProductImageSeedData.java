package com.example.backend.product.seed;

import com.example.backend.product.ProductRepository;
import com.example.backend.product.dto.ProductImageRequest;
import com.example.backend.product.entity.Product;
import com.example.backend.product.repository.ProductImageRepository;
import com.example.backend.product.service.ProductImageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(50)
@Profile("dev")
@ConditionalOnProperty(name = "app.seed.product-image.enabled", havingValue = "true")
public class ProductImageSeedData implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductImageSeedData.class);
    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final ProductImageService imageService;

    public ProductImageSeedData(ProductRepository productRepository, ProductImageRepository imageRepository,
                                ProductImageService imageService) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.imageService = imageService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int created = 0;
        created += seedProduct("iPhone 15", "Apple", "Điện thoại", "iphone-15");
        created += seedProduct("Samsung Galaxy S24", "Samsung", "Điện thoại", "samsung-galaxy-s24");
        created += seedProduct("Xiaomi Redmi Note 13", "Xiaomi", "Điện thoại", "xiaomi-redmi-note-13");
        created += seedProduct("MacBook Air M2", "Apple", "Laptop", "macbook-air-m2");
        created += seedProduct("Dell Inspiron 15", "Dell", "Laptop", "dell-inspiron-15");
        created += seedProduct("ASUS Vivobook 15", "ASUS", "Laptop", "asus-vivobook-15");
        log.info("Development product image seed finished: {} new image paths", created);
    }

    private int seedProduct(String productName, String brandName, String categoryName, String slug) {
        Product candidate = productRepository
                .findFirstByProductNameAndBrand_BrandNameAndCategory_CategoryNameAndCategory_Parent_CategoryNameAndCategory_Parent_ParentIsNullOrderByProductIdAsc(
                        productName, brandName, categoryName,
                        "Điện thoại".equals(categoryName) ? "Điện thoại & Máy tính bảng" : "Máy tính & Laptop"
                ).orElse(null);
        if (candidate == null) {
            log.warn("Image seed skipped: product '{}' is missing. "
                    + "Enable app.seed.product.enabled and its Category/Brand seeds in dev first", productName);
            return 0;
        }
        // Use the same product lock as CRUD before checking existing URLs/primary flags.
        Product product = productRepository.findByIdForUpdate(candidate.getProductId()).orElse(null);
        if (product == null) {
            return 0;
        }
        String prefix = "/images/products/" + slug + "/";
        int created = createIfMissing(product, prefix + "main.jpg",
                !imageRepository.existsByProduct_ProductIdAndPrimaryTrue(product.getProductId()));
        return created + createIfMissing(product, prefix + "detail.jpg", false);
    }

    private int createIfMissing(Product product, String imageUrl, boolean primary) {
        if (imageRepository.existsByProduct_ProductIdAndImageUrl(product.getProductId(), imageUrl)) {
            return 0;
        }
        ProductImageRequest request = new ProductImageRequest();
        request.setImageUrl(imageUrl);
        request.setIsPrimary(primary);
        imageService.createImage(product.getProductId(), request);
        return 1;
    }
}
