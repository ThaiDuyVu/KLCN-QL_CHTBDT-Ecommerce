package com.example.backend.returns.service;

import com.example.backend.returns.dto.*;
import com.example.backend.returns.entity.ReturnStatus;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReturnService {
    ReturnResponse create(UUID userId, CreateReturnRequest request);
    ReturnPageResponse mine(UUID userId, int page, int size);
    ReturnResponse myReturn(UUID userId, UUID returnId);
    List<ReturnResponse> forOrder(UUID userId, UUID orderId);
    ReturnPageResponse list(ReturnStatus status, int page, int size, Collection<String> authorities);
    ReturnResponse detail(UUID returnId, Collection<String> authorities);
    ReturnResponse updateStatus(UUID userId, UUID returnId, UpdateReturnStatusRequest request, Collection<String> authorities);
}
