package com.example.backend.report.service;

import com.example.backend.report.dto.*;
import java.util.List;

public interface ReportService {
    ReportSummaryResponse summary(ReportFilter filter);
    RevenueResponse revenue(ReportFilter filter);
    List<OrderStatusRow> orderStatuses(ReportFilter filter);
    List<TopProductRow> topProducts(ReportFilter filter, int limit);
    List<WarehouseReportRow> warehouses(ReportFilter filter);
    List<PaymentReportRow> payments(ReportFilter filter);
    GoodsReceiptReportResponse goodsReceipts(ReportFilter filter);
    List<LowStockRow> lowStock(ReportFilter filter, int threshold, int limit);
}
