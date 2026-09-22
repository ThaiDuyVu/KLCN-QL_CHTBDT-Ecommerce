package com.example.backend.warranty.service;

import com.example.backend.auth.entity.*;
import com.example.backend.auth.repository.EmployeeRepository;
import com.example.backend.cart.repository.CustomerCartRepository;
import com.example.backend.inventory.serial.dto.SerialResponse;
import com.example.backend.inventory.serial.entity.*;
import com.example.backend.inventory.serial.repository.SerialNumberRepository;
import com.example.backend.inventory.serial.service.SerialQueryService;
import com.example.backend.order.entity.*;
import com.example.backend.order.repository.*;
import com.example.backend.warranty.dto.*;
import com.example.backend.warranty.entity.*;
import com.example.backend.warranty.exception.WarrantyException;
import com.example.backend.warranty.repository.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class WarrantyServiceImpl implements WarrantyService {
    private static final int MAX_PAGE_SIZE = 100;
    private final WarrantyRepository warranties;
    private final WarrantyTicketRepository tickets;
    private final CustomerCartRepository customers;
    private final EmployeeRepository employees;
    private final OrderItemRepository orderItems;
    private final OrderRepository orders;
    private final SerialNumberRepository serials;
    private final SerialQueryService serialQuery;

    public WarrantyServiceImpl(WarrantyRepository warranties, WarrantyTicketRepository tickets,
            CustomerCartRepository customers, EmployeeRepository employees,
            OrderItemRepository orderItems, OrderRepository orders,
            SerialNumberRepository serials, SerialQueryService serialQuery) {
        this.warranties = warranties;
        this.tickets = tickets;
        this.customers = customers;
        this.employees = employees;
        this.orderItems = orderItems;
        this.orders = orders;
        this.serials = serials;
        this.serialQuery = serialQuery;
    }

    @Override
    public WarrantyPageResponse myWarranties(UUID userId, int page, int size) {
        return warrantyPage(warranties.findByCustomerId(customerId(userId), warrantyPageable(page, size)));
    }

    @Override
    public WarrantyResponse myWarranty(UUID userId, UUID warrantyId) {
        UUID customerId = customerId(userId);
        Warranty warranty = warranties.findByWarrantyIdAndCustomerId(warrantyId, customerId)
                .orElseThrow(() -> notFound());
        return mapWarrantyDetail(warranty, false);
    }

    @Override
    public WarrantyResponse myLookup(UUID userId, String code) {
        UUID customerId = customerId(userId);
        Warranty warranty = findByCode(code);
        if (!warranty.getCustomerId().equals(customerId)) throw notFound();
        return mapWarrantyDetail(warranty, false);
    }

    @Override
    public WarrantyPageResponse warranties(int page, int size) {
        return warrantyPage(warranties.findAll(warrantyPageable(page, size)));
    }

    @Override
    public WarrantyResponse warranty(UUID warrantyId) {
        return mapWarrantyDetail(findWarranty(warrantyId), true);
    }

    @Override
    public WarrantyResponse lookup(String code) {
        return mapWarrantyDetail(findByCode(code), true);
    }

    @Override
    @Transactional
    public WarrantyTicketResponse createTicket(UUID userId, UUID warrantyId, CreateWarrantyTicketRequest request) {
        UUID customerId = customerId(userId);
        Warranty warranty = warranties.findByWarrantyIdAndCustomerId(warrantyId, customerId)
                .orElseThrow(() -> notFound());
        ensureEligible(warranty);
        WarrantyTicket ticket = new WarrantyTicket();
        ticket.setTicketCode("WT-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
        ticket.setWarrantyId(warranty.getWarrantyId());
        ticket.setSerialId(warranty.getSerialId());
        ticket.setCustomerId(warranty.getCustomerId());
        ticket.setIssueDescription(request.issueDescription().trim());
        ticket.setCreatedAt(OffsetDateTime.now());
        ticket.setStatus(WarrantyTicketStatus.RECEIVED);
        tickets.saveAndFlush(ticket);
        return mapTickets(List.of(ticket), false).get(0);
    }

    @Override
    public WarrantyTicketPageResponse myTickets(UUID userId, int page, int size) {
        Page<WarrantyTicket> result = tickets.findByCustomerId(customerId(userId), pageable(page, size));
        return ticketPage(result, false);
    }

    @Override
    public WarrantyTicketResponse myTicket(UUID userId, UUID ticketId) {
        WarrantyTicket ticket = tickets.findByTicketIdAndCustomerId(ticketId, customerId(userId))
                .orElseThrow(() -> ticketNotFound());
        return mapTickets(List.of(ticket), false).get(0);
    }

    @Override
    public WarrantyTicketPageResponse tickets(String keyword, WarrantyTicketStatus status, int page, int size) {
        Page<WarrantyTicket> result = tickets.findAll(ticketSpec(keyword, status), pageable(page, size));
        return ticketPage(result, true);
    }

    @Override
    public WarrantyTicketResponse ticket(UUID ticketId) {
        WarrantyTicket ticket = tickets.findById(ticketId).orElseThrow(() -> ticketNotFound());
        return mapTickets(List.of(ticket), true).get(0);
    }

    @Override
    @Transactional
    public WarrantyTicketResponse updateTicket(UUID userId, UUID ticketId, UpdateWarrantyTicketRequest request) {
        WarrantyTicket ticket = tickets.lockById(ticketId).orElseThrow(() -> ticketNotFound());
        requireTransition(ticket.getStatus(), request.status());
        String note = request.resolutionNote() == null ? null : request.resolutionNote().trim();
        if ((request.status() == WarrantyTicketStatus.COMPLETED
                || request.status() == WarrantyTicketStatus.REJECTED)
                && (note == null || note.isEmpty())) {
            throw new WarrantyException(400, "Ghi chú xử lý là bắt buộc khi hoàn tất hoặc từ chối ticket");
        }
        Employee employee = employees.findByUser_UserId(userId)
                .orElseThrow(() -> new WarrantyException(403, "Tài khoản chưa có hồ sơ nhân viên"));
        if (ticket.getEmployeeId() == null) ticket.setEmployeeId(employee.getEmployeeId());
        ticket.setStatus(request.status());
        if (note != null && !note.isEmpty()) ticket.setResolutionNote(note);
        if (request.status() == WarrantyTicketStatus.COMPLETED
                || request.status() == WarrantyTicketStatus.REJECTED) {
            ticket.setResolvedAt(OffsetDateTime.now());
        }
        tickets.saveAndFlush(ticket);
        return mapTickets(List.of(ticket), true).get(0);
    }

    private Warranty findByCode(String code) {
        SerialResponse serial = serialQuery.lookup(code);
        return warranties.findBySerialId(serial.serialId()).orElseThrow(() ->
                new WarrantyException(404, "Thiết bị này chưa có quyền bảo hành"));
    }

    private Warranty findWarranty(UUID warrantyId) {
        return warranties.findById(warrantyId).orElseThrow(() -> notFound());
    }

    private void ensureEligible(Warranty warranty) {
        LocalDate today = LocalDate.now();
        if (warranty.getStatus() != WarrantyStatus.ACTIVE
                || today.isBefore(warranty.getStartDate()) || today.isAfter(warranty.getEndDate())) {
            throw new WarrantyException(409, "Bảo hành chưa có hiệu lực hoặc đã hết hạn");
        }
        SerialResponse serial = serialQuery.getById(warranty.getSerialId());
        if (!serial.serialId().equals(warranty.getSerialId()) || serial.status() != SerialStatus.SOLD) {
            throw new WarrantyException(409, "Thiết bị không còn ở trạng thái đã bán hợp lệ để tiếp nhận bảo hành");
        }
    }

    private WarrantyPageResponse warrantyPage(Page<Warranty> result) {
        List<WarrantyResponse> content = mapWarranties(result.getContent(), false, false);
        return new WarrantyPageResponse(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    private WarrantyTicketPageResponse ticketPage(Page<WarrantyTicket> result, boolean management) {
        return new WarrantyTicketPageResponse(mapTickets(result.getContent(), management), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private WarrantyResponse mapWarrantyDetail(Warranty warranty, boolean management) {
        return mapWarranties(List.of(warranty), true, management).get(0);
    }

    private List<WarrantyResponse> mapWarranties(List<Warranty> rows, boolean includeTickets, boolean management) {
        if (rows.isEmpty()) return List.of();
        DetailContext context = context(rows);
        Map<UUID, List<WarrantyTicket>> history = includeTickets
                ? rows.stream().collect(Collectors.toMap(Warranty::getWarrantyId,
                    row -> tickets.findByWarrantyIdOrderByCreatedAtDescTicketIdDesc(row.getWarrantyId())))
                : Map.of();
        Map<UUID, Employee> employeeMap = employeesFor(history.values().stream().flatMap(Collection::stream).toList());
        return rows.stream().map(row -> mapWarranty(row, context,
                mapTicketRows(history.getOrDefault(row.getWarrantyId(), List.of()), row, context, employeeMap, management)))
                .toList();
    }

    private List<WarrantyTicketResponse> mapTickets(List<WarrantyTicket> rows, boolean management) {
        if (rows.isEmpty()) return List.of();
        Map<UUID, Warranty> warrantyMap = warranties.findByWarrantyIdIn(
                rows.stream().map(WarrantyTicket::getWarrantyId).distinct().toList()
        ).stream().collect(Collectors.toMap(Warranty::getWarrantyId, Function.identity()));
        if (warrantyMap.size() != rows.stream().map(WarrantyTicket::getWarrantyId).distinct().count()) {
            throw new WarrantyException(409, "Ticket tham chiếu Warranty không hợp lệ");
        }
        DetailContext context = context(new ArrayList<>(warrantyMap.values()));
        Map<UUID, Employee> employeeMap = employeesFor(rows);
        return rows.stream().map(row -> mapTicket(row, warrantyMap.get(row.getWarrantyId()), context,
                employeeMap, management)).toList();
    }

    private List<WarrantyTicketResponse> mapTicketRows(List<WarrantyTicket> rows, Warranty warranty,
            DetailContext context, Map<UUID, Employee> employeeMap, boolean management) {
        return rows.stream().map(row -> mapTicket(row, warranty, context, employeeMap, management)).toList();
    }

    private WarrantyResponse mapWarranty(Warranty warranty, DetailContext context,
            List<WarrantyTicketResponse> history) {
        SerialNumber serial = required(context.serials().get(warranty.getSerialId()), "Serial của Warranty không tồn tại");
        OrderItem item = required(context.items().get(warranty.getOrderItemId()), "OrderItem của Warranty không tồn tại");
        Order order = required(context.orders().get(item.getOrderId()), "Order của Warranty không tồn tại");
        Customer customer = required(context.customers().get(warranty.getCustomerId()), "Customer của Warranty không tồn tại");
        WarrantyStatus effective = effectiveStatus(warranty);
        boolean eligible = effective == WarrantyStatus.ACTIVE && serial.getStatus() == SerialStatus.SOLD
                && !LocalDate.now().isBefore(warranty.getStartDate());
        return new WarrantyResponse(warranty.getWarrantyId(), warranty.getSerialId(), warranty.getOrderItemId(),
                warranty.getCustomerId(), customer.getFullName(), order.getOrderId(), order.getOrderCode(),
                serial.getVariant().getVariantId(), serial.getVariant().getProduct().getProductName(),
                serial.getVariant().getSku(), serial.getSerialNumber(),
                serial.getImeis().stream().map(Imei::getImeiNumber).sorted().toList(),
                warranty.getStartDate(), warranty.getEndDate(), effective, eligible, history);
    }

    private WarrantyTicketResponse mapTicket(WarrantyTicket ticket, Warranty warranty, DetailContext context,
            Map<UUID, Employee> employeeMap, boolean management) {
        if (!ticket.getSerialId().equals(warranty.getSerialId())
                || !ticket.getCustomerId().equals(warranty.getCustomerId())) {
            throw new WarrantyException(409, "Warranty Ticket không khớp Serial/Customer của Warranty");
        }
        WarrantyResponse details = mapWarranty(warranty, context, List.of());
        Employee employee = ticket.getEmployeeId() == null ? null : employeeMap.get(ticket.getEmployeeId());
        return new WarrantyTicketResponse(ticket.getTicketId(), ticket.getTicketCode(), ticket.getWarrantyId(),
                ticket.getSerialId(), ticket.getCustomerId(), ticket.getEmployeeId(),
                employee == null ? null : employee.getFullName(), ticket.getIssueDescription(),
                ticket.getResolutionNote(), ticket.getCreatedAt(), ticket.getResolvedAt(), ticket.getStatus(),
                details.serialNumber(), details.imeiNumbers(), details.productName(), details.sku(),
                details.customerName(), details.orderCode(), management ? allowed(ticket.getStatus()) : List.of());
    }

    private DetailContext context(List<Warranty> rows) {
        List<UUID> serialIds = rows.stream().map(Warranty::getSerialId).distinct().toList();
        Map<UUID, SerialNumber> serialMap = serials.findAllWithDetailsByIdIn(serialIds).stream()
                .collect(Collectors.toMap(SerialNumber::getSerialId, Function.identity()));
        Map<UUID, OrderItem> itemMap = orderItems.findAllById(
                rows.stream().map(Warranty::getOrderItemId).distinct().toList()
        ).stream().collect(Collectors.toMap(OrderItem::getOrderItemId, Function.identity()));
        Map<UUID, Order> orderMap = orders.findAllById(itemMap.values().stream()
                .map(OrderItem::getOrderId).distinct().toList()).stream()
                .collect(Collectors.toMap(Order::getOrderId, Function.identity()));
        Map<UUID, Customer> customerMap = customers.findAllById(
                rows.stream().map(Warranty::getCustomerId).distinct().toList()
        ).stream().collect(Collectors.toMap(Customer::getCustomerId, Function.identity()));
        return new DetailContext(serialMap, itemMap, orderMap, customerMap);
    }

    private Map<UUID, Employee> employeesFor(List<WarrantyTicket> rows) {
        List<UUID> ids = rows.stream().map(WarrantyTicket::getEmployeeId).filter(Objects::nonNull).distinct().toList();
        return employees.findAllById(ids).stream().collect(Collectors.toMap(Employee::getEmployeeId, Function.identity()));
    }

    private UUID customerId(UUID userId) {
        return customers.findByUser_UserId(userId).orElseThrow(() ->
                new WarrantyException(403, "Tài khoản chưa có hồ sơ customer")).getCustomerId();
    }

    private PageRequest pageable(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || (long) page * size > Integer.MAX_VALUE) {
            throw new WarrantyException(400, "Phân trang không hợp lệ; size phải từ 1 đến " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("ticketId")));
    }

    private PageRequest warrantyPageable(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || (long) page * size > Integer.MAX_VALUE) {
            throw new WarrantyException(400, "Phân trang không hợp lệ; size phải từ 1 đến " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("startDate"), Sort.Order.desc("warrantyId")));
    }

    private Specification<WarrantyTicket> ticketSpec(String keyword, WarrantyTicketStatus status) {
        return (root, query, cb) -> {
            List<Predicate> values = new ArrayList<>();
            if (status != null) values.add(cb.equal(root.get("status"), status));
            if (keyword != null && !keyword.isBlank()) {
                String term = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                values.add(cb.or(cb.like(cb.lower(root.get("ticketCode")), term),
                        cb.like(cb.lower(root.get("issueDescription")), term)));
            }
            return cb.and(values.toArray(Predicate[]::new));
        };
    }

    private WarrantyStatus effectiveStatus(Warranty warranty) {
        return warranty.getStatus() == WarrantyStatus.EXPIRED || LocalDate.now().isAfter(warranty.getEndDate())
                ? WarrantyStatus.EXPIRED : WarrantyStatus.ACTIVE;
    }

    private void requireTransition(WarrantyTicketStatus from, WarrantyTicketStatus to) {
        boolean valid = (from == WarrantyTicketStatus.RECEIVED
                && (to == WarrantyTicketStatus.IN_PROGRESS || to == WarrantyTicketStatus.REJECTED))
                || (from == WarrantyTicketStatus.IN_PROGRESS && to == WarrantyTicketStatus.COMPLETED);
        if (!valid) throw new WarrantyException(409, "Không thể chuyển ticket từ " + from + " sang " + to);
    }

    private List<WarrantyTicketStatus> allowed(WarrantyTicketStatus status) {
        return switch (status) {
            case RECEIVED -> List.of(WarrantyTicketStatus.IN_PROGRESS, WarrantyTicketStatus.REJECTED);
            case IN_PROGRESS -> List.of(WarrantyTicketStatus.COMPLETED);
            case COMPLETED, REJECTED -> List.of();
        };
    }

    private <T> T required(T value, String message) {
        if (value == null) throw new WarrantyException(409, message);
        return value;
    }

    private WarrantyException notFound() { return new WarrantyException(404, "Không tìm thấy Warranty"); }
    private WarrantyException ticketNotFound() { return new WarrantyException(404, "Không tìm thấy Warranty Ticket"); }
    private record DetailContext(Map<UUID, SerialNumber> serials, Map<UUID, OrderItem> items,
            Map<UUID, Order> orders, Map<UUID, Customer> customers) {}
}
