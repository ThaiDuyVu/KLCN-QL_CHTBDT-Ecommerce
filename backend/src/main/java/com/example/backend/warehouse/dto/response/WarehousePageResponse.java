package com.example.backend.warehouse.dto.response;

import java.util.List;

public class WarehousePageResponse {

    private List<WarehouseResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public WarehousePageResponse() {
    }

    public WarehousePageResponse(
            List<WarehouseResponse> content,
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

    public List<WarehouseResponse> getContent() {
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
