package com.example.backend.supplier.dto.response;

import java.util.List;

public class SupplierPageResponse {

    private List<SupplierResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public SupplierPageResponse() {
    }

    public SupplierPageResponse(
            List<SupplierResponse> content,
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

    public List<SupplierResponse> getContent() {
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
