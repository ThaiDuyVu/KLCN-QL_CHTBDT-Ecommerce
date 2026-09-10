package com.example.backend.category.dto;

import java.util.UUID;

public record CategoryRequest(String categoryName,String description, UUID parentId)
{}