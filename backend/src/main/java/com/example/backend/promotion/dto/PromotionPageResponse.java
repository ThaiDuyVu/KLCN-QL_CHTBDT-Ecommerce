package com.example.backend.promotion.dto;
import java.util.List;
public class PromotionPageResponse {
    
    private List<PromotionResponse> content;
    
    private int page;
    
    private int size;
    
    private long totalElements;
    
    private int totalPages;
    public List<PromotionResponse> getContent() { return content; }
    public void setContent(List<PromotionResponse> value) { this.content = value; }
    public int getPage() { return page; }
    public void setPage(int value) { this.page = value; }
    public int getSize() { return size; }
    public void setSize(int value) { this.size = value; }
    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long value) { this.totalElements = value; }
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int value) { this.totalPages = value; }
}
