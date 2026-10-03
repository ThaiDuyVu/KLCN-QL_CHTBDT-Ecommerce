package com.example.backend.report.service;

import com.example.backend.report.dto.*;
import com.example.backend.report.exception.ReportException;
import com.example.backend.report.repository.ReportRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
@Transactional(readOnly = true, timeout = 10, isolation = Isolation.REPEATABLE_READ)
public class ReportServiceImpl implements ReportService {
    private final ReportRepository repository;

    public ReportServiceImpl(ReportRepository repository) { this.repository = repository; }

    private ReportFilter validate(ReportFilter filter) {
        LocalDate to = filter.getTo() == null ? LocalDate.now() : filter.getTo();
        if (to.getYear() < 1 || to.getYear() > 9998) {
            throw new ReportException(400, "Ngày báo cáo ngoài khoảng hỗ trợ");
        }
        LocalDate from = filter.getFrom() == null ? to.minusDays(29) : filter.getFrom();
        if (from.getYear() < 1 || from.isAfter(to)) {
            throw new ReportException(400, "Từ ngày phải nhỏ hơn hoặc bằng đến ngày");
        }
        if (ChronoUnit.DAYS.between(from, to) >= 366) {
            throw new ReportException(400, "Khoảng báo cáo tối đa 366 ngày");
        }
        filter.setFrom(from);
        filter.setTo(to);
        if (filter.getWarehouseId() != null && !repository.warehouseExists(filter.getWarehouseId())) {
            throw new ReportException(404, "Chi nhánh không tồn tại");
        }
        return filter;
    }

    private void limit(int value, int max) {
        if (value < 1 || value > max) throw new ReportException(400, "Limit phải từ 1 đến " + max);
    }

    @Override public ReportSummaryResponse summary(ReportFilter filter) {
        return repository.summary(validate(filter));
    }

    @Override public RevenueResponse revenue(ReportFilter filter) {
        validate(filter);
        boolean monthly = ChronoUnit.DAYS.between(filter.getFrom(), filter.getTo()) >= 90;
        List<RevenuePoint> rows = repository.revenue(filter, monthly);
        // Only fill missing buckets (max 90 days / 13 months); never load individual orders.
        Map<LocalDate, RevenuePoint> byDate = new HashMap<>();
        for (RevenuePoint row : rows) byDate.put(row.getBucket(), row);
        List<RevenuePoint> points = new ArrayList<>();
        LocalDate start = monthly ? filter.getFrom().withDayOfMonth(1) : filter.getFrom();
        LocalDate end = monthly ? filter.getTo().withDayOfMonth(1) : filter.getTo();
        for (LocalDate date = start; !date.isAfter(end); date = monthly ? date.plusMonths(1) : date.plusDays(1)) {
            points.add(byDate.getOrDefault(date, new RevenuePoint(date, BigDecimal.ZERO, 0)));
        }
        return new RevenueResponse(monthly ? "MONTH" : "DAY", points);
    }

    @Override public List<OrderStatusRow> orderStatuses(ReportFilter filter) {
        return repository.orderStatuses(validate(filter));
    }
    @Override public List<TopProductRow> topProducts(ReportFilter filter, int limit) {
        limit(limit, 20);
        return repository.topProducts(validate(filter), limit);
    }
    @Override public List<WarehouseReportRow> warehouses(ReportFilter filter) {
        return repository.warehouses(validate(filter));
    }
    @Override public List<PaymentReportRow> payments(ReportFilter filter) {
        return repository.payments(validate(filter));
    }
    @Override public GoodsReceiptReportResponse goodsReceipts(ReportFilter filter) {
        return repository.goodsReceipts(validate(filter));
    }
    @Override public List<LowStockRow> lowStock(ReportFilter filter, int threshold, int limit) {
        limit(limit, 100);
        if (threshold < 0 || threshold > 1000) throw new ReportException(400, "Ngưỡng tồn kho phải từ 0 đến 1000");
        return repository.lowStock(validate(filter), threshold, limit);
    }
}
