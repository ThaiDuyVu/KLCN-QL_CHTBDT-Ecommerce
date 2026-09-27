package com.example.backend.order.dto;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.validation.constraints.*;

public class OrderPageResponse {
    private List<OrderResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    public OrderPageResponse() {}
    public List<OrderResponse> getContent() { return content; }
    public void setContent(List<OrderResponse> value) { this.content = value; }
    public int getPage() { return page; }
    public void setPage(int value) { this.page = value; }
    public int getSize() { return size; }
    public void setSize(int value) { this.size = value; }
    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long value) { this.totalElements = value; }
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int value) { this.totalPages = value; }
}
