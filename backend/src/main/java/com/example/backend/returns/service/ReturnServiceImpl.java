package com.example.backend.returns.service;

import com.example.backend.auth.entity.Customer;
import com.example.backend.auth.entity.Employee;
import com.example.backend.auth.repository.EmployeeRepository;
import com.example.backend.cart.repository.CustomerCartRepository;
import com.example.backend.inventory.repository.InventoryRepository;
import com.example.backend.inventory.serial.entity.SerialNumber;
import com.example.backend.inventory.serial.entity.SerialStatus;
import com.example.backend.inventory.serial.repository.SerialNumberRepository;
import com.example.backend.order.entity.*;
import com.example.backend.order.exception.CommerceException;
import com.example.backend.order.repository.*;
import com.example.backend.product.entity.ProductVariant;
import com.example.backend.product.repository.ProductVariantRepository;
import com.example.backend.returns.dto.*;
import com.example.backend.returns.entity.*;
import com.example.backend.returns.repository.ReturnItemRepository;
import com.example.backend.returns.repository.ReturnRequestRepository;
import com.example.backend.warranty.entity.WarrantyStatus;
import com.example.backend.warranty.repository.WarrantyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReturnServiceImpl implements ReturnService {
    /** Return window counted from the delivery date (store policy, decision Q2 of the plan). */
    public static final int RETURN_WINDOW_DAYS = 7;
    private static final Set<String> SALES = Set.of("ADMIN", "MANAGER", "STAFF", "SALES_STAFF");
    private static final Set<String> WAREHOUSE = Set.of("ADMIN", "MANAGER", "STAFF", "WAREHOUSE_KEEPER");
    private static final Set<String> FINANCE = Set.of("ADMIN", "MANAGER");
    /** Every status that still holds the items: only a rejection frees them for a new request. */
    private static final List<ReturnStatus> ACTIVE = List.of(ReturnStatus.REQUESTED, ReturnStatus.APPROVED, ReturnStatus.RECEIVED,
            ReturnStatus.REFUNDED, ReturnStatus.EXCHANGED);

    private final ReturnRequestRepository returns;
    private final ReturnItemRepository returnItems;
    private final CustomerCartRepository customers;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final ShipmentRepository shipments;
    private final PaymentRepository payments;
    private final ProductVariantRepository variants;
    private final SerialNumberRepository serials;
    private final InventoryRepository inventory;
    private final WarrantyRepository warranties;
    private final EmployeeRepository employees;
    private final org.springframework.beans.factory.ObjectProvider<ReturnRefundListener> refundListeners;
    private final com.example.backend.common.branch.BranchScope branch;
    private final com.example.backend.returns.repository.ReturnExchangeItemRepository exchangeItems;
    private final com.example.backend.order.service.ExchangeOrderService exchangeOrders;

    public ReturnServiceImpl(ReturnRequestRepository returns, ReturnItemRepository returnItems, CustomerCartRepository customers,
                             OrderRepository orders, OrderItemRepository orderItems, ShipmentRepository shipments,
                             PaymentRepository payments, ProductVariantRepository variants, SerialNumberRepository serials,
                             InventoryRepository inventory, WarrantyRepository warranties, EmployeeRepository employees,
                             org.springframework.beans.factory.ObjectProvider<ReturnRefundListener> refundListeners,
                             com.example.backend.common.branch.BranchScope branch,
                             com.example.backend.returns.repository.ReturnExchangeItemRepository exchangeItems,
                             com.example.backend.order.service.ExchangeOrderService exchangeOrders) {
        this.branch = branch;
        this.exchangeItems = exchangeItems;
        this.exchangeOrders = exchangeOrders;
        this.returns = returns; this.returnItems = returnItems; this.customers = customers; this.orders = orders;
        this.orderItems = orderItems; this.shipments = shipments; this.payments = payments; this.variants = variants;
        this.serials = serials; this.inventory = inventory; this.warranties = warranties; this.employees = employees;
        this.refundListeners = refundListeners;
    }

    // ---------------------------------------------------------------- customer

    @Override
    @Transactional
    public ReturnResponse create(UUID userId, CreateReturnRequest request) {
        Customer customer = customer(userId);
        Order order = orders.lockById(request.orderId()).orElseThrow(() -> new CommerceException(404, "Không tìm thấy đơn hàng"));
        if (!order.getCustomerId().equals(customer.getCustomerId())) throw new CommerceException(404, "Không tìm thấy đơn hàng");
        if (order.getStatus() != OrderStatus.DELIVERED) throw new CommerceException(409, "Chỉ đơn hàng đã giao mới được yêu cầu đổi trả");
        OffsetDateTime deliveredAt = shipments.findFirstByOrderIdOrderByShippedDateDesc(order.getOrderId())
                .map(Shipment::getDeliveredDate).filter(Objects::nonNull).orElse(order.getOrderDate());
        if (OffsetDateTime.now().isAfter(deliveredAt.plusDays(RETURN_WINDOW_DAYS))) {
            throw new CommerceException(409, "Đã quá " + RETURN_WINDOW_DAYS + " ngày kể từ ngày nhận hàng, không thể đổi trả");
        }
        Map<UUID, OrderItem> lines = orderItems.findByOrderIdOrderByVariantIdAscOrderItemIdAsc(order.getOrderId()).stream()
                .collect(Collectors.toMap(OrderItem::getOrderItemId, Function.identity()));
        Map<UUID, Long> claimed = quantities(order.getOrderId(), ACTIVE);
        Set<UUID> seen = new HashSet<>();
        BigDecimal refund = BigDecimal.ZERO;
        for (var line : request.items()) {
            OrderItem item = lines.get(line.orderItemId());
            if (item == null) throw new CommerceException(400, "Sản phẩm không thuộc đơn hàng này");
            if (!seen.add(line.orderItemId())) throw new CommerceException(400, "Mỗi sản phẩm chỉ được chọn một lần");
            long remaining = item.getQuantity() - claimed.getOrDefault(item.getOrderItemId(), 0L);
            if (line.quantity() > remaining) {
                throw new CommerceException(409, remaining <= 0 ? "Sản phẩm đã có yêu cầu đổi trả" : "Chỉ còn " + remaining + " sản phẩm có thể trả");
            }
            refund = refund.add(item.getFinalUnitPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        ReturnType type = request.type() == null ? ReturnType.RETURN : request.type();
        Map<UUID, Integer> wanted = exchangeLines(type, request.exchangeItems(), order.getWarehouseId());
        ReturnRequest row = new ReturnRequest();
        row.setRequestType(type);
        row.setReturnCode(nextCode());
        row.setOrderId(order.getOrderId());
        row.setCustomerId(customer.getCustomerId());
        row.setReason(request.reason().strip());
        row.setRequestDate(OffsetDateTime.now());
        row.setRefundAmount(refund);
        row.setStatus(ReturnStatus.REQUESTED);
        row = returns.saveAndFlush(row);
        List<ReturnItem> created = new ArrayList<>();
        for (var line : request.items()) {
            ReturnItem item = new ReturnItem();
            item.setReturnId(row.getReturnId());
            item.setOrderItemId(line.orderItemId());
            item.setSerialId(lines.get(line.orderItemId()).getSerialId());
            item.setQuantity(line.quantity());
            item.setReason(line.reason() == null || line.reason().isBlank() ? null : line.reason().strip());
            created.add(item);
        }
        returnItems.saveAllAndFlush(created);
        UUID returnId = row.getReturnId();
        exchangeItems.saveAllAndFlush(wanted.entrySet().stream().map(entry -> {
            ReturnExchangeItem item = new ReturnExchangeItem();
            item.setReturnId(returnId); item.setVariantId(entry.getKey()); item.setQuantity(entry.getValue());
            return item;
        }).toList());
        return map(List.of(row), Set.of()).get(0);
    }

    @Override
    public ReturnPageResponse mine(UUID userId, int page, int size) {
        return page(returns.findByCustomerId(customer(userId).getCustomerId(), pageable(page, size)), Set.of());
    }

    @Override
    public ReturnResponse myReturn(UUID userId, UUID returnId) {
        ReturnRequest row = returns.findById(returnId).orElseThrow(this::notFound);
        if (!row.getCustomerId().equals(customer(userId).getCustomerId())) throw notFound();
        return map(List.of(row), Set.of()).get(0);
    }

    @Override
    public List<ReturnResponse> forOrder(UUID userId, UUID orderId) {
        UUID customerId = customer(userId).getCustomerId();
        return map(returns.findByOrderIdOrderByRequestDateDesc(orderId).stream()
                .filter(row -> row.getCustomerId().equals(customerId)).toList(), Set.of());
    }

    // ---------------------------------------------------------------- staff

    @Override
    public ReturnPageResponse list(ReturnStatus status, int page, int size, Collection<String> authorities) {
        var pageable = pageable(page, size);
        Optional<UUID> mine = branch.current();
        if (mine.isPresent()) return page(returns.findByBranch(mine.get(), status, pageable), Set.copyOf(authorities));
        return page(status == null ? returns.findAll(pageable) : returns.findByStatus(status, pageable), Set.copyOf(authorities));
    }

    @Override
    public ReturnResponse detail(UUID returnId, Collection<String> authorities) {
        ReturnRequest row = returns.findById(returnId).orElseThrow(this::notFound);
        requireBranch(row);
        return map(List.of(row), Set.copyOf(authorities)).get(0);
    }

    @Override
    @Transactional
    public ReturnResponse updateStatus(UUID userId, UUID returnId, UpdateReturnStatusRequest request, Collection<String> authorities) {
        Set<String> roles = Set.copyOf(authorities);
        ReturnRequest row = returns.findById(returnId).orElseThrow(this::notFound);
        // Same lock order as order transitions: order first, then the return request.
        Order order = orders.lockById(row.getOrderId()).orElseThrow(this::notFound);
        if (!branch.allows(order.getWarehouseId())) throw notFound();
        row = returns.lockById(returnId).orElseThrow(this::notFound);
        ReturnStatus next = request.status();
        if (!transitions(row.getStatus(), row.getRequestType()).contains(next)) {
            throw new CommerceException(409, "Không thể chuyển yêu cầu đổi trả từ " + row.getStatus() + " sang " + next);
        }
        if (!allowed(next, roles)) throw new CommerceException(403, "Chức danh của bạn không được chuyển yêu cầu sang " + next);
        Employee actor = employees.findByUser_UserId(userId)
                .orElseThrow(() -> new CommerceException(403, "Tài khoản chưa có hồ sơ nhân viên"));
        String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
        List<ReturnItem> lines = returnItems.findByReturnIdIn(List.of(returnId));
        switch (next) {
            case REJECTED -> {
                if (note == null) throw new CommerceException(400, "Vui lòng nhập lý do từ chối");
                row.setRejectionReason(note);
                row.setProcessedAt(OffsetDateTime.now());
            }
            case RECEIVED -> receive(order, lines, request.conditions());
            case REFUNDED -> {
                row.setProcessedAt(OffsetDateTime.now());
                row.setStatus(ReturnStatus.REFUNDED);
                returns.saveAndFlush(row);
                refundIfFullyReturned(order);
            }
            case EXCHANGED -> {
                Map<UUID, Integer> wanted = new TreeMap<>();
                exchangeItems.findByReturnIdIn(List.of(row.getReturnId())).forEach(i -> wanted.merge(i.getVariantId(), i.getQuantity(), Integer::sum));
                var result = exchangeOrders.create(order, row.getReturnCode(), row.getRefundAmount(), wanted, userId);
                row.setExchangeOrderId(result.order().getOrderId());
                row.setExchangeDifference(result.difference());
                row.setProcessedAt(OffsetDateTime.now());
                row.setStatus(ReturnStatus.EXCHANGED);
                returns.saveAndFlush(row);
                refundIfFullyReturned(order);
            }
            default -> { }
        }
        row.setEmployeeId(actor.getEmployeeId());
        row.setStatus(next);
        returns.saveAndFlush(row);
        return map(List.of(row), roles).get(0);
    }

    /** Goods are back at the order's branch: re-stock or set aside each line and end the device warranty. */
    private void receive(Order order, List<ReturnItem> lines, List<UpdateReturnStatusRequest.LineCondition> conditions) {
        Map<UUID, ReturnCondition> byLine = new HashMap<>();
        if (conditions != null) conditions.forEach(c -> byLine.put(c.returnItemId(), c.condition()));
        if (lines.stream().anyMatch(line -> !byLine.containsKey(line.getReturnItemId())) || byLine.size() != lines.size()) {
            throw new CommerceException(400, "Vui lòng chọn tình trạng cho từng sản phẩm nhận lại");
        }
        Map<UUID, OrderItem> orderLines = orderItems.findByOrderIdOrderByVariantIdAscOrderItemIdAsc(order.getOrderId()).stream()
                .collect(Collectors.toMap(OrderItem::getOrderItemId, Function.identity()));
        List<UUID> serialIds = lines.stream().map(ReturnItem::getSerialId).filter(Objects::nonNull).toList();
        Map<UUID, SerialNumber> serialRows = serialIds.isEmpty() ? Map.of() : serials.findAllByIdForUpdate(serialIds).stream()
                .collect(Collectors.toMap(SerialNumber::getSerialId, Function.identity()));
        // Lock inventory rows in a stable order (same convention as order stock updates).
        Map<UUID, Integer> restock = new TreeMap<>();
        for (ReturnItem line : lines) {
            ReturnCondition condition = byLine.get(line.getReturnItemId());
            if (condition == ReturnCondition.RESTOCK) {
                restock.merge(orderLines.get(line.getOrderItemId()).getVariantId(), line.getQuantity(), Integer::sum);
            }
            if (line.getSerialId() != null) {
                SerialNumber serial = serialRows.get(line.getSerialId());
                if (serial == null || serial.getStatus() != SerialStatus.SOLD) {
                    throw new CommerceException(409, "Thiết bị " + (serial == null ? line.getSerialId() : serial.getSerialNumber()) + " không ở trạng thái đã bán");
                }
                serial.setStatus(condition == ReturnCondition.RESTOCK ? SerialStatus.AVAILABLE : SerialStatus.DEFECTIVE);
                // The original period is kept for the record; VOIDED stops any new warranty ticket.
                warranties.findBySerialId(line.getSerialId()).ifPresent(warranty -> {
                    warranty.setStatus(WarrantyStatus.VOIDED);
                    warranties.save(warranty);
                });
            }
            line.setReceivedCondition(condition);
        }
        for (var entry : restock.entrySet()) {
            var row = inventory.lockByWarehouseAndVariant(order.getWarehouseId(), entry.getKey())
                    .orElseThrow(() -> new CommerceException(409, "Không có tồn kho của sản phẩm tại chi nhánh của đơn"));
            row.setQuantity(row.getQuantity() + entry.getValue());
            row.setUpdatedAt(OffsetDateTime.now());
        }
        if (!serialRows.isEmpty()) serials.saveAll(serialRows.values());
        returnItems.saveAllAndFlush(lines);
        inventory.flush();
    }

    /** When every unit of the order has been refunded, the whole payment is refunded. */
    private void refundIfFullyReturned(Order order) {
        List<OrderItem> lines = orderItems.findByOrderIdOrderByVariantIdAscOrderItemIdAsc(order.getOrderId());
        Map<UUID, Long> settled = quantities(order.getOrderId(), List.of(ReturnStatus.REFUNDED, ReturnStatus.EXCHANGED));
        if (!lines.stream().allMatch(item -> settled.getOrDefault(item.getOrderItemId(), 0L) >= item.getQuantity())) return;
        // Every unit came back: the sale is void (invoice cancelled). The payment is refunded only when no part
        // of it was carried over as credit to a replacement order.
        Map<UUID, Long> refunded = quantities(order.getOrderId(), List.of(ReturnStatus.REFUNDED));
        if (lines.stream().allMatch(item -> refunded.getOrDefault(item.getOrderItemId(), 0L) >= item.getQuantity())) {
            var paymentRows = payments.lockByOrderId(order.getOrderId());
            for (Payment payment : paymentRows) if (payment.getStatus() == PaymentStatus.PAID) payment.setStatus(PaymentStatus.REFUNDED);
            payments.saveAllAndFlush(paymentRows);
        }
        refundListeners.orderedStream().forEach(listener -> listener.orderFullyRefunded(order));
    }

    /** Validates the products wanted in exchange: purchasable and in stock at the order's branch today. */
    private Map<UUID, Integer> exchangeLines(ReturnType type, List<CreateReturnRequest.ExchangeLine> requested, UUID warehouseId) {
        boolean any = requested != null && !requested.isEmpty();
        if (type == ReturnType.RETURN) {
            if (any) throw new CommerceException(400, "Yêu cầu trả hàng không kèm sản phẩm đổi");
            return Map.of();
        }
        if (!any) throw new CommerceException(400, "Vui lòng chọn sản phẩm muốn đổi sang");
        Map<UUID, Integer> wanted = new TreeMap<>();
        requested.forEach(line -> wanted.merge(line.variantId(), line.quantity(), Integer::sum));
        Map<UUID, ProductVariant> variantById = variants.findByVariantIdIn(new ArrayList<>(wanted.keySet())).stream()
                .collect(Collectors.toMap(ProductVariant::getVariantId, Function.identity()));
        for (var entry : wanted.entrySet()) {
            ProductVariant variant = variantById.get(entry.getKey());
            if (variant == null) throw new CommerceException(404, "Không tìm thấy sản phẩm muốn đổi");
            com.example.backend.cart.service.CartServiceImpl.ensurePurchasable(variant);
            var stockRow = inventory.findByWarehouse_WarehouseIdAndVariant_VariantId(warehouseId, variant.getVariantId());
            long available = stockRow.map(r -> (long) r.getQuantity() - r.getReservedQuantity()).orElse(0L);
            if (available < entry.getValue()) {
                throw new CommerceException(409, variant.getSku() + " không đủ hàng tại chi nhánh của đơn để đổi");
            }
        }
        return wanted;
    }

    static List<ReturnStatus> transitions(ReturnStatus current, ReturnType type) {
        return switch (current) {
            case REQUESTED -> List.of(ReturnStatus.APPROVED, ReturnStatus.REJECTED);
            case APPROVED -> List.of(ReturnStatus.RECEIVED, ReturnStatus.REJECTED);
            // Money back for a return; a replacement order for an exchange.
            case RECEIVED -> List.of(type == ReturnType.EXCHANGE ? ReturnStatus.EXCHANGED : ReturnStatus.REFUNDED);
            default -> List.of();
        };
    }

    static boolean allowed(ReturnStatus next, Set<String> roles) {
        Set<String> who = switch (next) {
            case APPROVED, REJECTED -> SALES;
            case RECEIVED -> WAREHOUSE;
            case REFUNDED -> FINANCE;
            case EXCHANGED -> SALES;
            default -> Set.of();
        };
        return roles.stream().anyMatch(who::contains);
    }

    // ---------------------------------------------------------------- helpers

    private Map<UUID, Long> quantities(UUID orderId, Collection<ReturnStatus> statuses) {
        Map<UUID, Long> result = new HashMap<>();
        for (Object[] row : returnItems.quantitiesByOrderItem(orderId, statuses)) result.put((UUID) row[0], ((Number) row[1]).longValue());
        return result;
    }

    private String nextCode() {
        String day = DateTimeFormatter.ofPattern("yyyyMMdd").format(java.time.LocalDate.now());
        String code;
        do code = "TH-" + day + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        while (returns.existsByReturnCode(code));
        return code;
    }

    private Customer customer(UUID userId) {
        return customers.findByUser_UserId(userId).orElseThrow(() -> new CommerceException(403, "Tài khoản chưa có hồ sơ khách hàng"));
    }

    private void requireBranch(ReturnRequest row) {
        UUID warehouseId = orders.findById(row.getOrderId()).map(Order::getWarehouseId).orElse(null);
        if (!branch.allows(warehouseId)) throw notFound();
    }

    private CommerceException notFound() { return new CommerceException(404, "Không tìm thấy yêu cầu đổi trả"); }

    private static PageRequest pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new CommerceException(400, "page >= 0, size từ 1–100, offset trong giới hạn");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("requestDate"), Sort.Order.desc("returnId")));
    }

    private ReturnPageResponse page(Page<ReturnRequest> result, Set<String> roles) {
        return new ReturnPageResponse(map(result.getContent(), roles), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    private List<ReturnResponse> map(List<ReturnRequest> rows, Set<String> roles) {
        if (rows.isEmpty()) return List.of();
        Map<UUID, List<ReturnItem>> linesByReturn = returnItems.findByReturnIdIn(rows.stream().map(ReturnRequest::getReturnId).toList())
                .stream().collect(Collectors.groupingBy(ReturnItem::getReturnId));
        Map<UUID, Order> orderById = orders.findAllById(rows.stream().map(ReturnRequest::getOrderId).distinct().toList()).stream()
                .collect(Collectors.toMap(Order::getOrderId, Function.identity()));
        Map<UUID, Order> exchangeOrderById = orders.findAllById(rows.stream().map(ReturnRequest::getExchangeOrderId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Order::getOrderId, Function.identity()));
        Map<UUID, List<ReturnExchangeItem>> exchangeByReturn = exchangeItems.findByReturnIdIn(rows.stream().map(ReturnRequest::getReturnId).toList())
                .stream().collect(Collectors.groupingBy(ReturnExchangeItem::getReturnId));
        Map<UUID, ProductVariant> exchangeVariants = variants.findByVariantIdIn(exchangeByReturn.values().stream().flatMap(List::stream)
                .map(ReturnExchangeItem::getVariantId).distinct().toList()).stream().collect(Collectors.toMap(ProductVariant::getVariantId, Function.identity()));
        Map<UUID, OrderItem> orderLines = orderItems.findAllById(linesByReturn.values().stream().flatMap(List::stream)
                .map(ReturnItem::getOrderItemId).distinct().toList()).stream()
                .collect(Collectors.toMap(OrderItem::getOrderItemId, Function.identity()));
        Map<UUID, ProductVariant> variantById = variants.findByVariantIdIn(orderLines.values().stream().map(OrderItem::getVariantId).distinct().toList())
                .stream().collect(Collectors.toMap(ProductVariant::getVariantId, Function.identity()));
        List<UUID> serialIds = linesByReturn.values().stream().flatMap(List::stream).map(ReturnItem::getSerialId).filter(Objects::nonNull).toList();
        Map<UUID, String> serialNumbers = serialIds.isEmpty() ? Map.of() : serials.findAllById(serialIds).stream()
                .collect(Collectors.toMap(SerialNumber::getSerialId, SerialNumber::getSerialNumber));
        Map<UUID, String> customerNames = customers.findAllById(rows.stream().map(ReturnRequest::getCustomerId).distinct().toList())
                .stream().collect(Collectors.toMap(Customer::getCustomerId, Customer::getFullName));
        Map<UUID, String> employeeNames = employees.findAllById(rows.stream().map(ReturnRequest::getEmployeeId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Employee::getEmployeeId, Employee::getFullName));
        return rows.stream().map(row -> {
            List<ReturnResponse.Line> lines = linesByReturn.getOrDefault(row.getReturnId(), List.of()).stream().map(line -> {
                OrderItem item = orderLines.get(line.getOrderItemId());
                ProductVariant variant = item == null ? null : variantById.get(item.getVariantId());
                return new ReturnResponse.Line(line.getReturnItemId(), line.getOrderItemId(),
                        variant == null ? null : variant.getProduct().getProductName(), variant == null ? null : variant.getSku(),
                        line.getSerialId() == null ? null : serialNumbers.get(line.getSerialId()), line.getQuantity(),
                        item == null ? null : item.getFinalUnitPrice(), line.getReason(), line.getReceivedCondition());
            }).toList();
            Order order = orderById.get(row.getOrderId());
            List<ReturnStatus> next = roles.isEmpty() ? List.of()
                    : transitions(row.getStatus(), row.getRequestType()).stream().filter(status -> allowed(status, roles)).toList();
            List<ReturnResponse.ExchangeLine> exchange = exchangeByReturn.getOrDefault(row.getReturnId(), List.of()).stream().map(item -> {
                ProductVariant variant = exchangeVariants.get(item.getVariantId());
                return new ReturnResponse.ExchangeLine(item.getVariantId(), variant == null ? null : variant.getProduct().getProductName(),
                        variant == null ? null : variant.getSku(), item.getQuantity(), variant == null ? null : variant.getPrice());
            }).toList();
            Order exchangeOrder = row.getExchangeOrderId() == null ? null : exchangeOrderById.get(row.getExchangeOrderId());
            return new ReturnResponse(row.getReturnId(), row.getReturnCode(), row.getOrderId(), order == null ? null : order.getOrderCode(),
                    row.getCustomerId(), customerNames.get(row.getCustomerId()), row.getReason(), row.getRequestDate(), row.getProcessedAt(),
                    row.getRefundAmount(), row.getStatus(), row.getRejectionReason(), row.getEmployeeId() == null ? null : employeeNames.get(row.getEmployeeId()),
                    lines, next, row.getRequestType(), exchange, row.getExchangeOrderId(),
                    exchangeOrder == null ? null : exchangeOrder.getOrderCode(), row.getExchangeDifference());
        }).toList();
    }
}
