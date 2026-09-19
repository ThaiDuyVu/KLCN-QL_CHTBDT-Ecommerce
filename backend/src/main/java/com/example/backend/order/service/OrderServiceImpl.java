package com.example.backend.order.service;
import com.example.backend.cart.repository.*;
import com.example.backend.cart.service.CartServiceImpl;
import com.example.backend.order.dto.*;
import com.example.backend.order.entity.*;
import com.example.backend.order.exception.CommerceException;
import com.example.backend.order.repository.*;
import com.example.backend.product.entity.ProductVariant;
import com.example.backend.product.repository.ProductVariantRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {
    private static final BigDecimal MAX_MONEY = new BigDecimal("9999999999999.99");
    private final CustomerCartRepository customers;
    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final PaymentRepository payments;
    private final ProductVariantRepository variants;
    private final OrderStockService stock;
    public OrderServiceImpl(CustomerCartRepository customers, CartRepository carts, CartItemRepository cartItems, OrderRepository orders,
                            OrderItemRepository items, PaymentRepository payments, ProductVariantRepository variants, OrderStockService stock) {
        this.customers = customers; this.carts = carts; this.cartItems = cartItems; this.orders = orders;
        this.items = items; this.payments = payments; this.variants = variants; this.stock = stock;
    }
    @Transactional
    public OrderResponse checkout(UUID userId, CheckoutRequest request) {
        if (request.getPaymentMethod() != PaymentMethod.COD) throw new CommerceException(400, "Phase hiện tại chỉ hỗ trợ COD");
        var customer = customers.lockByUserId(userId).orElseThrow(() -> new CommerceException(403, "Tài khoản chưa có hồ sơ customer"));
        var cart = carts.findFirstByCustomerIdAndStatusOrderByCreatedAtAscCartIdAsc(customer.getCustomerId(), "ACTIVE")
                .orElseThrow(() -> new CommerceException(409, "Cart rỗng, không thể checkout"));
        var links = cartItems.findByCartIdOrderByVariantIdAsc(cart.getCartId());
        if (links.isEmpty()) throw new CommerceException(409, "Cart rỗng, không thể checkout");
        var products = variantMap(links.stream().map(i -> i.getVariantId()).toList());
        BigDecimal subtotal = BigDecimal.ZERO;
        Map<UUID, Integer> quantities = new HashMap<>();
        for (var link : links) {
            var variant = products.get(link.getVariantId());
            if (variant == null) throw new CommerceException(409, "Variant không còn tồn tại");
            CartServiceImpl.ensurePurchasable(variant);
            subtotal = subtotal.add(variant.getPrice().multiply(BigDecimal.valueOf(link.getQuantity())));
            quantities.put(link.getVariantId(), link.getQuantity());
        }
        if (subtotal.compareTo(MAX_MONEY) > 0) throw new CommerceException(400, "Tổng tiền vượt giới hạn đơn hàng");
        stock.apply(quantities, OrderStockService.Action.RESERVE);
        var order = new Order(); order.setCustomerId(customer.getCustomerId());
        order.setOrderCode("ORD-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT)); order.setOrderDate(OffsetDateTime.now());
        order.setRecipientName(request.getRecipientName()); order.setRecipientPhone(request.getRecipientPhone());
        order.setShippingAddress(request.getShippingAddress()); order.setNote(request.getNote());
        order.setSubtotal(subtotal); order.setDiscountAmount(BigDecimal.ZERO); order.setShippingFee(BigDecimal.ZERO);
        order.setTotalAmount(subtotal); order.setStatus(OrderStatus.PENDING); orders.saveAndFlush(order);
        List<OrderItem> snapshots = new ArrayList<>();
        for (var link : links) {
            var variant = products.get(link.getVariantId()); var item = new OrderItem();
            item.setOrderId(order.getOrderId()); item.setVariantId(variant.getVariantId()); item.setQuantity(link.getQuantity());
            item.setCostPrice(variant.getCostPrice()); item.setUnitPrice(variant.getPrice()); item.setFinalUnitPrice(variant.getPrice());
            item.setDiscountAmount(BigDecimal.ZERO); snapshots.add(item);
        }
        items.saveAllAndFlush(snapshots);
        var payment = new Payment(); payment.setOrderId(order.getOrderId()); payment.setPaymentMethod(PaymentMethod.COD);
        payment.setAmount(subtotal); payment.setStatus(PaymentStatus.PENDING); payment.setTransactionCode(null); payment.setPaymentDate(OffsetDateTime.now());
        payments.saveAndFlush(payment);
        cartItems.deleteAll(links); cartItems.flush(); cart.setUpdatedAt(OffsetDateTime.now()); carts.save(cart);
        return full(order, true, snapshots, List.of(payment));
    }
    private UUID customerId(UUID userId) {
        return customers.findByUser_UserId(userId).orElseThrow(() -> new CommerceException(403, "Tài khoản chưa có hồ sơ customer")).getCustomerId();
    }
    public OrderPageResponse list(UUID userId, boolean customer, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) throw new CommerceException(400, "page >= 0, size từ 1–100, offset trong giới hạn");
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("orderDate"), Sort.Order.desc("orderId")));
        var result = customer ? orders.findByCustomerId(customerId(userId), pageable) : orders.findAll(pageable);
        var response = new OrderPageResponse(); response.setContent(result.getContent().stream().map(this::summary).toList());
        response.setPage(result.getNumber()); response.setSize(result.getSize()); response.setTotalElements(result.getTotalElements()); response.setTotalPages(result.getTotalPages());
        return response;
    }
    private void owned(Order order, UUID userId, boolean customer) {
        if (customer && !order.getCustomerId().equals(customerId(userId))) throw new CommerceException(404, "Không tìm thấy đơn hàng");
    }
    public OrderResponse detail(UUID userId, boolean customer, UUID id) {
        var order = orders.findById(id).orElseThrow(() -> new CommerceException(404, "Không tìm thấy đơn hàng"));
        owned(order, userId, customer);
        return full(order, customer, items.findByOrderIdOrderByVariantIdAsc(id), payments.findByOrderId(id));
    }
    @Transactional
    public OrderResponse status(UUID userId, boolean customer, UUID id, OrderStatus status) {
        var order = orders.lockById(id).orElseThrow(() -> new CommerceException(404, "Không tìm thấy đơn hàng"));
        owned(order, userId, customer);
        var paymentRows = payments.lockByOrderId(id);
        boolean paid = paymentRows.stream().anyMatch(p -> p.getStatus() == PaymentStatus.PAID);
        OrderTransitions.require(order.getStatus(), status, customer, paid);
        var snapshots = items.findByOrderIdOrderByVariantIdAsc(id);
        Map<UUID, Integer> quantities = new HashMap<>();
        for (var item : snapshots) quantities.merge(item.getVariantId(), item.getQuantity(), (a, b) -> {
            long sum = (long) a + b;
            if (sum > Integer.MAX_VALUE) throw new CommerceException(409, "Số lượng đơn vượt giới hạn");
            return (int) sum;
        });
        if (status == OrderStatus.CANCELLED) stock.apply(quantities, OrderStockService.Action.RELEASE);
        if (status == OrderStatus.DELIVERED) stock.apply(quantities, OrderStockService.Action.DELIVER);
        order.setStatus(status); orders.saveAndFlush(order);
        return full(order, customer, snapshots, paymentRows);
    }
    private Map<UUID, ProductVariant> variantMap(List<UUID> ids) {
        return variants.findByVariantIdIn(ids).stream().collect(Collectors.toMap(ProductVariant::getVariantId, v -> v));
    }
    private OrderResponse summary(Order o) {
        var r = new OrderResponse(); r.setOrderId(o.getOrderId()); r.setCustomerId(o.getCustomerId()); r.setOrderCode(o.getOrderCode()); r.setOrderDate(o.getOrderDate());
        r.setRecipientName(o.getRecipientName()); r.setRecipientPhone(o.getRecipientPhone()); r.setShippingAddress(o.getShippingAddress()); r.setNote(o.getNote());
        r.setSubtotal(o.getSubtotal()); r.setDiscountAmount(o.getDiscountAmount()); r.setShippingFee(o.getShippingFee()); r.setTotalAmount(o.getTotalAmount()); r.setStatus(o.getStatus()); return r;
    }
    private OrderResponse full(Order order, boolean customer, List<OrderItem> snapshots, List<Payment> paymentRows) {
        var r = summary(order); var products = variantMap(snapshots.stream().map(OrderItem::getVariantId).distinct().toList());
        r.setItems(snapshots.stream().map(item -> {
            var v = products.get(item.getVariantId()); var response = new OrderItemResponse();
            response.setOrderItemId(item.getOrderItemId()); response.setVariantId(item.getVariantId()); response.setQuantity(item.getQuantity());
            response.setUnitPrice(item.getUnitPrice()); response.setDiscountAmount(item.getDiscountAmount()); response.setFinalUnitPrice(item.getFinalUnitPrice());
            response.setSku(v == null ? null : v.getSku()); response.setProductName(v == null ? null : v.getProduct().getProductName()); return response;
        }).toList());
        if (paymentRows.size() != 1) throw new CommerceException(409, "Đơn không có đúng một payment; cần rà dữ liệu trước khi xử lý");
        var p = paymentRows.get(0); var pr = new PaymentResponse(); pr.setPaymentId(p.getPaymentId()); pr.setPaymentMethod(p.getPaymentMethod());
        pr.setStatus(p.getStatus()); pr.setAmount(p.getAmount()); pr.setTransactionCode(p.getTransactionCode()); r.setPayment(pr);
        r.setAllowedStatuses(OrderTransitions.allowed(order.getStatus(), customer, p.getStatus() == PaymentStatus.PAID)); return r;
    }
}
