package com.example.backend.inventory.serial.dto;
import java.util.List;
public record SerialPageResponse(List<SerialResponse> content, int page, int size, long totalElements, int totalPages) {}
