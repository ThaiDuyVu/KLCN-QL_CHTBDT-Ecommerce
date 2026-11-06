package com.example.backend.returns.dto;

import java.util.List;

public record ReturnPageResponse(List<ReturnResponse> content, int page, int size, long totalElements, int totalPages) {}
