package com.example.backend.product.service;

import com.example.backend.brand.dto.BrandResponse;
import com.example.backend.category.dto.CategoryResponse;
import com.example.backend.category.entity.Category;
import com.example.backend.product.repository.ProductRepository;
import com.example.backend.product.dto.ProductDetailResponse;
import com.example.backend.product.dto.ProductImageResponse;
import com.example.backend.product.dto.ProductResponse;
import com.example.backend.product.dto.ProductVariantResponse;
import com.example.backend.product.dto.SpecificationResponse;
import com.example.backend.product.entity.Brand;
import com.example.backend.product.entity.Product;
import com.example.backend.product.entity.ProductImage;
import com.example.backend.product.entity.ProductVariant;
import com.example.backend.product.entity.Specification;
import com.example.backend.product.exception.ProductNotFoundException;
import com.example.backend.product.repository.ProductImageRepository;
import com.example.backend.product.repository.ProductVariantRepository;
import com.example.backend.product.repository.SpecificationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductDetailServiceImpl implements ProductDetailService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final SpecificationRepository specificationRepository;

    public ProductDetailServiceImpl(ProductRepository productRepository, ProductVariantRepository variantRepository,
                                    ProductImageRepository imageRepository, SpecificationRepository specificationRepository) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.imageRepository = imageRepository;
        this.specificationRepository = specificationRepository;
    }

    @Override
    public ProductDetailResponse getProductDetail(UUID productId) {
        Product product = productRepository.findDetailById(productId).orElseThrow(() -> new ProductNotFoundException(
                "Không tìm thấy sản phẩm với ID: " + productId
        ));
        Category category = product.getCategory();
        Brand brand = product.getBrand();

        List<ProductVariantResponse> variants = variantRepository.findByProduct_ProductId(productId, Sort.by(
                Sort.Order.asc("sku"), Sort.Order.asc("variantId")
        )).stream().map(this::mapVariant).toList();
        List<ProductImageResponse> images = imageRepository.findByProduct_ProductId(productId, Sort.by(
                Sort.Order.desc("primary"), Sort.Order.asc("imageId")
        )).stream().map(this::mapImage).toList();
        List<SpecificationResponse> specifications = specificationRepository.findByProduct_ProductId(productId, Sort.by(
                Sort.Order.asc("specKey"), Sort.Order.asc("specificationId")
        )).stream().map(this::mapSpecification).toList();

        return new ProductDetailResponse(
                new ProductResponse(
                        product.getProductId(), product.getProductName(), product.getDescription(),
                        category.getCategoryId(), brand.getBrandId(), product.getStatus(),
                        category.getCategoryName(), brand.getBrandName(), product.getCreatedAt(), product.getUpdatedAt()
                ),
                new CategoryResponse(
                        category.getCategoryId(), category.getCategoryName(), category.getDescription(),
                        category.getParent() == null ? null : category.getParent().getCategoryId(), category.getStatus()
                ),
                new BrandResponse(brand.getBrandId(), brand.getBrandName(), brand.getDescription(), brand.getStatus()),
                variants, images, specifications
        );
    }

    private ProductVariantResponse mapVariant(ProductVariant variant) {
        return new ProductVariantResponse(
                variant.getVariantId(), variant.getProduct().getProductId(), variant.getProduct().getProductName(),
                variant.getSku(), variant.getPrice(), variant.getCostPrice(), variant.getColor(),
                variant.getStorage(), variant.getRam(), variant.getStatus()
        );
    }

    private ProductImageResponse mapImage(ProductImage image) {
        return new ProductImageResponse(
                image.getImageId(), image.getProduct().getProductId(), image.getImageUrl(), image.getPrimary()
        );
    }

    private SpecificationResponse mapSpecification(Specification specification) {
        return new SpecificationResponse(
                specification.getSpecificationId(), specification.getProduct().getProductId(),
                specification.getSpecKey(), specification.getSpecValue()
        );
    }
}
