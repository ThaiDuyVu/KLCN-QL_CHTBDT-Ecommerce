package com.example.backend.auth.dto;

import java.util.List;

public class UserPageResponse {

    private List<UserResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public UserPageResponse() {
    }

    public UserPageResponse(
            List<UserResponse> content,
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

    public List<UserResponse> getContent() {
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