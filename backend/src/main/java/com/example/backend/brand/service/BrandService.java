package com.example.backend.brand.service;

import com.example.backend.brand.dto.BrandRequest;
import com.example.backend.brand.dto.BrandResponse;
import com.example.backend.brand.dto.BrandPageResponse;

import java.util.UUID;

public interface BrandService {
    BrandResponse createBrand(BrandRequest request);
    BrandPageResponse getBrands(int page, int size);
    BrandResponse getBrandById(UUID id);
    BrandResponse updateBrand(UUID id, BrandRequest request);
    void deleteBrand(UUID id);
}
