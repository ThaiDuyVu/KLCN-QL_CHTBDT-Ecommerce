package com.example.backend.category.seed;

import com.example.backend.category.entity.CategoryStatus;
import com.example.backend.category.CategoryRepository;
import com.example.backend.category.entity.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(10)
@Profile("dev")
@ConditionalOnProperty(
        name = "app.seed.category.enabled",
        havingValue = "true"
)
public class CategorySeedData implements CommandLineRunner {

    private static final Logger log =
            LoggerFactory.getLogger(CategorySeedData.class);

    private final CategoryRepository categoryRepository;

    public CategorySeedData(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {

        Category mobileDevices = createCategory(
                "Điện thoại & Máy tính bảng",
                "Các thiết bị di động và máy tính bảng",
                null
        );

        createCategory(
                "Điện thoại",
                "Điện thoại thông minh và điện thoại di động",
                mobileDevices
        );

        createCategory(
                "Máy tính bảng",
                "Máy tính bảng và thiết bị tablet",
                mobileDevices
        );


        Category computers = createCategory(
                "Máy tính & Laptop",
                "Máy tính cá nhân, laptop và thiết bị máy tính",
                null
        );

        createCategory(
                "Laptop",
                "Laptop phục vụ học tập, văn phòng và gaming",
                computers
        );

        createCategory(
                "Máy tính để bàn",
                "Máy tính để bàn và PC",
                computers
        );


        Category accessories = createCategory(
                "Phụ kiện",
                "Phụ kiện dành cho thiết bị điện tử",
                null
        );

        createCategory(
                "Tai nghe",
                "Tai nghe có dây, không dây và gaming",
                accessories
        );

        createCategory(
                "Sạc & Cáp",
                "Củ sạc, cáp sạc và phụ kiện kết nối",
                accessories
        );

        createCategory(
                "Pin dự phòng",
                "Pin sạc dự phòng cho thiết bị di động",
                accessories
        );

        categoryRepository.flush();
        log.info("Development category seed finished: ensured 3 parent categories and 7 child categories");
    }

    private Category createCategory(
            String name,
            String description,
            Category parent
    ) {
        // Match the parent as well as the name; unrelated or partial data must not block the seed.
        Category existing = parent == null
                ? categoryRepository.findFirstByCategoryNameAndParentIsNullOrderByCategoryIdAsc(name).orElse(null)
                : categoryRepository.findFirstByCategoryNameAndParent_CategoryIdOrderByCategoryIdAsc(
                        name, parent.getCategoryId()).orElse(null);
        if (existing != null) {
            return existing;
        }
        Category category = new Category();

        category.setCategoryName(name);
        category.setDescription(description);
        category.setParent(parent);
        category.setStatus(CategoryStatus.ACTIVE);

        return categoryRepository.save(category);
    }
}
