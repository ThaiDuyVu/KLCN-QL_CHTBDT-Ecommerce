package com.example.backend.order.service;

import com.example.backend.auth.entity.Employee;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.EmployeeRepository;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.order.dto.OrderStatusChange;
import com.example.backend.order.entity.OrderStatus;
import com.example.backend.order.entity.OrderStatusHistory;
import com.example.backend.order.repository.OrderStatusHistoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Writes and reads order_status_history (V11): who moved an order, from which status, when and why. */
@Component
public class OrderHistory {
    private final OrderStatusHistoryRepository history;
    private final UserRepository users;
    private final EmployeeRepository employees;

    public OrderHistory(OrderStatusHistoryRepository history, UserRepository users, EmployeeRepository employees) {
        this.history = history;
        this.users = users;
        this.employees = employees;
    }

    /** actorUserId null = the system. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID orderId, OrderStatus from, OrderStatus to, UUID actorUserId, String note) {
        OrderStatusHistory row = new OrderStatusHistory();
        row.setOrderId(orderId);
        row.setFromStatus(from);
        row.setToStatus(to);
        row.setChangedByUserId(actorUserId);
        row.setNote(note);
        row.setChangedAt(OffsetDateTime.now());
        history.save(row);
    }

    public List<OrderStatusChange> of(UUID orderId) {
        List<OrderStatusHistory> rows = history.findByOrderIdOrderByChangedAtAscHistoryIdAsc(orderId);
        Set<UUID> actors = rows.stream().map(OrderStatusHistory::getChangedByUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, User> userById = actors.isEmpty() ? Map.of() : users.findAllById(actors).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
        Map<UUID, Employee> staffByUser = actors.isEmpty() ? Map.of() : employees.findByUser_UserIdIn(actors).stream()
                .collect(Collectors.toMap(e -> e.getUser().getUserId(), Function.identity()));
        return rows.stream().map(row -> {
            UUID actor = row.getChangedByUserId();
            Employee staff = actor == null ? null : staffByUser.get(actor);
            User user = actor == null ? null : userById.get(actor);
            String name = staff != null ? staff.getFullName()
                    : user == null ? null : (user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
            String type = actor == null ? "SYSTEM" : staff != null ? "STAFF" : "CUSTOMER";
            return new OrderStatusChange(row.getFromStatus(), row.getToStatus(), row.getChangedAt(), name, type, row.getNote());
        }).toList();
    }
}
