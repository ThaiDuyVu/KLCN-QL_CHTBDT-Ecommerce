package com.example.backend.promotion.service;
import com.example.backend.promotion.entity.*;
import com.example.backend.promotion.dto.*;
import java.util.*;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
public interface PromotionService {
    PromotionPageResponse list(int page, int size, String keyword, PromotionStatus status);
    PromotionResponse detail(UUID id);
    PromotionResponse create(PromotionRequest request);
    PromotionResponse update(UUID id, PromotionRequest request);
    PromotionResponse status(UUID id, PromotionStatus status);
    void delete(UUID id);
    Map<UUID,Promotion> resolve(Collection<UUID> productIds, OffsetDateTime now);
    Map<UUID,Promotion> resolveForCheckout(Collection<UUID> productIds, OffsetDateTime now);
    PromotionPrice calculate(BigDecimal unitPrice, Promotion promotion);
}
