package com.example.backend.product.service;

import com.example.backend.category.CategoryRepository;
import com.example.backend.category.entity.Category;
import com.example.backend.product.dto.ProductRequest;
import com.example.backend.product.entity.Brand;
import com.example.backend.product.entity.Product;
import com.example.backend.product.entity.ProductStatus;
import com.example.backend.product.exception.InvalidProductPaginationException;
import com.example.backend.product.exception.ProductInUseException;
import com.example.backend.product.exception.ProductNotFoundException;
import com.example.backend.product.exception.ProductReferenceNotFoundException;
import com.example.backend.product.repository.BrandRepository;
import com.example.backend.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private BrandRepository brandRepository;
    @InjectMocks private ProductServiceImpl service;

    private Category category;
    private Brand brand;
    private Product product;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setCategoryId(UUID.randomUUID());
        brand = new Brand();
        brand.setBrandId(UUID.randomUUID());
        product = new Product();
        product.setProductId(UUID.randomUUID());
        product.setProductName("Thiết bị kiểm thử");
        product.setCategory(category);
        product.setBrand(brand);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void create_defaultsStatusAndLooksUpEachReferenceOnce(String status) {
        stubReferences();
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setProductId(product.getProductId());
            return saved;
        });

        var response = service.createProduct(request(status));

        assertThat(response.productName()).isEqualTo("Thiết bị mới");
        assertThat(response.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(response.categoryId()).isEqualTo(category.getCategoryId());
        assertThat(response.brandId()).isEqualTo(brand.getBrandId());
        verify(categoryRepository).findById(category.getCategoryId());
        verify(brandRepository).findById(brand.getBrandId());
    }

    @Test
    void create_reportsMissingCategoryWithoutSaving() {
        when(categoryRepository.findById(category.getCategoryId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createProduct(request(null)))
                .isInstanceOf(ProductReferenceNotFoundException.class);
        verify(productRepository, never()).save(any());
        verify(brandRepository, never()).findById(any());
    }

    @Test
    void create_reportsMissingBrandWithoutSaving() {
        when(categoryRepository.findById(category.getCategoryId())).thenReturn(Optional.of(category));
        when(brandRepository.findById(brand.getBrandId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createProduct(request(null)))
                .isInstanceOf(ProductReferenceNotFoundException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void getById_reportsMissingProduct() {
        when(productRepository.findById(product.getProductId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProductById(product.getProductId()))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void getProducts_queriesOnlyRequestedPageWithStableSort() {
        PageRequest pageable = PageRequest.of(1, 2, Sort.by(
                Sort.Order.desc("createdAt"), Sort.Order.desc("productId")));
        when(productRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(product), pageable, 3));

        var response = service.getProducts(1, 2);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().productId()).isEqualTo(product.getProductId());
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(2);
        assertThat(response.getTotalElements()).isEqualTo(3);
        assertThat(response.getTotalPages()).isEqualTo(2);
        verify(productRepository).findAll(pageable);
        verify(productRepository, never()).findAll();
    }

    @Test
    void getProducts_returnsEmptyPageBeyondLastPage() {
        when(productRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(5, 20), 3));

        var response = service.getProducts(5, 20);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getPage()).isEqualTo(5);
        assertThat(response.getTotalElements()).isEqualTo(3);
        assertThat(response.getTotalPages()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101", "0,2147483647", "2147483647,100"})
    void getProducts_rejectsInvalidPaginationBeforeDatabaseAccess(int page, int size) {
        assertThatThrownBy(() -> service.getProducts(page, size))
                .isInstanceOf(InvalidProductPaginationException.class);
        verifyNoInteractions(productRepository);
    }

    @Test
    void getProducts_acceptsMaximumPageSize() {
        when(productRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        assertThat(service.getProducts(0, 100).getSize()).isEqualTo(100);
        verify(productRepository, never()).findAll();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void update_preservesPersistedStatusWhenOmittedOrEmpty(String status) {
        product.setStatus(ProductStatus.INACTIVE);
        when(productRepository.findById(product.getProductId())).thenReturn(Optional.of(product));
        stubReferences();
        when(productRepository.save(product)).thenReturn(product);

        var response = service.updateProduct(product.getProductId(), request(status));

        assertThat(response.status()).isEqualTo(ProductStatus.INACTIVE);
        assertThat(response.productName()).isEqualTo("Thiết bị mới");
        assertThat(response.description()).isEqualTo("Mô tả mới");
    }

    @Test
    void update_acceptsExplicitStatus() {
        when(productRepository.findById(product.getProductId())).thenReturn(Optional.of(product));
        stubReferences();
        when(productRepository.save(product)).thenReturn(product);

        assertThat(service.updateProduct(product.getProductId(), request("INACTIVE")).status())
                .isEqualTo(ProductStatus.INACTIVE);
    }

    @Test
    void update_reportsMissingProductBeforeLookingUpReferences() {
        when(productRepository.findById(product.getProductId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProduct(product.getProductId(), request(null)))
                .isInstanceOf(ProductNotFoundException.class);
        verify(categoryRepository, never()).findById(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void update_withMissingReferenceDoesNotMutateProduct() {
        when(productRepository.findById(product.getProductId())).thenReturn(Optional.of(product));
        when(categoryRepository.findById(category.getCategoryId())).thenReturn(Optional.of(category));
        when(brandRepository.findById(brand.getBrandId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProduct(product.getProductId(), request(null)))
                .isInstanceOf(ProductReferenceNotFoundException.class);
        assertThat(product.getProductName()).isEqualTo("Thiết bị kiểm thử");
        verify(productRepository, never()).save(any());
    }

    @Test
    void delete_flushesToDetectForeignKeyConflicts() {
        when(productRepository.findById(product.getProductId())).thenReturn(Optional.of(product));

        service.deleteProduct(product.getProductId());

        verify(productRepository).delete(product);
        verify(productRepository).flush();
    }

    @Test
    void delete_translatesForeignKeyConflict() {
        when(productRepository.findById(product.getProductId())).thenReturn(Optional.of(product));
        doThrow(new DataIntegrityViolationException("FK RESTRICT")).when(productRepository).flush();

        assertThatThrownBy(() -> service.deleteProduct(product.getProductId()))
                .isInstanceOf(ProductInUseException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void delete_reportsMissingProduct() {
        when(productRepository.findById(product.getProductId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteProduct(product.getProductId()))
                .isInstanceOf(ProductNotFoundException.class);
        verify(productRepository, never()).delete(any(Product.class));
    }

    private void stubReferences() {
        when(categoryRepository.findById(category.getCategoryId())).thenReturn(Optional.of(category));
        when(brandRepository.findById(brand.getBrandId())).thenReturn(Optional.of(brand));
    }

    private ProductRequest request(String status) {
        return new ProductRequest("Thiết bị mới", "Mô tả mới", category.getCategoryId(), brand.getBrandId(), ProductStatus.fromValue(status));
    }
}
