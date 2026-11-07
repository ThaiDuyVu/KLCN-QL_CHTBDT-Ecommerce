package com.example.backend.order.service;

import com.example.backend.auth.entity.Customer;
import com.example.backend.cart.repository.CustomerCartRepository;
import com.example.backend.cart.service.CartServiceImpl;
import com.example.backend.common.branch.BranchScope;
import com.example.backend.inventory.serial.entity.SerialNumber;
import com.example.backend.inventory.serial.entity.SerialStatus;
import com.example.backend.inventory.serial.repository.SerialNumberRepository;
import com.example.backend.inventory.serial.service.SerialAllocationService;
import com.example.backend.invoice.service.InvoiceService;
import com.example.backend.order.dto.InStoreSaleRequest;
import com.example.backend.order.dto.OrderResponse;
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
import com.example.backend.warehouse.entity.WarehouseStatus;
import com.example.backend.warehouse.repository.WarehouseRepository;
import com.example.backend.warranty.service.WarrantyProvisioningService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InStoreSaleServiceImpl implements InStoreSaleService {
    private final CustomerCartRepository customers;
    private final WarehouseRepository warehouses;
    private final ProductVariantRepository variants;
    private final PromotionService promotions;
    private final OrderStockService stock;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final PaymentRepository payments;
    private final SerialNumberRepository serials;
    private final SerialAllocationService serialAllocation;
    private final WarrantyProvisioningService warranties;
    private final InvoiceService invoices;
    private final OrderHistory history;
    private final BranchScope branch;
    private final OrderService orderService;

    public InStoreSaleServiceImpl(CustomerCartRepository customers, WarehouseRepository warehouses, ProductVariantRepository variants,
                                  PromotionService promotions, OrderStockService stock, OrderRepository orders, OrderItemRepository items,
                                  PaymentRepository payments, SerialNumberRepository serials, SerialAllocationService serialAllocation,
                                  WarrantyProvisioningService warranties, InvoiceService invoices, OrderHistory history,
                                  BranchScope branch, OrderService orderService) {
        this.customers = customers; this.warehouses = warehouses; this.variants = variants; this.promotions = promotions;
        this.stock = stock; this.orders = orders; this.items = items; this.payments = payments; this.serials = serials;
        this.serialAllocation = serialAllocation; this.warranties = warranties; this.invoices = invoices; this.history = history;
        this.branch = branch; this.orderService = orderService;
    }

    @Override
    @Transactional
    public OrderResponse sell(UUID userId, InStoreSaleRequest request, Collection<String> authorities) {
        UUID warehouseId = request.warehouseId() != null ? request.warehouseId()
                : branch.current().orElseThrow(() -> new CommerceException(400, "Vui lòng chọn chi nhánh bán hàng"));
        if (!branch.allows(warehouseId)) throw new CommerceException(403, "Bạn chỉ bán hàng tại chi nhánh của mình");
        var warehouse = warehouses.findById(warehouseId).orElseThrow(() -> new CommerceException(404, "Không tìm thấy chi nhánh"));
        if (warehouse.getStatus() != WarehouseStatus.ACTIVE) throw new CommerceException(409, "Chi nhánh đã ngừng hoạt động");
        Customer customer = customers.findById(request.customerId()).orElseThrow(() -> new CommerceException(404, "Không tìm thấy khách hàng"));

        // One line per variant; serial numbers given for a variant must match its quantity.
        Map<UUID, Integer> quantities = new TreeMap<>();
        Map<UUID, List<String>> chosenSerials = new HashMap<>();
        for (var line : request.items()) {
            quantities.merge(line.variantId(), line.quantity(), Integer::sum);
            if (line.serialNumbers() != null && !line.serialNumbers().isEmpty()) {
                chosenSerials.computeIfAbsent(line.variantId(), k -> new ArrayList<>())
                        .addAll(line.serialNumbers().stream().map(String::trim).toList());
            }
        }
        Map<UUID, ProductVariant> variantById = variants.findByVariantIdIn(new ArrayList<>(quantities.keySet())).stream()
                .collect(Collectors.toMap(ProductVariant::getVariantId, Function.identity()));
        var pricing = promotions.resolveForCheckout(variantById.values().stream().map(v -> v.getProduct().getProductId()).distinct().toList(),
                OffsetDateTime.now());
        BigDecimal subtotal = BigDecimal.ZERO, discount = BigDecimal.ZERO;
        Map<UUID, PromotionPrice> prices = new HashMap<>();
        for (var entry : quantities.entrySet()) {
            ProductVariant variant = variantById.get(entry.getKey());
            if (variant == null) throw new CommerceException(404, "Không tìm thấy sản phẩm " + entry.getKey());
            CartServiceImpl.ensurePurchasable(variant);
            List<String> given = chosenSerials.get(variant.getVariantId());
            if (given != null && (variant.getTrackingType() == ProductTrackingType.NONE || given.size() != entry.getValue()
                    || new HashSet<>(given).size() != given.size())) {
                throw new CommerceException(400, "Số serial của " + variant.getSku() + " phải khác nhau và khớp số lượng " + entry.getValue());
            }
            PromotionPrice price = promotions.calculate(variant.getPrice(), pricing.get(variant.getProduct().getProductId()));
            prices.put(variant.getVariantId(), price);
            subtotal = subtotal.add(price.getUnitPrice().multiply(BigDecimal.valueOf(entry.getValue())));
            discount = discount.add(price.getDiscountAmount().multiply(BigDecimal.valueOf(entry.getValue())));
        }
        BigDecimal total = subtotal.subtract(discount);

        stock.apply(warehouseId, quantities, OrderStockService.Action.RESERVE);
        Order order = new Order();
        order.setCustomerId(customer.getCustomerId());
        order.setWarehouseId(warehouseId);
        order.setSalesChannel(SalesChannel.IN_STORE);
        order.setOrderCode("POS-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
        order.setOrderDate(OffsetDateTime.now());
        order.setRecipientName(customer.getFullName());
        String phone = customer.getUser() != null && customer.getUser().getPhone() != null ? customer.getUser().getPhone() : customer.getPhone();
        order.setRecipientPhone(phone == null ? "" : phone);
        order.setShippingAddress("Nhận tại cửa hàng: " + warehouse.getWarehouseName());
        order.setNote(request.note() == null || request.note().isBlank() ? null : request.note().strip());
        order.setSubtotal(subtotal); order.setDiscountAmount(discount); order.setShippingFee(BigDecimal.ZERO); order.setTotalAmount(total);
        order.setStatus(OrderStatus.DELIVERED);
        orders.saveAndFlush(order);

        List<OrderItem> lines = new ArrayList<>();
        List<UUID> soldSerials = new ArrayList<>();
        for (var entry : quantities.entrySet()) {
            ProductVariant variant = variantById.get(entry.getKey());
            boolean tracked = variant.getTrackingType() != ProductTrackingType.NONE;
            List<UUID> units = tracked ? units(variant, warehouseId, entry.getValue(), chosenSerials.get(variant.getVariantId())) : List.of();
            int rows = tracked ? entry.getValue() : 1;
            for (int index = 0; index < rows; index++) {
                OrderItem item = new OrderItem();
                PromotionPrice price = prices.get(variant.getVariantId());
                item.setOrderId(order.getOrderId()); item.setVariantId(variant.getVariantId());
                item.setQuantity(tracked ? 1 : entry.getValue());
                item.setCostPrice(variant.getCostPrice()); item.setUnitPrice(price.getUnitPrice());
                item.setDiscountAmount(price.getDiscountAmount()); item.setFinalUnitPrice(price.getFinalUnitPrice());
                if (tracked) item.setSerialId(units.get(index));
                lines.add(item);
            }
            soldSerials.addAll(units);
        }
        items.saveAllAndFlush(lines);
        stock.apply(warehouseId, quantities, OrderStockService.Action.DELIVER);
        serialAllocation.markSold(soldSerials);

        Payment payment = new Payment();
        payment.setOrderId(order.getOrderId()); payment.setPaymentMethod(PaymentMethod.CASH);
        payment.setAmount(total); payment.setStatus(PaymentStatus.PAID); payment.setPaymentDate(OffsetDateTime.now());
        payments.saveAndFlush(payment);

        warranties.createForDeliveredOrder(order, lines);
        invoices.issueFor(order);
        history.record(order.getOrderId(), null, OrderStatus.DELIVERED, userId, "Bán tại quầy, khách thanh toán tiền mặt");
        return orderService.staffDetail(order.getOrderId(), authorities);
    }

    /** Devices handed over: the scanned serials, or available units of the branch. Leaves them RESERVED. */
    private List<UUID> units(ProductVariant variant, UUID warehouseId, int quantity, List<String> serialNumbers) {
        if (serialNumbers == null) return serialAllocation.reserveAvailable(variant.getVariantId(), warehouseId, quantity);
        List<UUID> ids = new ArrayList<>();
        for (String number : serialNumbers) {
            SerialNumber serial = serials.findBySerialNumber(number)
                    .orElseThrow(() -> new CommerceException(404, "Không tìm thấy serial " + number));
            ids.add(serial.getSerialId());
        }
        for (SerialNumber serial : serials.findAllByIdForUpdate(ids)) {
            if (!serial.getVariant().getVariantId().equals(variant.getVariantId()) || !serial.getWarehouse().getWarehouseId().equals(warehouseId)
                    || serial.getStatus() != SerialStatus.AVAILABLE) {
                throw new CommerceException(409, "Serial " + serial.getSerialNumber() + " không có sẵn để bán tại chi nhánh này");
            }
            serial.setStatus(SerialStatus.RESERVED);
        }
        serials.flush();
        return ids;
    }
}
