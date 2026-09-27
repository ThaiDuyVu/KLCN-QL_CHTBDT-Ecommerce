package com.example.backend.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/** Optional specifications accompanying product creation; existing Specification contract. */
public class ProductSpecificationsRequest {
    @NotNull(message = "Danh sách thông số không được null")
    @Valid
    private List<@NotNull(message = "Thông số không được null") SpecificationRequest> specifications = new ArrayList<>();

    public List<SpecificationRequest> getSpecifications() { return specifications; }
    public void setSpecifications(List<SpecificationRequest> specifications) { this.specifications = specifications; }
}
