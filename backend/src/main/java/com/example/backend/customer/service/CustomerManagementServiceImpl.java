package com.example.backend.customer.service;

import com.example.backend.auth.entity.UserStatus;
import com.example.backend.customer.dto.*;
import com.example.backend.customer.exception.CustomerManagementException;
import com.example.backend.auth.entity.Customer;
import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.RoleRepository;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.auth.service.UserAccountFactory;
import com.example.backend.customer.repository.CustomerManagementRepository;
import com.example.backend.customer.repository.CustomerProfileRepository;
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
    private final CustomerProfileRepository profiles;
    private final RoleRepository roles;
    private final UserRepository users;
    private final UserAccountFactory accounts;
    public CustomerManagementServiceImpl(CustomerManagementRepository repository, CustomerProfileRepository profiles,
                                         RoleRepository roles, UserRepository users, UserAccountFactory accounts) {
        this.repository = repository; this.profiles = profiles; this.roles = roles; this.users = users; this.accounts = accounts;
    }

    @Override
    @Transactional
    public CustomerSummaryResponse create(CreateCustomerRequest request) {
        if (profiles.phoneTaken(request.getPhone(), null)) {
            throw new CustomerManagementException(409, "Số điện thoại đã thuộc về một khách hàng khác");
        }
        if (request.getPassword() == null) {
            if (request.getUsername() != null || request.getEmail() != null) {
                throw new CustomerManagementException(400, "Tạo tài khoản đăng nhập cần mật khẩu ban đầu");
            }
            Customer walkIn = new Customer();
            walkIn.setFullName(request.getFullName());
            walkIn.setPhone(request.getPhone());
            walkIn.setAddress(request.getAddress());
            return detail(profiles.saveAndFlush(walkIn).getCustomerId());
        }
        Role customerRole = roles.findByRoleName("CUSTOMER").orElseThrow(() -> new IllegalStateException("Missing CUSTOMER role"));
        // "+84…" is not a valid username, so the national form is used as the default login name.
        String username = request.getUsername() != null ? request.getUsername()
                : request.getPhone().startsWith("+84") ? "0" + request.getPhone().substring(3) : request.getPhone();
        User user = accounts.create(username, request.getPassword(), request.getEmail(), request.getFullName(),
                request.getPhone(), customerRole);
        Customer customer = new Customer();
        customer.setUser(user);
        customer.setFullName(request.getFullName());
        customer.setAddress(request.getAddress());
        customer = profiles.saveAndFlush(customer);
        return detail(customer.getCustomerId());
    }

    @Override
    @Transactional
    public CustomerSummaryResponse update(UUID customerId, UpdateCustomerRequest request) {
        Customer customer = profiles.lockById(customerId)
                .orElseThrow(() -> new CustomerManagementException(404, "Khách hàng không tồn tại"));
        if (profiles.phoneTaken(request.getPhone(), customerId)) {
            throw new CustomerManagementException(409, "Số điện thoại đã thuộc về một khách hàng khác");
        }
        User user = customer.getUser();
        if (user == null) {
            if (request.getEmail() != null) throw new CustomerManagementException(400, "Khách vãng lai chưa có tài khoản nên không lưu email");
            customer.setFullName(request.getFullName());
            customer.setAddress(request.getAddress());
            customer.setPhone(request.getPhone());
            profiles.saveAndFlush(customer);
            return detail(customerId);
        }
        if (request.getEmail() != null && !request.getEmail().equalsIgnoreCase(user.getEmail())) {
            if (users.existsByEmailAndUserIdNot(request.getEmail(), user.getUserId())) {
                throw new CustomerManagementException(409, "Email đã được sử dụng bởi tài khoản khác");
            }
            user.setEmail(request.getEmail());
        }
        customer.setFullName(request.getFullName());
        customer.setAddress(request.getAddress());
        user.setDisplayName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setUpdatedAt(java.time.OffsetDateTime.now());
        users.saveAndFlush(user);
        profiles.saveAndFlush(customer);
        return detail(customerId);
    }

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
