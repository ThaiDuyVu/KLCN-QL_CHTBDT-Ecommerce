package com.example.backend.goodsreceipt.dto.response;

import java.util.List;

public class GoodsReceiptPageResponse {

    private List<GoodsReceiptSummaryResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public GoodsReceiptPageResponse() {
    }

    public GoodsReceiptPageResponse(
            List<GoodsReceiptSummaryResponse> content,
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

    public List<GoodsReceiptSummaryResponse> getContent() { return content; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
}
