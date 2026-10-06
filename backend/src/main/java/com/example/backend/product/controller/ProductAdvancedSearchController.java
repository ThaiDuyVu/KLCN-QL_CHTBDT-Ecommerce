package com.example.backend.product.controller;

import com.example.backend.product.dto.ProductSearchCandidate;
import com.example.backend.product.service.ProductAdvancedSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products/search/advanced")
public class ProductAdvancedSearchController {
    private final ProductAdvancedSearchService service;

    public ProductAdvancedSearchController(ProductAdvancedSearchService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProductSearchCandidate> search(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> brands,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer minRamGb,
            @RequestParam(required = false) Integer minStorageGb,
            @RequestParam(required = false) List<String> cpuKeywords,
            @RequestParam(defaultValue = "false") boolean inStockOnly,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        return service.search(category, brands, minPrice, maxPrice, minRamGb, minStorageGb,
                cpuKeywords, inStockOnly, warehouseId, limit, offset);
    }
}
