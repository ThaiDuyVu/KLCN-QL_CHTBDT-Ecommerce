package com.example.backend.invoice.service;

import com.example.backend.auth.entity.Customer;
import com.example.backend.cart.repository.CustomerCartRepository;
import com.example.backend.inventory.serial.entity.SerialNumber;
import com.example.backend.inventory.serial.repository.SerialNumberRepository;
import com.example.backend.invoice.dto.InvoicePageResponse;
import com.example.backend.invoice.dto.InvoiceResponse;
import com.example.backend.invoice.dto.InvoiceSummary;
import com.example.backend.invoice.entity.Invoice;
import com.example.backend.invoice.entity.InvoiceStatus;
import com.example.backend.invoice.repository.InvoiceRepository;
import com.example.backend.order.entity.Order;
import com.example.backend.order.entity.OrderItem;
import com.example.backend.order.entity.Payment;
import com.example.backend.order.exception.CommerceException;
import com.example.backend.order.repository.OrderItemRepository;
import com.example.backend.order.repository.OrderRepository;
import com.example.backend.order.repository.PaymentRepository;
import com.example.backend.product.entity.ProductVariant;
import com.example.backend.product.repository.ProductVariantRepository;
import com.example.backend.returns.repository.ReturnRequestRepository;
import com.example.backend.returns.service.ReturnRefundListener;
import com.example.backend.warehouse.repository.WarehouseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class InvoiceServiceImpl implements InvoiceService, ReturnRefundListener {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final InvoiceRepository invoices;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final PaymentRepository payments;
    private final ProductVariantRepository variants;
    private final SerialNumberRepository serials;
    private final WarehouseRepository warehouses;
    private final CustomerCartRepository customers;
    private final ReturnRequestRepository returns;
    private final JdbcTemplate jdbc;

    public InvoiceServiceImpl(InvoiceRepository invoices, OrderRepository orders, OrderItemRepository orderItems,
                              PaymentRepository payments, ProductVariantRepository variants, SerialNumberRepository serials,
                              WarehouseRepository warehouses, CustomerCartRepository customers, ReturnRequestRepository returns,
                              JdbcTemplate jdbc) {
        this.invoices = invoices; this.orders = orders; this.orderItems = orderItems; this.payments = payments;
        this.variants = variants; this.serials = serials; this.warehouses = warehouses; this.customers = customers;
        this.returns = returns; this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public InvoiceSummary issueFor(Order order) {
        Optional<Invoice> existing = invoices.findByOrderId(order.getOrderId());
        if (existing.isPresent()) return summaries(List.of(existing.get())).get(0);
        // Serialises numbering across concurrent deliveries; released at commit.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext('invoice-number'))", rs -> { });
        String prefix = "HD-" + DAY.format(LocalDate.now()) + "-";
        int next = invoices.maxNumberLike(prefix + "%").map(max -> Integer.parseInt(max.substring(prefix.length())) + 1).orElse(1);
        Invoice invoice = new Invoice();
        invoice.setOrderId(order.getOrderId());
        invoice.setInvoiceNumber(prefix + String.format("%04d", next));
        invoice.setIssuedDate(OffsetDateTime.now());
        invoice.setTotalAmount(order.getTotalAmount());
        invoice.setStatus(InvoiceStatus.ISSUED);
        return summaries(List.of(invoices.saveAndFlush(invoice))).get(0);
    }

    /** All units returned and refunded: the sale is void, the invoice is kept but cancelled. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void orderFullyRefunded(Order order) {
        invoices.findByOrderId(order.getOrderId()).ifPresent(invoice -> {
            invoice.setStatus(InvoiceStatus.CANCELLED);
            invoices.saveAndFlush(invoice);
        });
    }

    @Override
    public Optional<InvoiceSummary> forOrder(UUID orderId) {
        return invoices.findByOrderId(orderId).map(invoice -> summaries(List.of(invoice)).get(0));
    }

    @Override
    public InvoicePageResponse list(String keyword, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new CommerceException(400, "page >= 0, size từ 1–100, offset trong giới hạn");
        }
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("issuedDate"), Sort.Order.desc("invoiceId")));
        String term = keyword == null ? "" : keyword.trim();
        Page<Invoice> result = term.isEmpty() ? invoices.findAll(pageable) : invoices.findByInvoiceNumberContainingIgnoreCase(term, pageable);
        return new InvoicePageResponse(summaries(result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public InvoiceResponse detail(UUID invoiceId) {
        return detail(invoices.findById(invoiceId).orElseThrow(this::notFound));
    }

    @Override
    public InvoiceResponse mineForOrder(UUID userId, UUID orderId) {
        UUID customerId = customers.findByUser_UserId(userId).map(Customer::getCustomerId)
                .orElseThrow(() -> new CommerceException(403, "Tài khoản chưa có hồ sơ khách hàng"));
        Order order = orders.findById(orderId).filter(o -> o.getCustomerId().equals(customerId))
                .orElseThrow(() -> new CommerceException(404, "Không tìm thấy đơn hàng"));
        return detail(invoices.findByOrderId(order.getOrderId())
                .orElseThrow(() -> new CommerceException(404, "Đơn hàng chưa có hóa đơn (hóa đơn được lập khi giao hàng thành công)")));
    }

    private InvoiceResponse detail(Invoice invoice) {
        Order order = orders.findById(invoice.getOrderId()).orElseThrow(this::notFound);
        List<OrderItem> items = orderItems.findByOrderIdOrderByVariantIdAscOrderItemIdAsc(order.getOrderId());
        Map<UUID, ProductVariant> variantById = variants.findByVariantIdIn(items.stream().map(OrderItem::getVariantId).distinct().toList())
                .stream().collect(Collectors.toMap(ProductVariant::getVariantId, Function.identity()));
        List<UUID> serialIds = items.stream().map(OrderItem::getSerialId).filter(Objects::nonNull).toList();
        Map<UUID, SerialNumber> serialById = serialIds.isEmpty() ? Map.of() : serials.findAllWithImeisByIdIn(serialIds).stream()
                .collect(Collectors.toMap(SerialNumber::getSerialId, Function.identity()));
        var warehouse = order.getWarehouseId() == null ? null : warehouses.findById(order.getWarehouseId()).orElse(null);
        Payment payment = payments.findByOrderId(order.getOrderId()).stream().findFirst().orElse(null);
        String customerName = customers.findById(order.getCustomerId()).map(Customer::getFullName).orElse(null);
        List<InvoiceResponse.Line> lines = items.stream().map(item -> {
            ProductVariant variant = variantById.get(item.getVariantId());
            SerialNumber serial = item.getSerialId() == null ? null : serialById.get(item.getSerialId());
            return new InvoiceResponse.Line(variant == null ? null : variant.getProduct().getProductName(),
                    variant == null ? null : variant.getSku(), serial == null ? null : serial.getSerialNumber(),
                    serial == null ? List.of() : serial.getImeis().stream().map(imei -> imei.getImeiNumber()).sorted().toList(),
                    item.getQuantity(), item.getUnitPrice(), item.getDiscountAmount(), item.getFinalUnitPrice(),
                    item.getFinalUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }).toList();
        return new InvoiceResponse(invoice.getInvoiceId(), invoice.getInvoiceNumber(), invoice.getStatus(), invoice.getIssuedDate(),
                order.getOrderId(), order.getOrderCode(), order.getOrderDate(),
                warehouse == null ? null : warehouse.getWarehouseName(), warehouse == null ? null : warehouse.getAddress(),
                customerName, order.getRecipientName(), order.getRecipientPhone(), order.getShippingAddress(),
                payment == null ? null : payment.getPaymentMethod(), payment == null ? null : payment.getStatus(),
                order.getSubtotal(), order.getDiscountAmount(), order.getShippingFee(), invoice.getTotalAmount(),
                returns.refundedAmount(order.getOrderId()), lines);
    }

    private List<InvoiceSummary> summaries(List<Invoice> rows) {
        if (rows.isEmpty()) return List.of();
        Map<UUID, Order> orderById = orders.findAllById(rows.stream().map(Invoice::getOrderId).toList()).stream()
                .collect(Collectors.toMap(Order::getOrderId, Function.identity()));
        Map<UUID, String> names = customers.findAllById(orderById.values().stream().map(Order::getCustomerId).distinct().toList())
                .stream().collect(Collectors.toMap(Customer::getCustomerId, Customer::getFullName));
        return rows.stream().map(invoice -> {
            Order order = orderById.get(invoice.getOrderId());
            return new InvoiceSummary(invoice.getInvoiceId(), invoice.getInvoiceNumber(), invoice.getOrderId(),
                    order == null ? null : order.getOrderCode(), order == null ? null : names.get(order.getCustomerId()),
                    invoice.getIssuedDate(), invoice.getTotalAmount(), invoice.getStatus());
        }).toList();
    }

    private CommerceException notFound() { return new CommerceException(404, "Không tìm thấy hóa đơn"); }
}
