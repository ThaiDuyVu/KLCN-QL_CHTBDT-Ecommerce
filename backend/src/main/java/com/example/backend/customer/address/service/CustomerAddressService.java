package com.example.backend.customer.address.service;

import com.example.backend.cart.repository.CustomerCartRepository;
import com.example.backend.customer.address.dto.CustomerAddressRequest;
import com.example.backend.customer.address.dto.CustomerAddressResponse;
import com.example.backend.customer.address.entity.CustomerAddress;
import com.example.backend.customer.address.exception.CustomerAddressException;
import com.example.backend.customer.address.repository.CustomerAddressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class CustomerAddressService {
    private final CustomerCartRepository customers;
    private final CustomerAddressRepository addresses;

    public CustomerAddressService(CustomerCartRepository customers, CustomerAddressRepository addresses) {
        this.customers = customers;
        this.addresses = addresses;
    }

    public List<CustomerAddressResponse> list(UUID userId) {
        UUID customerId = customers.findByUser_UserId(userId)
                .orElseThrow(() -> new CustomerAddressException(403, "Tài khoản chưa có hồ sơ customer"))
                .getCustomerId();
        return addresses.findByCustomerIdOrderByCreatedAtAscAddressIdAsc(customerId).stream().map(this::response).toList();
    }

    public CustomerAddress owned(UUID customerId, UUID addressId) {
        return addresses.findByAddressIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new CustomerAddressException(404, "Không tìm thấy địa chỉ giao hàng của bạn"));
    }

    public String fullAddress(CustomerAddress address) {
        return Stream.of(address.getAddressLine(), address.getWard(), address.getDistrict(), address.getProvince())
                .filter(value -> value != null && !value.isBlank()).collect(Collectors.joining(", "));
    }

    @Transactional
    public CustomerAddressResponse create(UUID userId, CustomerAddressRequest request) {
        UUID customerId = lockCustomer(userId);
        var rows = addresses.findByCustomerIdOrderByCreatedAtAscAddressIdAsc(customerId);
        var address = new CustomerAddress();
        address.setCustomerId(customerId);
        apply(address, request);
        address.setDefault(rows.stream().noneMatch(CustomerAddress::isDefault));
        return response(addresses.saveAndFlush(address));
    }

    @Transactional
    public CustomerAddressResponse update(UUID userId, UUID addressId, CustomerAddressRequest request) {
        UUID customerId = lockCustomer(userId);
        var address = owned(customerId, addressId);
        apply(address, request);
        return response(addresses.saveAndFlush(address));
    }

    @Transactional
    public CustomerAddressResponse makeDefault(UUID userId, UUID addressId) {
        UUID customerId = lockCustomer(userId);
        var target = owned(customerId, addressId);
        if (target.isDefault()) return response(target);
        for (var address : addresses.findByCustomerIdOrderByCreatedAtAscAddressIdAsc(customerId)) {
            if (address.isDefault()) {
                address.setDefault(false);
                addresses.saveAndFlush(address);
            }
        }
        target.setDefault(true);
        return response(addresses.saveAndFlush(target));
    }

    @Transactional
    public void delete(UUID userId, UUID addressId) {
        UUID customerId = lockCustomer(userId);
        var target = owned(customerId, addressId);
        boolean wasDefault = target.isDefault();
        addresses.delete(target);
        addresses.flush();
        if (wasDefault) {
            addresses.findByCustomerIdOrderByCreatedAtAscAddressIdAsc(customerId).stream().findFirst()
                    .ifPresent(next -> { next.setDefault(true); addresses.saveAndFlush(next); });
        }
    }

    private UUID lockCustomer(UUID userId) {
        return customers.lockByUserId(userId)
                .orElseThrow(() -> new CustomerAddressException(403, "Tài khoản chưa có hồ sơ customer"))
                .getCustomerId();
    }

    private void apply(CustomerAddress address, CustomerAddressRequest request) {
        address.setLabel(request.label());
        address.setRecipientName(request.recipientName());
        address.setRecipientPhone(request.recipientPhone());
        address.setAddressLine(request.addressLine());
        address.setWard(request.ward());
        address.setDistrict(request.district());
        address.setProvince(request.province());
    }

    private CustomerAddressResponse response(CustomerAddress address) {
        return new CustomerAddressResponse(address.getAddressId(), address.getCustomerId(), address.getLabel(),
                address.getRecipientName(), address.getRecipientPhone(), address.getAddressLine(), address.getWard(),
                address.getDistrict(), address.getProvince(), fullAddress(address), address.isDefault(),
                address.getCreatedAt(), address.getUpdatedAt());
    }
}
