package com.example.backend.report.controller;

import com.example.backend.common.security.RequireAnyAuthority;
import com.example.backend.report.dto.*;
import com.example.backend.report.service.ReportService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService service;
    public ReportController(ReportService service) { this.service = service; }

    @GetMapping("/summary")
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public ReportSummaryResponse summary(@ModelAttribute ReportFilter filter) {
        return service.summary(filter);
    }

    @GetMapping("/revenue")
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public RevenueResponse revenue(@ModelAttribute ReportFilter filter) {
        return service.revenue(filter);
    }

    @GetMapping("/order-status")
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public List<OrderStatusRow> orderStatuses(@ModelAttribute ReportFilter filter) {
        return service.orderStatuses(filter);
    }

    @GetMapping("/top-products")
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public List<TopProductRow> topProducts(@ModelAttribute ReportFilter filter, @RequestParam(defaultValue = "5") int limit) {
        return service.topProducts(filter, limit);
    }

    @GetMapping("/warehouses")
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public List<WarehouseReportRow> warehouses(@ModelAttribute ReportFilter filter) {
        return service.warehouses(filter);
    }

    @GetMapping("/payments")
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public List<PaymentReportRow> payments(@ModelAttribute ReportFilter filter) {
        return service.payments(filter);
    }

    @GetMapping("/goods-receipts")
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public GoodsReceiptReportResponse goodsReceipts(@ModelAttribute ReportFilter filter) {
        return service.goodsReceipts(filter);
    }

    @GetMapping("/low-stock")
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public List<LowStockRow> lowStock(@ModelAttribute ReportFilter filter, @RequestParam(defaultValue = "5") int threshold, @RequestParam(defaultValue = "20") int limit) {
        return service.lowStock(filter, threshold, limit);
    }
}
