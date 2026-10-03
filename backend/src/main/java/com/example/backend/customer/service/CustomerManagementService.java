package com.example.backend.customer.service;

import com.example.backend.auth.entity.UserStatus;
import com.example.backend.customer.dto.*;
import java.util.UUID;

public interface CustomerManagementService {
    CustomerPageResponse<CustomerSummaryResponse> list(String keyword, UserStatus status, int page, int size);
    CustomerSummaryResponse detail(UUID customerId);
    CustomerPageResponse<CustomerOrderResponse> orders(UUID customerId, int page, int size);
    CustomerPageResponse<CustomerWarrantyResponse> warranties(UUID customerId, int page, int size);
    CustomerPageResponse<CustomerInstallmentResponse> installments(UUID customerId, int page, int size);
}
