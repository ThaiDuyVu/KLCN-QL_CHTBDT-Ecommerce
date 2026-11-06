package com.example.backend.order.service;

import com.example.backend.auth.entity.Employee;
import com.example.backend.auth.repository.EmployeeRepository;
import com.example.backend.order.dto.DeliveryStaffResponse;
import com.example.backend.order.dto.ShipmentResponse;
import com.example.backend.order.dto.UpdateOrderStatusRequest;
import com.example.backend.order.entity.Order;
import com.example.backend.order.entity.Shipment;
import com.example.backend.order.entity.ShipmentStatus;
import com.example.backend.order.exception.CommerceException;
import com.example.backend.order.repository.ShipmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ShipmentServiceImpl implements ShipmentService {
    static final String IN_HOUSE = "Giao nội bộ";
    private static final String DELIVERY_ROLE = "DELIVERY_STAFF";
    private final ShipmentRepository shipments;
    private final EmployeeRepository employees;

    public ShipmentServiceImpl(ShipmentRepository shipments, EmployeeRepository employees) {
        this.shipments = shipments;
        this.employees = employees;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Shipment dispatch(Order order, UUID actorUserId, UpdateOrderStatusRequest request) {
        Employee carrier;
        if (request.getDeliveryEmployeeId() != null) {
            carrier = employees.findById(request.getDeliveryEmployeeId())
                    .orElseThrow(() -> new CommerceException(400, "Không tìm thấy nhân viên giao hàng đã chọn"));
            if (!employees.hasRole(carrier.getUser().getUserId(), DELIVERY_ROLE) || !"ACTIVE".equals(carrier.getUser().getStatus())) {
                throw new CommerceException(400, "Người được chọn không phải nhân viên giao hàng đang hoạt động");
            }
        } else {
            carrier = actorUserId == null ? null : employees.findByUser_UserId(actorUserId).orElse(null);
            if (carrier == null) throw new CommerceException(400, "Vui lòng chọn nhân viên giao hàng");
        }
        String tracking = request.getTrackingNumber();
        if (tracking == null) {
            do tracking = "GH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
            while (shipments.existsByTrackingNumberIgnoreCase(tracking));
        } else if (shipments.existsByTrackingNumberIgnoreCase(tracking)) {
            throw new CommerceException(409, "Mã vận đơn đã được dùng cho đơn khác: " + tracking);
        }
        Shipment shipment = new Shipment();
        shipment.setOrderId(order.getOrderId());
        shipment.setEmployeeId(carrier.getEmployeeId());
        shipment.setShippingProvider(request.getShippingProvider() == null ? IN_HOUSE : request.getShippingProvider());
        shipment.setTrackingNumber(tracking);
        shipment.setShippedDate(OffsetDateTime.now());
        shipment.setStatus(ShipmentStatus.SHIPPING);
        return shipments.saveAndFlush(shipment);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void delivered(Order order, UUID actorUserId, boolean ownOnly) {
        close(order, actorUserId, ownOnly, ShipmentStatus.DELIVERED);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void failed(Order order, UUID actorUserId, boolean ownOnly) {
        close(order, actorUserId, ownOnly, ShipmentStatus.FAILED);
    }

    private void close(Order order, UUID actorUserId, boolean ownOnly, ShipmentStatus outcome) {
        Shipment shipment = shipments.findFirstByOrderIdOrderByShippedDateDesc(order.getOrderId()).orElse(null);
        if (shipment == null) {
            // Orders shipped before shipments were recorded: only management may close them.
            if (ownOnly) throw new CommerceException(403, "Đơn này chưa được giao cho bạn");
            return;
        }
        if (ownOnly) {
            UUID mine = employees.findByUser_UserId(actorUserId).map(Employee::getEmployeeId).orElse(null);
            if (!shipment.getEmployeeId().equals(mine)) throw new CommerceException(403, "Đơn này được giao cho nhân viên khác");
        }
        if (shipment.getStatus() != ShipmentStatus.SHIPPING) throw new CommerceException(409, "Chuyến giao đã kết thúc");
        shipment.setStatus(outcome);
        if (outcome == ShipmentStatus.DELIVERED) shipment.setDeliveredDate(OffsetDateTime.now());
        shipments.saveAndFlush(shipment);
    }

    @Override
    public Optional<ShipmentResponse> forOrder(UUID orderId) {
        return shipments.findFirstByOrderIdOrderByShippedDateDesc(orderId).map(shipment -> map(List.of(shipment)).get(0));
    }

    @Override
    public Map<UUID, ShipmentResponse> forOrders(Collection<UUID> orderIds) {
        if (orderIds.isEmpty()) return Map.of();
        // Keep the latest shipment per order.
        Map<UUID, Shipment> latest = new HashMap<>();
        for (Shipment shipment : shipments.findByOrderIdIn(orderIds)) {
            latest.merge(shipment.getOrderId(), shipment,
                    (a, b) -> a.getShippedDate().isAfter(b.getShippedDate()) ? a : b);
        }
        List<Shipment> rows = new ArrayList<>(latest.values());
        List<ShipmentResponse> mapped = map(rows);
        Map<UUID, ShipmentResponse> byOrder = new HashMap<>();
        for (int index = 0; index < rows.size(); index++) byOrder.put(rows.get(index).getOrderId(), mapped.get(index));
        return byOrder;
    }

    @Override
    public Page<Shipment> assignedTo(UUID actorUserId, Pageable pageable) {
        Employee employee = employees.findByUser_UserId(actorUserId)
                .orElseThrow(() -> new CommerceException(403, "Tài khoản chưa có hồ sơ nhân viên"));
        return shipments.findByEmployeeId(employee.getEmployeeId(), pageable);
    }

    @Override
    public List<DeliveryStaffResponse> deliveryStaff() {
        return employees.findActiveByRoleName(DELIVERY_ROLE).stream().map(employee -> new DeliveryStaffResponse(
                employee.getEmployeeId(), employee.getEmployeeCode(), employee.getFullName(), employee.getUser().getPhone())).toList();
    }

    private List<ShipmentResponse> map(List<Shipment> rows) {
        Map<UUID, Employee> carriers = employees.findAllById(rows.stream().map(Shipment::getEmployeeId).distinct().toList())
                .stream().collect(Collectors.toMap(Employee::getEmployeeId, Function.identity()));
        return rows.stream().map(shipment -> {
            Employee carrier = carriers.get(shipment.getEmployeeId());
            return new ShipmentResponse(shipment.getShipmentId(), shipment.getStatus(), shipment.getEmployeeId(),
                    carrier == null ? null : carrier.getFullName(), carrier == null ? null : carrier.getEmployeeCode(),
                    carrier == null ? null : carrier.getUser().getPhone(), shipment.getShippingProvider(),
                    shipment.getTrackingNumber(), shipment.getShippedDate(), shipment.getDeliveredDate());
        }).toList();
    }
}
