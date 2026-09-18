package com.example.backend.product.seed;

import com.example.backend.product.entity.ProductStatus;
import com.example.backend.category.CategoryRepository;
import com.example.backend.category.entity.Category;
import com.example.backend.product.BrandRepository;
import com.example.backend.product.ProductRepository;
import com.example.backend.product.entity.Brand;
import com.example.backend.product.entity.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(30)
@Profile("dev")
@ConditionalOnProperty(name = "app.seed.product.enabled", havingValue = "true")
public class ProductSeedData implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductSeedData.class);
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    public ProductSeedData(ProductRepository productRepository, CategoryRepository categoryRepository,
                           BrandRepository brandRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Category phones = categoryRepository
                .findFirstByCategoryNameAndParent_CategoryNameAndParent_ParentIsNullOrderByCategoryIdAsc(
                        "Điện thoại", "Điện thoại & Máy tính bảng").orElse(null);
        Category laptops = categoryRepository
                .findFirstByCategoryNameAndParent_CategoryNameAndParent_ParentIsNullOrderByCategoryIdAsc(
                        "Laptop", "Máy tính & Laptop").orElse(null);
        Map<String, Brand> brands = new LinkedHashMap<>();
        for (String name : new String[]{"Apple", "Samsung", "Xiaomi", "Dell", "ASUS"}) {
            brands.put(name, brandRepository.findByBrandName(name).orElse(null));
        }
        if (phones == null || laptops == null || brands.containsValue(null)) {
            log.warn("Product seed skipped: required Category/Brand seed data is missing. "
                    + "Enable app.seed.category.enabled and app.seed.brand.enabled in dev first");
            return;
        }

        int created = 0;
        created += createIfMissing("iPhone 15", "Điện thoại Apple dành cho công việc và giải trí", phones, brands.get("Apple"));
        created += createIfMissing("Samsung Galaxy S24", "Điện thoại Samsung Galaxy dòng S", phones, brands.get("Samsung"));
        created += createIfMissing("Xiaomi Redmi Note 13", "Điện thoại Xiaomi Redmi dùng hằng ngày", phones, brands.get("Xiaomi"));
        created += createIfMissing("MacBook Air M2", "Laptop Apple dành cho học tập và văn phòng", laptops, brands.get("Apple"));
        created += createIfMissing("Dell Inspiron 15", "Laptop Dell dành cho học tập và làm việc", laptops, brands.get("Dell"));
        created += createIfMissing("ASUS Vivobook 15", "Laptop ASUS phục vụ học tập và giải trí", laptops, brands.get("ASUS"));
        productRepository.flush();
        log.info("Development product seed finished: {} new products", created);
    }

    private int createIfMissing(String name, String description, Category category, Brand brand) {
        if (productRepository.existsByProductNameAndCategory_CategoryIdAndBrand_BrandId(
                name, category.getCategoryId(), brand.getBrandId())) {
            return 0;
        }
        Product product = new Product();
        product.setProductName(name);
        product.setDescription(description);
        product.setCategory(category);
        product.setBrand(brand);
        product.setStatus(ProductStatus.ACTIVE);
        productRepository.save(product);
        return 1;
    }
}
