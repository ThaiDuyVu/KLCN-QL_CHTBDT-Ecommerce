import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { money } from '../../orders/components/orderFormat';

const compactMoney = (value) => new Intl.NumberFormat('vi-VN', { notation: 'compact', maximumFractionDigits: 1 }).format(value);
const dateLabel = (value, monthly = false) => monthly ? `${value.slice(5, 7)}/${value.slice(0, 4)}` : `${value.slice(8, 10)}/${value.slice(5, 7)}`;
const shorten = (value) => { const label = String(value ?? ''); return label.length > 18 ? `${label.slice(0, 17)}…` : label; };
const tooltipStyle = { border: '1px solid #dce5de', borderRadius: 8, padding: '10px 14px', fontSize: 12, maxWidth: 260, whiteSpace: 'normal', boxShadow: '0 4px 16px #18332b12' };
const tooltipLabelStyle = { fontWeight: 600, overflowWrap: 'anywhere', marginBottom: 6 };
const chartMargin = { top: 12, right: 16, left: 8, bottom: 8 };

function EmptyChart() { return <p className="report-empty">Chưa có đơn DELIVERED trong phạm vi này.</p>; }

export function RevenueChart({ data }) {
  if (!data.points.some((point) => point.completedOrders > 0)) return <EmptyChart />;
  const monthly = data.granularity === 'MONTH';
  return <><div className="report-chart" role="group" aria-label="Doanh thu theo thời gian, đơn vị đồng">
    <ResponsiveContainer width="100%" height="100%" minWidth={0} initialDimension={{ width: 320, height: 290 }} debounce={80}>
      <LineChart data={data.points} margin={chartMargin} accessibilityLayer>
        <CartesianGrid stroke="#e4ebe8" strokeDasharray="3 3" vertical={false} />
        <XAxis dataKey="bucket" tickFormatter={(value) => dateLabel(value, monthly)} minTickGap={28} tick={{ fontSize: 11 }} />
        <YAxis tickFormatter={compactMoney} width={65} tick={{ fontSize: 11 }} />
        <Tooltip contentStyle={tooltipStyle} labelStyle={tooltipLabelStyle} formatter={(value) => [money(value), 'Doanh thu']} labelFormatter={(value) => monthly ? dateLabel(value, true) : value} />
        <Line type="linear" dataKey="revenue" stroke="#087f5b" strokeWidth={2.5} dot={false} activeDot={{ r: 4 }} isAnimationActive={false} />
      </LineChart>
    </ResponsiveContainer>
  </div><details className="report-chart-data"><summary>Xem số liệu theo {monthly ? 'tháng' : 'ngày'}</summary><div className="report-table-scroll"><table className="product-table"><thead><tr><th>Kỳ</th><th className="report-number">Đơn hoàn tất</th><th className="report-number">Doanh thu</th></tr></thead><tbody>{data.points.map((point) => <tr key={point.bucket}><th scope="row">{point.bucket}</th><td className="report-number">{point.completedOrders}</td><td className="report-number">{money(point.revenue)}</td></tr>)}</tbody></table></div></details></>;
}

export function TopProductsChart({ data }) {
  if (!data.length) return <EmptyChart />;
  return <><div className="report-chart" role="group" aria-label="Top sản phẩm theo số lượng bán">
    <ResponsiveContainer width="100%" height="100%" minWidth={0} initialDimension={{ width: 320, height: 290 }} debounce={80}>
      <BarChart data={data} layout="vertical" margin={chartMargin} accessibilityLayer>
        <CartesianGrid stroke="#e4ebe8" strokeDasharray="3 3" horizontal={false} />
        <XAxis type="number" allowDecimals={false} tick={{ fontSize: 11 }} />
        <YAxis type="category" dataKey="productName" tickFormatter={shorten} interval={0} width={125} tick={{ fontSize: 11 }} />
        <Tooltip contentStyle={tooltipStyle} labelStyle={tooltipLabelStyle} formatter={(value) => [value, 'Số lượng bán']} />
        <Bar dataKey="quantity" fill="#087f5b" radius={[0, 4, 4, 0]} maxBarSize={28} isAnimationActive={false} />
      </BarChart>
    </ResponsiveContainer>
  </div><details className="report-chart-data"><summary>Xem số lượng và doanh thu sản phẩm</summary><div className="report-table-scroll"><table className="product-table"><thead><tr><th>Sản phẩm</th><th className="report-number">Đã bán</th><th className="report-number">Doanh thu hàng hóa</th></tr></thead><tbody>{data.map((row) => <tr key={row.productId}><th scope="row">{row.productName}</th><td className="report-number">{row.quantity}</td><td className="report-number">{money(row.revenue)}</td></tr>)}</tbody></table></div></details></>;
}

export function WarehouseRevenueChart({ data }) {
  if (!data.length) return <EmptyChart />;
  return <><div className="report-chart" role="group" aria-label="Doanh thu theo chi nhánh, đơn vị đồng">
    <ResponsiveContainer width="100%" height="100%" minWidth={0} initialDimension={{ width: 320, height: 290 }} debounce={80}>
      <BarChart data={data} margin={chartMargin} accessibilityLayer>
        <CartesianGrid stroke="#e4ebe8" strokeDasharray="3 3" vertical={false} />
        <XAxis dataKey="warehouseName" tickFormatter={shorten} tick={{ fontSize: 11 }} minTickGap={15} height={50} />
        <YAxis tickFormatter={compactMoney} width={65} tick={{ fontSize: 11 }} />
        <Tooltip contentStyle={tooltipStyle} labelStyle={tooltipLabelStyle} formatter={(value) => [money(value), 'Doanh thu']} />
        <Bar dataKey="amount" fill="#3c7ca0" radius={[4, 4, 0, 0]} maxBarSize={38} isAnimationActive={false} />
      </BarChart>
    </ResponsiveContainer>
  </div><details className="report-chart-data"><summary>Xem doanh thu và số đơn từng chi nhánh</summary><div className="report-table-scroll"><table className="product-table"><thead><tr><th>Chi nhánh</th><th className="report-number">Đơn hoàn tất</th><th className="report-number">Doanh thu</th></tr></thead><tbody>{data.map((row) => <tr key={row.warehouseId}><th scope="row">{row.warehouseName}</th><td className="report-number">{row.count}</td><td className="report-number">{money(row.amount)}</td></tr>)}</tbody></table></div></details></>;
}
