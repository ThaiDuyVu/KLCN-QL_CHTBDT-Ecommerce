import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { useCart } from '../../../hooks/useCart';
import { useWarehouse } from '../../../hooks/useWarehouse';
import './BranchSelectionModal.css';

const SEARCH_THRESHOLD = 6;
const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

function normalize(value) {
  return String(value || '').normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/gi, 'd').toLowerCase().replace(/\s+/g, ' ').trim();
}

// Mandatory branch step for CUSTOMER storefront sessions. It cannot be dismissed: no backdrop
// click, no Escape, no skip. The only ways out are confirming a branch or leaving the session.
export default function BranchSelectionModal({ onLogout, isSigningOut, logoutError = '' }) {
  const id = useId();
  const { warehouses, selectedWarehouseId, selectWarehouse, isChanging, error, listStatus, reloadWarehouses,
    clearRequiredFor, cartReady } = useWarehouse();
  const { cart, error: cartError, refreshCart } = useCart();
  const [choice, setChoice] = useState(selectedWarehouseId);
  const [query, setQuery] = useState('');
  const [confirmClear, setConfirmClear] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const dialog = useRef(null);
  const title = useRef(null);
  const busy = useRef(false);
  // A preselection only becomes available once the list (and cart) are verified.
  const chosen = choice || selectedWarehouseId;
  const chosenWarehouse = warehouses.find((warehouse) => warehouse.warehouseId === chosen) || null;
  const cartWarehouse = warehouses.find((warehouse) => warehouse.warehouseId === cart?.warehouseId) || null;
  const filtered = useMemo(() => {
    const term = normalize(query);
    return term ? warehouses.filter((warehouse) => normalize(`${warehouse.warehouseName} ${warehouse.address || ''}`).includes(term)) : warehouses;
  }, [query, warehouses]);
  const ready = listStatus === 'ready' && cartReady && warehouses.length > 0;
  const working = submitting || isChanging;

  useEffect(() => {
    const node = dialog.current;
    if (!node) return;
    (node.querySelector('input[type="radio"]:checked') || title.current)?.focus();
  }, [ready]);

  function onKeyDown(event) {
    if (event.key === 'Escape') { event.preventDefault(); return; }
    if (event.key !== 'Tab') return;
    // Tab stops as the browser sees them: a radio group is one stop (focused, checked or first radio).
    const all = [...dialog.current.querySelectorAll(FOCUSABLE)].filter((item) => !item.closest('[hidden]'));
    const items = [];
    for (const item of all) {
      if (item.type !== 'radio' || !item.name) { items.push(item); continue; }
      const group = all.filter((other) => other.type === 'radio' && other.name === item.name);
      if (group[0] !== item) continue;
      items.push(group.find((radio) => radio === document.activeElement) || group.find((radio) => radio.checked) || group[0]);
    }
    if (!items.length) { event.preventDefault(); return; }
    // Wrap at both ends, and also when focus sits on a non-cycle element such as the title.
    const index = items.indexOf(document.activeElement);
    if (event.shiftKey && index <= 0) { event.preventDefault(); items.at(-1).focus(); }
    else if (!event.shiftKey && (index === -1 || index === items.length - 1)) { event.preventDefault(); items[0].focus(); }
  }

  async function submit(event) {
    event.preventDefault();
    if (!chosenWarehouse || working || busy.current) return;
    if (clearRequiredFor(chosenWarehouse.warehouseId) && !confirmClear) { setConfirmClear(true); return; }
    busy.current = true; setSubmitting(true);
    try {
      // Same provider path as the header selector; clearItems semantics are unchanged.
      await selectWarehouse(chosenWarehouse.warehouseId, { confirmClear: () => true });
    } finally {
      busy.current = false; setSubmitting(false); setConfirmClear(false);
    }
  }

  let body;
  if (listStatus === 'loading' || (listStatus === 'ready' && !cartReady && !cartError)) {
    body = <p className="branch-modal-status" role="status">Đang tải danh sách chi nhánh…</p>;
  } else if (listStatus === 'error') {
    body = <div className="branch-modal-problem" role="alert"><p>Không thể tải danh sách chi nhánh. Vui lòng thử lại.</p>
      <button type="button" className="button" onClick={reloadWarehouses}>Thử lại</button></div>;
  } else if (cartError) {
    body = <div className="branch-modal-problem" role="alert"><p>Không thể kiểm tra giỏ hàng hiện tại. Vui lòng thử lại.</p>
      <button type="button" className="button" onClick={() => refreshCart().catch(() => {})}>Thử lại</button></div>;
  } else if (!warehouses.length) {
    body = <p className="branch-modal-problem" role="alert">Hiện chưa có chi nhánh mua sắm khả dụng. Vui lòng liên hệ cửa hàng.</p>;
  } else {
    body = <form id={`${id}-form`} onSubmit={submit} className="branch-modal-form">
      {warehouses.length > SEARCH_THRESHOLD && <label className="branch-modal-search">
        <span>Tìm chi nhánh</span>
        <input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Tên hoặc địa chỉ chi nhánh" disabled={working} />
      </label>}
      <fieldset className="branch-modal-options" disabled={working || confirmClear}>
        <legend className="sr-only">Chi nhánh mua sắm</legend>
        {filtered.map((warehouse) => {
          const checked = warehouse.warehouseId === chosen;
          return <label key={warehouse.warehouseId} className={`branch-option${checked ? ' is-selected' : ''}`}>
            <input type="radio" name={`${id}-branch`} value={warehouse.warehouseId} checked={checked}
              onChange={() => { setChoice(warehouse.warehouseId); setConfirmClear(false); }} />
            <span className="branch-option-text">
              <strong>{warehouse.warehouseName}</strong>
              {warehouse.address && <span>{warehouse.address}</span>}
              {cartWarehouse?.warehouseId === warehouse.warehouseId && <small>Chi nhánh của giỏ hàng hiện tại</small>}
            </span>
          </label>;
        })}
        {!filtered.length && <p className="branch-modal-empty">Không tìm thấy chi nhánh phù hợp.</p>}
      </fieldset>
      {confirmClear && chosenWarehouse && <div className="branch-modal-confirm" role="alert">
        <p>Giỏ hàng đang có {cart?.items?.length} sản phẩm thuộc {cartWarehouse ? `chi nhánh ${cartWarehouse.warehouseName}` : 'chi nhánh khác'}.
          Đổi sang {chosenWarehouse.warehouseName} sẽ xóa toàn bộ sản phẩm trong giỏ.</p>
        <div><button type="button" className="button button-quiet" onClick={() => setConfirmClear(false)} disabled={working}>Giữ giỏ hàng hiện tại</button></div>
      </div>}
      {error && <p className="branch-modal-error" role="alert">{error}</p>}
    </form>;
  }

  // storefront-shell only scopes the retail theme tokens; the overlay itself is .branch-gate.
  return <div className="branch-gate storefront-shell">
    <div className="branch-modal" role="dialog" aria-modal="true" aria-labelledby={`${id}-title`} aria-describedby={`${id}-desc`} ref={dialog} onKeyDown={onKeyDown}>
      <h2 id={`${id}-title`} ref={title} tabIndex={-1}>Chọn chi nhánh mua sắm</h2>
      <p id={`${id}-desc`}>Vui lòng chọn chi nhánh bạn muốn mua hàng. Sản phẩm, tồn kho và giỏ hàng sẽ được hiển thị theo chi nhánh này.</p>
      {body}
      {logoutError && <p className="branch-modal-error" role="alert">{logoutError}</p>}
      <div className="branch-modal-actions">
        <div className="branch-modal-exit">
          <button type="button" className="button button-quiet" onClick={onLogout} disabled={isSigningOut || working}>{isSigningOut ? 'Đang thoát…' : 'Đăng xuất'}</button>
        </div>
        {ready && <button type="submit" form={`${id}-form`} className="button" disabled={!chosenWarehouse || working}>
          {working ? 'Đang xác nhận…' : confirmClear ? 'Xóa giỏ và tiếp tục' : 'Tiếp tục mua sắm'}
        </button>}
      </div>
    </div>
  </div>;
}
