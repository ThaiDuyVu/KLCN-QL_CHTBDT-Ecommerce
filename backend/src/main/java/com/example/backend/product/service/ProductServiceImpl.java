package com.example.backend.product.service;

import com.example.backend.product.entity.Product;
import com.example.backend.product.ProductRepository;
import com.example.backend.product.dto.ProductRequest;
import com.example.backend.product.dto.ProductResponse;
import com.example.backend.category.entity.Category;
import com.example.backend.category.CategoryRepository; // Cần gọi thủ kho Category để móc nối dữ liệu
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    public ProductResponse createProduct(ProductRequest request) {
        Product product = new Product();
        product.setProductName(request.productName());
        product.setDescription(request.description());
        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục này!"));
            product.setCategory(category);
        }
        Product saved = productRepository.save(product);
        return mapToResponse(saved);
    }
    @Override
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    private ProductResponse mapToResponse(Product product) {
        UUID catId = (product.getCategory() != null) ? product.getCategory().getCategoryId() : null;
        return new ProductResponse(
                product.getProductId(),
                product.getProductName(),
                product.getDescription(),
                catId
        );
    }
}