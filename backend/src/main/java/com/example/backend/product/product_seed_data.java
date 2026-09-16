package com.example.backend.product;

import com.example.backend.product.entity.Product;
import com.example.backend.category.entity.Category;
import com.example.backend.category.CategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

import java.util.List;

@Component
@Order(2)
public class product_seed_data implements CommandLineRunner {
    
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public product_seed_data(
            ProductRepository productRepository,
            CategoryRepository categoryRepository
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        // if (productRepository.count() == 0) {
        //     List<Category> categories = categoryRepository.findAll();
        //     if (!categories.isEmpty()) {
        //         Category firstCategory = categories.get(0);
        //         Product product1 = new Product();
        //         product1.setProductName("Laptop Dell XPS 15");
        //         product1.setDescription("Laptop văn phòng, chip Intel Core i5");
        //         product1.setCategory(firstCategory);
        //         Product product2 = new Product();
        //         product2.setProductName("MacBook Pro M3");
        //         product2.setDescription("Laptop chuyên đồ họa, chip Apple M3");
        //         product2.setCategory(firstCategory);
        //         productRepository.saveAll(List.of(product1, product2));
        //         System.out.println("Đã tạo Seed Data tự động!");
        //     }
        // }
    }
}
