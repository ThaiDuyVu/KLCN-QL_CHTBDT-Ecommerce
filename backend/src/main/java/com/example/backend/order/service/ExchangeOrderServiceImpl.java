package com.example.backend.order.service;

import com.example.backend.cart.service.CartServiceImpl;
import com.example.backend.inventory.serial.service.SerialAllocationService;
import com.example.backend.invoice.service.InvoiceService;
import com.example.backend.order.entity.*;
import com.example.backend.order.exception.CommerceException;
import com.example.backend.order.repository.OrderItemRepository;
import com.example.backend.order.repository.OrderRepository;
import com.example.backend.order.repository.PaymentRepository;
import com.example.backend.product.entity.ProductTrackingType;
import com.example.backend.product.entity.ProductVariant;
import com.example.backend.product.repository.ProductVariantRepository;
import com.example.backend.promotion.service.PromotionPrice;
import com.example.backend.promotion.service.PromotionService;
import com.example.backend.warranty.service.WarrantyProvisioningService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExchangeOrderServiceImpl implements ExchangeOrderService {
    private final ProductVariantRepository variants;
    private final PromotionService promotions;
    private final OrderStockService stock;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final PaymentRepository payments;
    private final SerialAllocationService serialAllocation;
    private final WarrantyProvisioningService warranties;
    private final InvoiceService invoices;
    private final OrderHistory history;

    public ExchangeOrderServiceImpl(ProductVariantRepository variants, PromotionService promotions, OrderStockService stock,
                                    OrderRepository orders, OrderItemRepository items, PaymentRepository payments,
                                    SerialAllocationService serialAllocation, WarrantyProvisioningService warranties,
                                    InvoiceService invoices, OrderHistory history) {
        this.variants = variants; this.promotions = promotions; this.stock = stock; this.orders = orders; this.items = items;
        this.payments = payments; this.serialAllocation = serialAllocation; this.warranties = warranties;
        this.invoices = invoices; this.history = history;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Result create(Order original, String returnCode, BigDecimal credit, Map<UUID, Integer> requested, UUID actorUserId) {
        Map<UUID, Integer> quantities = new TreeMap<>(requested);
        Map<UUID, ProductVariant> variantById = variants.findByVariantIdIn(new ArrayList<>(quantities.keySet())).stream()
                .collect(Collectors.toMap(ProductVariant::getVariantId, Function.identity()));
        var pricing = promotions.resolveForCheckout(variantById.values().stream().map(v -> v.getProduct().getProductId()).distinct().toList(),
                OffsetDateTime.now());
        BigDecimal subtotal = BigDecimal.ZERO, discount = BigDecimal.ZERO;
        Map<UUID, PromotionPrice> prices = new HashMap<>();
        for (var entry : quantities.entrySet()) {
            ProductVariant variant = variantById.get(entry.getKey());
            if (variant == null) throw new CommerceException(409, "Sản phẩm đổi không còn tồn tại");
            CartServiceImpl.ensurePurchasable(variant);
            PromotionPrice price = promotions.calculate(variant.getPrice(), pricing.get(variant.getProduct().getProductId()));
            prices.put(variant.getVariantId(), price);
            subtotal = subtotal.add(price.getUnitPrice().multiply(BigDecimal.valueOf(entry.getValue())));
            discount = discount.add(price.getDiscountAmount().multiply(BigDecimal.valueOf(entry.getValue())));
        }
        BigDecimal total = subtotal.subtract(discount);
        BigDecimal difference = total.subtract(credit);
        BigDecimal toCollect = difference.signum() > 0 ? difference : BigDecimal.ZERO;
        boolean counter = original.getSalesChannel() == SalesChannel.IN_STORE;

        stock.apply(original.getWarehouseId(), quantities, OrderStockService.Action.RESERVE);
        Order order = new Order();
        order.setCustomerId(original.getCustomerId());
        order.setWarehouseId(original.getWarehouseId());
        order.setSalesChannel(original.getSalesChannel());
        order.setOrderCode("EX-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
        order.setOrderDate(OffsetDateTime.now());
        order.setRecipientName(original.getRecipientName());
        order.setRecipientPhone(original.getRecipientPhone());
        order.setShippingAddress(original.getShippingAddress());
        order.setNote("Đơn đổi hàng của yêu cầu " + returnCode + " (đơn gốc " + original.getOrderCode() + ")");
        order.setSubtotal(subtotal); order.setDiscountAmount(discount); order.setShippingFee(BigDecimal.ZERO); order.setTotalAmount(total);
        order.setStatus(counter ? OrderStatus.DELIVERED : OrderStatus.CONFIRMED);
        orders.saveAndFlush(order);

        List<OrderItem> lines = new ArrayList<>();
        List<UUID> sold = new ArrayList<>();
        for (var entry : quantities.entrySet()) {
            ProductVariant variant = variantById.get(entry.getKey());
            boolean tracked = variant.getTrackingType() != ProductTrackingType.NONE;
            // Online: devices are picked when the warehouse starts processing, as for any order.
            List<UUID> units = tracked && counter
                    ? serialAllocation.reserveAvailable(variant.getVariantId(), original.getWarehouseId(), entry.getValue()) : List.of();
            int rows = tracked ? entry.getValue() : 1;
            PromotionPrice price = prices.get(variant.getVariantId());
            for (int index = 0; index < rows; index++) {
                OrderItem item = new OrderItem();
                item.setOrderId(order.getOrderId()); item.setVariantId(variant.getVariantId());
                item.setQuantity(tracked ? 1 : entry.getValue());
                item.setCostPrice(variant.getCostPrice()); item.setUnitPrice(price.getUnitPrice());
                item.setDiscountAmount(price.getDiscountAmount()); item.setFinalUnitPrice(price.getFinalUnitPrice());
                if (!units.isEmpty()) item.setSerialId(units.get(index));
                lines.add(item);
            }
            sold.addAll(units);
        }
        items.saveAllAndFlush(lines);

        Payment payment = new Payment();
        payment.setOrderId(order.getOrderId());
        payment.setPaymentMethod(counter ? PaymentMethod.CASH : PaymentMethod.COD);
        payment.setAmount(toCollect);
        // Nothing left to collect: settled by the returned goods.
        payment.setStatus(counter || toCollect.signum() == 0 ? PaymentStatus.PAID : PaymentStatus.PENDING);
        payment.setPaymentDate(OffsetDateTime.now());
        payments.saveAndFlush(payment);

        String note = "Đổi hàng từ yêu cầu " + returnCode;
        if (counter) {
            stock.apply(original.getWarehouseId(), quantities, OrderStockService.Action.DELIVER);
            serialAllocation.markSold(sold);
            warranties.createForDeliveredOrder(order, lines);
            invoices.issueFor(order);
            history.record(order.getOrderId(), null, OrderStatus.DELIVERED, actorUserId, note + ", giao tại quầy");
        } else {
            history.record(order.getOrderId(), null, OrderStatus.CONFIRMED, actorUserId, note);
        }
        return new Result(order, difference);
    }
}
