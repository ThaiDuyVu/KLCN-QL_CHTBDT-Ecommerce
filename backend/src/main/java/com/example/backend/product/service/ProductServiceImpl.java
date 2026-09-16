package com.example.backend.product.service;

import com.example.backend.product.entity.Product;
import com.example.backend.product.entity.Brand;
import com.example.backend.category.entity.Category;
import com.example.backend.product.ProductRepository;
import com.example.backend.product.BrandRepository;
import com.example.backend.category.CategoryRepository;
import com.example.backend.product.dto.ProductRequest;
import com.example.backend.product.dto.ProductResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository; // Đã tiêm BrandRepository

    public ProductServiceImpl(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            BrandRepository brandRepository
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
    }

    @Override
    public ProductResponse createProduct(ProductRequest request) {
        // kiểm tra dữ liệu đầu vào
        if (request.categoryId() == null) {
            throw new RuntimeException("Lỗi: categoryId là bắt buộc!");
        }
        if (request.brandId() == null) {
            throw new RuntimeException("Lỗi: brandId là bắt buộc!");
        }
        
        Product product = new Product();
        product.setProductName(request.productName());
        product.setDescription(request.description());

        // xu ly status
        if (request.status() != null && !request.status().isEmpty()) {
            product.setStatus(request.status());
        } else {
            product.setStatus("ACTIVE");
        }

        // xu ly category
        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục (Category) này!"));
            product.setCategory(category);
        }

        // xu li brand
        if (request.brandId() != null) {
            Brand brand = brandRepository.findById(request.brandId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thương hiệu (Brand) này!"));
            product.setBrand(brand);
        } else {
            throw new RuntimeException("Thương hiệu (Brand) là bắt buộc!");
        }
//find category by id
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục (Category) này!"));
        product.setCategory(category);

        // find brand by id
        Brand brand = brandRepository.findById(request.brandId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thương hiệu (Brand) này!"));
        product.setBrand(brand);

        Product saved = productRepository.save(product);
        return mapToResponse(saved);
    }

    @Override
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ProductResponse getProductById(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm này!"));
        return mapToResponse(product);
    }

    @Override
    public ProductResponse updateProduct(UUID id, ProductRequest request) {
        // kiểm tra dữ liệu đầu vào
        if (request.categoryId() == null) {
            throw new RuntimeException("Lỗi: categoryId là bắt buộc!");
        }
        if (request.brandId() == null) {
            throw new RuntimeException("Lỗi: brandId là bắt buộc!");
        }
        
        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm để sửa!"));

        existingProduct.setProductName(request.productName());
        existingProduct.setDescription(request.description());

        // cap nhat status
        if (request.status() != null && !request.status().isEmpty()) {
            existingProduct.setStatus(request.status());
        }

        // cap nhat category
        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục!"));
            existingProduct.setCategory(category);
        } else {
            existingProduct.setCategory(null);
        }

        //cap nhat brand
        if (request.brandId() != null) {
            Brand brand = brandRepository.findById(request.brandId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thương hiệu!"));
            existingProduct.setBrand(brand);
        }

        Product updated = productRepository.save(existingProduct);
        return mapToResponse(updated);
    }

    @Override
    public void deleteProduct(UUID id) {
        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm để xóa!"));
        
        productRepository.delete(existingProduct);
    }

    private ProductResponse mapToResponse(Product product) {
        UUID catId = (product.getCategory() != null) ? product.getCategory().getCategoryId() : null;
        UUID brnId = (product.getBrand() != null) ? product.getBrand().getBrandId() : null;

        return new ProductResponse(
                product.getProductId(),
                product.getProductName(),
                product.getDescription(),
                catId,
                brnId,
                product.getStatus()
        );
    }
}
