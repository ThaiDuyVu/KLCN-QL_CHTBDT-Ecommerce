package com.example.backend.product.dto;

import java.util.List;

public class ProductPageResponse {

    private List<ProductResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public ProductPageResponse() {
    }

    public ProductPageResponse(
            List<ProductResponse> content,
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

    public List<ProductResponse> getContent() {
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
