package com.example.backend.brand.service;

import com.example.backend.brand.dto.BrandRequest;
import com.example.backend.brand.dto.BrandResponse;
import com.example.backend.brand.dto.BrandPageResponse;
import com.example.backend.brand.exception.InvalidBrandPaginationException;
import com.example.backend.brand.exception.BrandInUseException;
import com.example.backend.brand.exception.BrandNameAlreadyExistsException;
import com.example.backend.brand.exception.BrandNotFoundException;
import com.example.backend.product.repository.BrandRepository;
import com.example.backend.product.repository.ProductRepository;
import com.example.backend.product.entity.Brand;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class BrandServiceImpl implements BrandService {

    private static final int MAX_PAGE_SIZE = 100;

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    public BrandServiceImpl(BrandRepository brandRepository, ProductRepository productRepository) {
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public BrandResponse createBrand(BrandRequest request) {
        Brand brand = new Brand();
        applyRequest(brand, request);
        return mapToResponse(saveBrand(brand));
    }

    @Override
    public BrandPageResponse getBrands(int page, int size) {
        if (page < 0) {
            throw new InvalidBrandPaginationException("Page không được nhỏ hơn 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidBrandPaginationException("Size phải từ 1 đến " + MAX_PAGE_SIZE);
        }
        // JPA setFirstResult accepts an int, even though Pageable uses a long offset.
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new InvalidBrandPaginationException("Page vượt quá giới hạn offset được hỗ trợ");
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.asc("brandName"),
                Sort.Order.asc("brandId")
        ));
        Page<Brand> brandPage = brandRepository.findAll(pageable);
        return new BrandPageResponse(
                brandPage.getContent().stream().map(this::mapToResponse).toList(),
                brandPage.getNumber(), brandPage.getSize(),
                brandPage.getTotalElements(), brandPage.getTotalPages()
        );
    }

    @Override
    public BrandResponse getBrandById(UUID id) {
        return mapToResponse(findBrandById(id));
    }

    @Override
    @Transactional
    public BrandResponse updateBrand(UUID id, BrandRequest request) {
        Brand brand = findBrandById(id);
        applyRequest(brand, request);
        return mapToResponse(saveBrand(brand));
    }

    @Override
    @Transactional
    public void deleteBrand(UUID id) {
        Brand brand = findBrandById(id);
        if (productRepository.existsByBrand_BrandId(id)) {
            throw new BrandInUseException("Không thể xóa thương hiệu đang được sản phẩm tham chiếu: " + id);
        }
        try {
            brandRepository.delete(brand);
            // Detect FK RESTRICT failures before commit, including concurrent references.
            brandRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BrandInUseException(
                    "Không thể xóa thương hiệu đang được dữ liệu khác tham chiếu: " + id, exception
            );
        }
    }

    private Brand findBrandById(UUID id) {
        return brandRepository.findById(id).orElseThrow(() -> new BrandNotFoundException(
                "Không tìm thấy thương hiệu với ID: " + id
        ));
    }

    private void applyRequest(Brand brand, BrandRequest request) {
        String name = request.getBrandName().trim();
        // Match the case-sensitive UNIQUE constraint in the existing migration.
        boolean duplicate = brand.getBrandId() == null
                ? brandRepository.existsByBrandName(name)
                : brandRepository.existsByBrandNameAndBrandIdNot(name, brand.getBrandId());
        if (duplicate) {
            throw new BrandNameAlreadyExistsException("Tên thương hiệu đã tồn tại: " + name);
        }
        brand.setBrandName(name);
        brand.setDescription(request.getDescription());
        // An omitted status keeps the default or current value.
        if (request.getStatus() != null) {
            brand.setStatus(request.getStatus());
        }
    }

    private Brand saveBrand(Brand brand) {
        try {
            // Flush here so concurrent duplicate names are translated within the service.
            return brandRepository.saveAndFlush(brand);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint
                        && "uq_brands_brand_name".equals(constraint.getConstraintName())) {
                    throw new BrandNameAlreadyExistsException(
                            "Tên thương hiệu đã tồn tại: " + brand.getBrandName(), exception
                    );
                }
            }
            throw exception;
        }
    }

    private BrandResponse mapToResponse(Brand brand) {
        return new BrandResponse(
                brand.getBrandId(), brand.getBrandName(), brand.getDescription(), brand.getStatus()
        );
    }
}
