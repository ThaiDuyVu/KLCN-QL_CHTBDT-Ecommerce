package com.example.backend.product.dto;

import com.example.backend.brand.dto.BrandResponse;
import com.example.backend.category.dto.CategoryResponse;

import java.util.List;

public class ProductDetailResponse {

    private final ProductResponse product;
    private final CategoryResponse category;
    private final BrandResponse brand;
    private final List<ProductVariantResponse> variants;
    private final List<ProductImageResponse> images;
    private final List<SpecificationResponse> specifications;

    public ProductDetailResponse(ProductResponse product, CategoryResponse category, BrandResponse brand,
                                 List<ProductVariantResponse> variants, List<ProductImageResponse> images,
                                 List<SpecificationResponse> specifications) {
        this.product = product;
        this.category = category;
        this.brand = brand;
        this.variants = List.copyOf(variants);
        this.images = List.copyOf(images);
        this.specifications = List.copyOf(specifications);
    }

    public ProductResponse getProduct() {
        return product;
    }

    public CategoryResponse getCategory() {
        return category;
    }

    public BrandResponse getBrand() {
        return brand;
    }

    public List<ProductVariantResponse> getVariants() {
        return variants;
    }

    public List<ProductImageResponse> getImages() {
        return images;
    }

    public List<SpecificationResponse> getSpecifications() {
        return specifications;
    }
}
