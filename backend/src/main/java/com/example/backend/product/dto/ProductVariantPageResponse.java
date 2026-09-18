package com.example.backend.product.dto;

import java.util.List;

public class ProductVariantPageResponse {

    private List<ProductVariantResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public ProductVariantPageResponse() {
    }

    public ProductVariantPageResponse(
            List<ProductVariantResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    public List<ProductVariantResponse> getContent() {
        return content;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }
}
