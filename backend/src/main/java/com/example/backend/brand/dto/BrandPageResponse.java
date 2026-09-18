package com.example.backend.brand.dto;

import java.util.List;

public class BrandPageResponse {

    private List<BrandResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public BrandPageResponse() {
    }

    public BrandPageResponse(
            List<BrandResponse> content,
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

    public List<BrandResponse> getContent() {
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
