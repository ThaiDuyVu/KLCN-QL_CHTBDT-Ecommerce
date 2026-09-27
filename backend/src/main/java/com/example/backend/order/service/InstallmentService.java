package com.example.backend.order.service;

import com.example.backend.auth.entity.Customer;
import com.example.backend.cart.repository.CustomerCartRepository;
import com.example.backend.order.dto.*;
import com.example.backend.order.entity.*;
import com.example.backend.order.exception.CommerceException;
import com.example.backend.order.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class InstallmentService {
    private final InstallmentProviderRepository providers;
    private final InstallmentPaymentRepository installments;
    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final CustomerCartRepository customers;

    public InstallmentService(InstallmentProviderRepository providers, InstallmentPaymentRepository installments,
                              PaymentRepository payments, OrderRepository orders, CustomerCartRepository customers) {
        this.providers = providers; this.installments = installments; this.payments = payments;
        this.orders = orders; this.customers = customers;
    }

    public List<InstallmentProviderResponse> providers(boolean activeOnly) {
        var rows = activeOnly ? providers.findByStatusOrderByProviderNameAsc(InstallmentProviderStatus.ACTIVE)
                : providers.findAllByOrderByProviderNameAsc();
        return rows.stream().map(this::providerResponse).toList();
    }

    @Transactional
    public InstallmentProviderResponse saveProvider(UUID id, InstallmentProviderRequest request) {
        String code = request.providerCode().trim().toUpperCase(Locale.ROOT);
        String name = request.providerName().trim();
        if (code.isBlank() || name.isBlank()) throw new CommerceException(400, "Tên và mã đơn vị trả góp là bắt buộc");
        var duplicate = providers.findByProviderCodeIgnoreCase(code);
        if (duplicate.isPresent() && !duplicate.get().getProviderId().equals(id)) {
            throw new CommerceException(409, "Mã đơn vị trả góp đã tồn tại");
        }
        var row = id == null ? new InstallmentProvider() : providers.findById(id)
                .orElseThrow(() -> new CommerceException(404, "Không tìm thấy đơn vị trả góp"));
        row.setProviderName(name); row.setProviderCode(code);
        row.setContactPhone(blankToNull(request.contactPhone()));
        row.setContactEmail(blankToNull(request.contactEmail())); row.setStatus(request.status());
        try { return providerResponse(providers.saveAndFlush(row)); }
        catch (DataIntegrityViolationException exception) {
            throw new CommerceException(409, "Mã đơn vị trả góp đã tồn tại");
        }
    }

    public InstallmentProvider requireCheckoutProvider(CheckoutRequest request, BigDecimal totalAmount) {
        if (request.getProviderId() == null || request.getTermMonths() == null || request.getDownPayment() == null) {
            throw new CommerceException(400, "Trả góp cần providerId, termMonths và downPayment");
        }
        if (request.getTermMonths() <= 0) throw new CommerceException(400, "termMonths phải lớn hơn 0");
        if (request.getDownPayment().scale() > 2 || request.getDownPayment().signum() < 0
                || request.getDownPayment().compareTo(totalAmount) > 0) {
            throw new CommerceException(400, "downPayment phải từ 0 đến tổng tiền của đơn");
        }
        var provider = providers.findById(request.getProviderId())
                .orElseThrow(() -> new CommerceException(404, "Không tìm thấy đơn vị trả góp"));
        if (provider.getStatus() != InstallmentProviderStatus.ACTIVE) {
            throw new CommerceException(409, "Đơn vị trả góp đã ngừng hoạt động");
        }
        return provider;
    }

    @Transactional
    public void createForPayment(Payment payment, InstallmentProvider provider, CheckoutRequest request) {
        var installment = new InstallmentPayment();
        installment.setPaymentId(payment.getPaymentId()); installment.setProviderId(provider.getProviderId());
        installment.setTotalAmount(payment.getAmount()); installment.setDownPayment(request.getDownPayment());
        installment.setRemainingAmount(payment.getAmount().subtract(request.getDownPayment()));
        installment.setTermMonths(request.getTermMonths()); installment.setStatus(InstallmentStatus.PENDING);
        installments.saveAndFlush(installment);
    }

    public void requireApproved(Payment payment) {
        var installment = installments.findByPaymentId(payment.getPaymentId())
                .orElseThrow(() -> new CommerceException(409, "Payment INSTALLMENT thiếu hồ sơ trả góp"));
        if (installment.getStatus() != InstallmentStatus.APPROVED) {
            throw new CommerceException(409, "Hồ sơ trả góp chưa được duyệt; không thể xác nhận đơn hàng");
        }
    }

    public InstallmentResponse forPayment(Payment payment, Order order, boolean customerView) {
        var installment = installments.findByPaymentId(payment.getPaymentId())
                .orElseThrow(() -> new CommerceException(409, "Payment INSTALLMENT thiếu hồ sơ trả góp"));
        var provider = providers.findById(installment.getProviderId())
                .orElseThrow(() -> new CommerceException(409, "Hồ sơ trả góp thiếu đơn vị xử lý"));
        var customer = customers.findById(order.getCustomerId())
                .orElseThrow(() -> new CommerceException(409, "Hồ sơ trả góp thiếu khách hàng"));
        return response(installment, payment, order, customer, provider, customerView);
    }

    public InstallmentPageResponse list(InstallmentStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new CommerceException(400, "page >= 0, size từ 1–100, offset trong giới hạn");
        }
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("installmentId")));
        Page<InstallmentPayment> result = status == null ? installments.findAll(pageable) : installments.findByStatus(status, pageable);
        var rows = result.getContent();
        Map<UUID, Payment> paymentMap = index(payments.findAllById(rows.stream().map(InstallmentPayment::getPaymentId).toList()), Payment::getPaymentId);
        Map<UUID, Order> orderMap = index(orders.findAllById(paymentMap.values().stream().map(Payment::getOrderId).toList()), Order::getOrderId);
        Map<UUID, Customer> customerMap = index(customers.findAllById(orderMap.values().stream().map(Order::getCustomerId).toList()), Customer::getCustomerId);
        Map<UUID, InstallmentProvider> providerMap = index(providers.findAllById(rows.stream().map(InstallmentPayment::getProviderId).toList()), InstallmentProvider::getProviderId);
        var content = rows.stream().map(row -> {
            var payment = paymentMap.get(row.getPaymentId());
            var order = payment == null ? null : orderMap.get(payment.getOrderId());
            var customer = order == null ? null : customerMap.get(order.getCustomerId());
            var provider = providerMap.get(row.getProviderId());
            if (payment == null || order == null || customer == null || provider == null) {
                throw new CommerceException(409, "Hồ sơ trả góp có tham chiếu không hợp lệ");
            }
            return response(row, payment, order, customer, provider, false);
        }).toList();
        return new InstallmentPageResponse(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public InstallmentResponse detail(UUID id) {
        var row = installments.findById(id).orElseThrow(() -> new CommerceException(404, "Không tìm thấy hồ sơ trả góp"));
        var payment = payments.findById(row.getPaymentId()).orElseThrow(() -> new CommerceException(409, "Hồ sơ trả góp thiếu payment"));
        var order = orders.findById(payment.getOrderId()).orElseThrow(() -> new CommerceException(409, "Hồ sơ trả góp thiếu order"));
        var customer = customers.findById(order.getCustomerId()).orElseThrow(() -> new CommerceException(409, "Hồ sơ trả góp thiếu khách hàng"));
        var provider = providers.findById(row.getProviderId()).orElseThrow(() -> new CommerceException(409, "Hồ sơ trả góp thiếu đơn vị xử lý"));
        return response(row, payment, order, customer, provider, false);
    }

    @Transactional
    public InstallmentResponse updateStatus(UUID id, InstallmentStatus next) {
        var reference = installments.findById(id).orElseThrow(() -> new CommerceException(404, "Không tìm thấy hồ sơ trả góp"));
        var payment = payments.findById(reference.getPaymentId()).orElseThrow(() -> new CommerceException(409, "Hồ sơ trả góp thiếu payment"));
        var order = orders.lockById(payment.getOrderId()).orElseThrow(() -> new CommerceException(409, "Hồ sơ trả góp thiếu order"));
        var lockedPayments = payments.lockByOrderId(order.getOrderId());
        if (lockedPayments.size() != 1 || !lockedPayments.get(0).getPaymentId().equals(payment.getPaymentId())) {
            throw new CommerceException(409, "Đơn không có đúng một payment");
        }
        var row = installments.lockById(id).orElseThrow(() -> new CommerceException(404, "Không tìm thấy hồ sơ trả góp"));
        if (order.getStatus() != OrderStatus.PENDING) throw new CommerceException(409, "Chỉ xử lý trả góp khi đơn còn PENDING");
        if (!allowed(row.getStatus()).contains(next)) throw new CommerceException(409, "Không thể chuyển hồ sơ từ " + row.getStatus() + " sang " + next);
        row.setStatus(next); installments.saveAndFlush(row);
        return detail(id);
    }

    private InstallmentResponse response(InstallmentPayment row, Payment payment, Order order, Customer customer,
                                         InstallmentProvider provider, boolean customerView) {
        if (payment.getPaymentMethod() != PaymentMethod.INSTALLMENT) throw new CommerceException(409, "Hồ sơ trả góp gắn sai phương thức thanh toán");
        return new InstallmentResponse(row.getInstallmentId(), order.getOrderId(), order.getOrderCode(), order.getStatus(),
                customer.getCustomerId(), customer.getFullName(), provider.getProviderId(), provider.getProviderName(),
                row.getTotalAmount(), row.getDownPayment(), row.getRemainingAmount(), row.getTermMonths(), row.getStatus(),
                customerView || order.getStatus() != OrderStatus.PENDING ? List.of() : allowed(row.getStatus()));
    }

    private static List<InstallmentStatus> allowed(InstallmentStatus current) {
        return switch (current) {
            case PENDING -> List.of(InstallmentStatus.PROCESSING);
            case PROCESSING -> List.of(InstallmentStatus.APPROVED, InstallmentStatus.REJECTED);
            default -> List.of();
        };
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private InstallmentProviderResponse providerResponse(InstallmentProvider row) {
        return new InstallmentProviderResponse(row.getProviderId(), row.getProviderName(), row.getProviderCode(),
                row.getContactPhone(), row.getContactEmail(), row.getStatus());
    }
    private static <T> Map<UUID, T> index(List<T> rows, Function<T, UUID> id) {
        return rows.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
