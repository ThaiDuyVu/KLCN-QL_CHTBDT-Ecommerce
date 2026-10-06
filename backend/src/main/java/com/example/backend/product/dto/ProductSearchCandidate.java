package com.example.backend.product.dto;

import java.util.UUID;

/** A variant that satisfies every structured search constraint. */
public record ProductSearchCandidate(UUID productId, UUID variantId) {}
