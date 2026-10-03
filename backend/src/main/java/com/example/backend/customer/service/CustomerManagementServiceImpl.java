package com.example.backend.customer.service;

import com.example.backend.auth.entity.UserStatus;
import com.example.backend.customer.dto.*;
import com.example.backend.customer.exception.CustomerManagementException;
import com.example.backend.customer.repository.CustomerManagementRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, timeout = 10, isolation = Isolation.REPEATABLE_READ)
public class CustomerManagementServiceImpl implements CustomerManagementService {
    private final CustomerManagementRepository repository;
    public CustomerManagementServiceImpl(CustomerManagementRepository repository) { this.repository = repository; }

    private void pagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new CustomerManagementException(400, "Page phải >= 0, size từ 1–100 và offset không vượt giới hạn");
        }
    }

    private void requireCustomer(UUID customerId) {
        if (!repository.exists(customerId)) throw new CustomerManagementException(404, "Khách hàng không tồn tại");
    }

    private void summaries(List<CustomerSummaryResponse> rows) {
        var byId = repository.orderSummaries(rows.stream().map(CustomerSummaryResponse::getCustomerId).toList());
        for (CustomerSummaryResponse row : rows) {
            row.setOrderSummary(byId.getOrDefault(row.getCustomerId(), CustomerManagementRepository.emptySummary()));
        }
    }

    @Override public CustomerPageResponse<CustomerSummaryResponse> list(String keyword, UserStatus status, int page, int size) {
        pagination(page, size);
        String term = keyword == null ? "" : keyword.trim();
        if (term.length() > 255) throw new CustomerManagementException(400, "Từ khóa tối đa 255 ký tự");
        long count = repository.countCustomers(term, status);
        List<CustomerSummaryResponse> rows = repository.customers(term, status, page, size);
        summaries(rows);
        return new CustomerPageResponse<>(rows, page, size, count);
    }

    @Override public CustomerSummaryResponse detail(UUID customerId) {
        CustomerSummaryResponse row = repository.customer(customerId)
                .orElseThrow(() -> new CustomerManagementException(404, "Khách hàng không tồn tại"));
        summaries(List.of(row));
        return row;
    }

    @Override public CustomerPageResponse<CustomerOrderResponse> orders(UUID customerId, int page, int size) {
        pagination(page, size);
        requireCustomer(customerId);
        long count = repository.countOrders(customerId);
        return new CustomerPageResponse<>(repository.orders(customerId, page, size), page, size, count);
    }

    @Override public CustomerPageResponse<CustomerWarrantyResponse> warranties(UUID customerId, int page, int size) {
        pagination(page, size);
        requireCustomer(customerId);
        long count = repository.countWarranties(customerId);
        List<CustomerWarrantyResponse> rows = repository.warranties(customerId, page, size);
        var imeis = repository.imeis(rows.stream().map(CustomerWarrantyResponse::getSerialId).toList());
        LocalDate today = LocalDate.now();
        for (CustomerWarrantyResponse row : rows) {
            row.setStatus(row.getStatus().effectiveOn(row.getEndDate(), today));
            row.setImeiNumbers(imeis.getOrDefault(row.getSerialId(), List.of()));
        }
        return new CustomerPageResponse<>(rows, page, size, count);
    }

    @Override public CustomerPageResponse<CustomerInstallmentResponse> installments(UUID customerId, int page, int size) {
        pagination(page, size);
        requireCustomer(customerId);
        long count = repository.countInstallments(customerId);
        return new CustomerPageResponse<>(repository.installments(customerId, page, size), page, size, count);
    }
}
