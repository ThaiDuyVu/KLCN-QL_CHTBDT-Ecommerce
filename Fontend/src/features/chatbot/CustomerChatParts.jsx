import { Link } from 'react-router';
import PriceDisplay from '../../components/ui/PriceDisplay';
import { money } from '../orders/components/orderFormat';
import ProductImage from '../products/components/ProductImage';
import { messageTime, safeHttpUrl, safeImageUrl } from './customerChat';

function Price({ product }) {
  const { original_price: original, effective_price: effective } = product;
  if (effective == null) return <span className="cchat-price-missing">Chưa có giá</span>;
  if (original == null) return <strong className="price-display-current">{money(effective)}</strong>;
  const discount = Number(original) - Number(effective);
  return <PriceDisplay originalPrice={original} effectivePrice={effective} discountAmount={discount > 0 ? discount : 0} />;
}

function stockText(quantity) {
  // null means the backend could not determine stock; never present it as sold out.
  if (quantity == null) return 'Tồn kho: Chưa xác định';
  return quantity > 0 ? `Tồn kho: còn ${quantity}` : 'Tồn kho: hết hàng';
}

export function CustomerProductCard({ product }) {
  const detail = `/products/${encodeURIComponent(product.product_id)}`;
  const specs = [product.ram && `RAM ${product.ram}`, product.storage && `Bộ nhớ ${product.storage}`, product.color && `Màu ${product.color}`].filter(Boolean);
  return <article className="cchat-product" aria-label={product.product_name}>
    <div className="cchat-product-image"><ProductImage src={safeImageUrl(product.image_url)} alt={product.product_name} /></div>
    <div className="cchat-product-body">
      <h4><Link to={detail}>{product.product_name}</Link></h4>
      <p className="cchat-product-price"><Price product={product} /></p>
      {specs.length > 0 && <p className="cchat-product-specs">{specs.join(' · ')}</p>}
      <p className="cchat-product-meta">
        <span>{stockText(product.available_quantity)}</span>
        {product.warranty_months != null && <span>Bảo hành {product.warranty_months} tháng</span>}
        {product.sku && <span>SKU {product.sku}</span>}
      </p>
      {product.reasons?.length > 0 && <ul className="cchat-product-reasons">{product.reasons.map((reason) => <li key={reason}>{reason}</li>)}</ul>}
      <Link className="button button-quiet cchat-product-link" to={detail} aria-label={`Xem chi tiết ${product.product_name}`}>Xem chi tiết</Link>
    </div>
  </article>;
}

export function SourceList({ sources }) {
  return <div className="cchat-sources">
    <strong>Nguồn tham khảo</strong>
    <ul>{sources.map((source, index) => {
      const href = safeHttpUrl(source.source);
      return <li key={source.chunk_id || source.document_id || index}>
        {href ? <a href={href} target="_blank" rel="noopener noreferrer">{source.title}</a> : <span>{source.title}</span>}
      </li>;
    })}</ul>
  </div>;
}

export function MessageBubble({ item, onSuggestion, suggestionsDisabled }) {
  const assistant = item.role === 'ASSISTANT';
  const time = messageTime(item.createdAt);
  return <article className={`cchat-message ${assistant ? 'cchat-message-assistant' : 'cchat-message-user'}`}>
    <header><strong>{assistant ? 'Trợ lý Điện Việt' : 'Bạn'}</strong>{time && <time dateTime={item.createdAt}>{time}</time>}</header>
    {/* Plain text only: model output is never interpreted as HTML. */}
    <p className="cchat-message-text">{item.text}</p>
    {item.pending && <small className="cchat-message-note">Đang gửi…</small>}
    {item.products?.length > 0 && <div className="cchat-products">{item.products.map((product) => <CustomerProductCard key={`${product.product_id}-${product.variant_id || ''}`} product={product} />)}</div>}
    {item.sources?.length > 0 && <SourceList sources={item.sources} />}
    {item.suggestions?.length > 0 && <div className="cchat-suggestions" aria-label="Gợi ý câu hỏi tiếp theo">
      {item.suggestions.map((question) => <button type="button" key={question} disabled={suggestionsDisabled} onClick={() => onSuggestion(question)}>{question}</button>)}
    </div>}
  </article>;
}
