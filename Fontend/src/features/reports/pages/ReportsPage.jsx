import { useCallback, useState } from 'react';
import PageHeader from '../../../components/ui/PageHeader';
import useProductRequest from '../../products/hooks/useProductRequest';
import { inventoryApi } from '../../inventory/api/inventoryApi';
import ReportWidget from '../components/ReportWidget';
import { RevenueChart, TopProductsChart, WarehouseRevenueChart } from '../components/ReportCharts';
import { SummaryCards, OrderStatusSummary, PaymentSummary, GoodsReceiptSummary, LowStockTable } from '../components/ReportSummaries';
import '../reports.css';

function localDate(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
function initialFilter() {
  const to = new Date();
  const from = new Date(to);
  from.setDate(from.getDate() - 29);
  return { from: localDate(from), to: localDate(to), warehouseId: '' };
}

export default function ReportsPage() {
  const [filters, setFilters] = useState(initialFilter);
  const [threshold, setThreshold] = useState(5);
  const [error, setError] = useState('');
  const loadWarehouses = useCallback((signal) => inventoryApi.warehouses(signal), []);
  const warehouses = useProductRequest('report-warehouse-options', loadWarehouses);
  function apply(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const from = String(form.get('from'));
    const to = String(form.get('to'));
    const days = (Date.parse(to) - Date.parse(from)) / 86400000;
    if (!Number.isFinite(days) || days < 0 || days >= 366) {
      setError('Chọn từ ngày ≤ đến ngày, trong khoảng tối đa 366 ngày.');
      return;
    }
    setError('');
    setThreshold(Number(form.get('threshold')));
    setFilters({ from, to, warehouseId: String(form.get('warehouseId') || '') });
  }
  return <div className="reports-page">
    <PageHeader title="Thống kê & Báo cáo" description="Theo dõi kết quả bán hàng, giá vốn và tồn kho từ dữ liệu nghiệp vụ đã ghi nhận." />
    <form className="panel report-filters" onSubmit={apply} aria-label="Bộ lọc báo cáo">
      <label>Từ ngày<input type="date" name="from" defaultValue={filters.from} required /></label>
      <label>Đến ngày<input type="date" name="to" defaultValue={filters.to} required /></label>
      <label>Chi nhánh<select name="warehouseId" defaultValue="" disabled={warehouses.isLoading || !!warehouses.error}><option value="">Tất cả chi nhánh</option>{(warehouses.data?.content || []).map((row) => <option value={row.warehouseId} key={row.warehouseId}>{row.warehouseName}</option>)}</select></label>
      <label>Ngưỡng tồn khả dụng<input type="number" name="threshold" min="0" max="1000" defaultValue="5" required /></label>
      <button className="button" type="submit">Áp dụng</button>
    </form>
    {error && <p className="auth-alert" role="alert">{error}</p>}
    {warehouses.error && <p className="auth-alert" role="alert">Chưa tải được chi nhánh: {warehouses.error.message} <button className="button button-quiet" onClick={warehouses.retry}>Thử lại</button></p>}
    <div className="report-scope"><strong>{filters.from} → {filters.to}</strong><span>Doanh thu theo ngày đặt đơn · Chỉ Order DELIVERED</span></div>
    <ReportWidget resource="summary" filters={filters} title="Kết quả kinh doanh" description="Lợi nhuận gộp chưa trừ chi phí vận hành; payment PAID không đồng nghĩa với doanh thu.">{(data) => <SummaryCards data={data} />}</ReportWidget>
    <ReportWidget resource="revenue" filters={filters} title="Doanh thu theo thời gian" description="Theo ngày khi ≤ 90 ngày, theo tháng khi dài hơn. Không dùng ngày giao hàng.">{(data) => <RevenueChart data={data} />}</ReportWidget>
    <div className="report-two-columns">
      <ReportWidget resource="top-products" filters={{ ...filters, limit: 5 }} title="Top 5 sản phẩm bán chạy" description="Xếp theo số lượng bán trong đơn DELIVERED.">{(data) => <TopProductsChart data={data} />}</ReportWidget>
      <ReportWidget resource="warehouses" filters={filters} title="Doanh thu theo chi nhánh" description="Order DELIVERED · tối đa 100 chi nhánh theo doanh thu.">{(data) => <WarehouseRevenueChart data={data} />}</ReportWidget>
    </div>
    <div className="report-two-columns">
      <ReportWidget resource="order-status" filters={filters} title="Trạng thái đơn hàng" description="Tất cả đơn có ngày đặt trong phạm vi lọc.">{(data) => <OrderStatusSummary data={data} />}</ReportWidget>
      <ReportWidget resource="payments" filters={filters} title="Phương thức thanh toán" description="Payment của đơn trong phạm vi ngày đặt; đây không phải doanh thu đã ghi nhận.">{(data) => <PaymentSummary data={data} />}</ReportWidget>
    </div>
    <ReportWidget resource="goods-receipts" filters={filters} title="Giá trị nhập hàng" description="Chỉ phiếu CONFIRMED · lọc theo receiptDate · nhóm tối đa 100 chi nhánh.">{(data) => <GoodsReceiptSummary data={data} />}</ReportWidget>
    <ReportWidget resource="low-stock" filters={{ ...filters, threshold, limit: 20 }} title="Tồn kho thấp" description={`Tồn khả dụng hiện tại ≤ ${threshold} · tối đa 20 SKU/chi nhánh · không phải snapshot lịch sử theo khoảng ngày.`}>{(data) => <LowStockTable data={data} />}</ReportWidget>
  </div>;
}
