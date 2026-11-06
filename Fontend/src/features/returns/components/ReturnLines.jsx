import { money } from '../../orders/components/orderFormat';

export default function ReturnLines({ items }) {
  return <ul className="return-lines">{items.map((line) => <li key={line.returnItemId}>
    <strong>{line.productName}</strong> <span className="muted">{line.sku}{line.serialNumber ? ` · Serial ${line.serialNumber}` : ''}</span>
    <span> · SL {line.quantity} × {money(line.unitPrice)}</span>
    {line.reason && <p className="muted">{line.reason}</p>}
  </li>)}</ul>;
}
