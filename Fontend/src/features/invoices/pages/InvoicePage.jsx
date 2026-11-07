import { useCallback } from 'react';
import { Link, useParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import useProductRequest from '../../products/hooks/useProductRequest';
import CommerceState from '../../orders/components/CommerceState';
import { invoiceApi } from '../api/invoiceApi';
import InvoiceDocument from '../components/InvoiceDocument';
import '../../orders/commerce.css';
import '../invoices.css';

/** Staff: /invoices/:invoiceId. Customer: /my-orders/:orderId/invoice. Printing uses the browser (PDF via "Save as PDF"). */
export default function InvoicePage({ customer = false }) {
  const { invoiceId, orderId } = useParams();
  const load = useCallback((signal) => (customer ? invoiceApi.mineForOrder(orderId, signal) : invoiceApi.detail(invoiceId, signal)), [customer, invoiceId, orderId]);
  const { data, error, isLoading, retry } = useProductRequest(`invoice:${customer}:${invoiceId || orderId}`, load);
  return <><PageHeader title="Hóa đơn bán hàng" description="In hoặc lưu thành PDF bằng chức năng in của trình duyệt." />
    <div className="invoice-toolbar"><Link className="button button-quiet" to={customer ? `/my-orders/${orderId}` : '/invoices'}>← Quay lại</Link>
      {data && <button className="button" onClick={() => window.print()}>In hóa đơn</button>}</div>
    <CommerceState loading={isLoading} error={error} retry={retry} />
    {data && <InvoiceDocument invoice={data} />}
  </>;
}
