package com.example.backend.category;

import com.example.backend.category.entity.Category;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class category_seed_data implements CommandLineRunner {
    private final CategoryRepository categoryRepository;

    public category_seed_data(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (categoryRepository.count() == 0) {
            Category electronics = new Category();
            electronics.setCategoryName("Điện tử");
            categoryRepository.save(electronics);
            System.out.println("Đã tạo Seed Data tự động!");
        }
    }
}
