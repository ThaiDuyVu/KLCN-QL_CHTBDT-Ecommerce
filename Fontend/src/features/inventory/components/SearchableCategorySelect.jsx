import { useEffect, useId, useMemo, useRef, useState } from 'react';

// Case-, accent- and spacing-insensitive form of Vietnamese text ("Điện  Thoại " -> "dien thoai").
function normalize(value) {
  return String(value || '').normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/gi, 'd')
    .toLowerCase().replace(/\s+/g, ' ').trim();
}

// Searchable replacement for the native category <select>: the search text only filters the
// list; `value` (categoryId) changes solely when the user picks an option.
export default function SearchableCategorySelect({ categories, value, onChange, label = 'Danh mục' }) {
  const id = useId();
  const [open, setOpen] = useState(false);
  const [categorySearchText, setCategorySearchText] = useState('');
  const [active, setActive] = useState(-1);
  const root = useRef(null);
  const trigger = useRef(null);
  const search = useRef(null);

  const indexed = useMemo(() => categories.map((item) => ({ ...item, key: normalize(item.categoryName) })), [categories]);
  const query = normalize(categorySearchText);
  const matches = useMemo(() => (query ? indexed.filter((item) => item.key.includes(query)) : indexed), [indexed, query]);
  // "Tất cả" stays first so the filter can always be cleared, even while searching.
  const options = useMemo(() => [{ categoryId: '', categoryName: 'Tất cả' }, ...matches], [matches]);
  const selected = categories.find((item) => item.categoryId === value);
  const optionId = (index) => `${id}-option-${index}`;

  useEffect(() => {
    if (!open) return undefined;
    search.current?.focus();
    function outside(event) {
      // Clicking elsewhere closes without changing the selected category.
      if (!root.current?.contains(event.target)) { setOpen(false); setCategorySearchText(''); setActive(-1); }
    }
    document.addEventListener('mousedown', outside);
    return () => document.removeEventListener('mousedown', outside);
  }, [open]);

  useEffect(() => {
    if (open && active >= 0) document.getElementById(optionId(active))?.scrollIntoView?.({ block: 'nearest' });
  });

  function show() {
    setCategorySearchText('');
    setActive(Math.max(0, value ? indexed.findIndex((item) => item.categoryId === value) + 1 : 0));
    setOpen(true);
  }

  function close(restoreFocus) {
    setOpen(false); setCategorySearchText(''); setActive(-1);
    if (restoreFocus) trigger.current?.focus();
  }

  function choose(option) {
    if (option.categoryId !== value) onChange(option.categoryId);
    close(true);
  }

  function onSearchKey(event) {
    if (event.key === 'ArrowDown') { event.preventDefault(); setActive((index) => Math.min(options.length - 1, index + 1)); }
    else if (event.key === 'ArrowUp') { event.preventDefault(); setActive((index) => Math.max(0, index - 1)); }
    else if (event.key === 'Home') { event.preventDefault(); setActive(0); }
    else if (event.key === 'End') { event.preventDefault(); setActive(options.length - 1); }
    else if (event.key === 'Enter') { event.preventDefault(); if (options[active]) choose(options[active]); }
    else if (event.key === 'Escape') { event.preventDefault(); close(true); }
    else if (event.key === 'Tab') close(false); // let focus move on; never trap it in the popup
  }

  return <div className="category-select" ref={root}>
    <span className="category-select-label" id={`${id}-label`}>{label}</span>
    <button type="button" ref={trigger} id={`${id}-trigger`} className="category-select-trigger"
      aria-haspopup="listbox" aria-expanded={open} aria-controls={open ? `${id}-listbox` : undefined}
      aria-labelledby={`${id}-label ${id}-trigger`}
      onClick={() => (open ? close(true) : show())}
      onKeyDown={(event) => { if (!open && (event.key === 'ArrowDown' || event.key === 'ArrowUp')) { event.preventDefault(); show(); } }}>
      <span>{value ? selected?.categoryName || 'Danh mục đã chọn' : 'Tất cả'}</span><span aria-hidden="true">▾</span>
    </button>
    {open && <div className="category-select-popup">
      <input ref={search} type="text" role="combobox" autoComplete="off" spellCheck="false"
        aria-label="Tìm danh mục" aria-expanded="true" aria-autocomplete="list" aria-controls={`${id}-listbox`}
        aria-activedescendant={active >= 0 ? optionId(active) : undefined}
        placeholder="Gõ tên danh mục…" value={categorySearchText}
        onChange={(event) => { setCategorySearchText(event.target.value); setActive(-1); }} onKeyDown={onSearchKey} />
      <ul id={`${id}-listbox`} role="listbox" aria-label={`${label}: kết quả`} className="category-select-list">
        {options.map((option, index) => {
          const isSelected = option.categoryId === value;
          return <li key={option.categoryId || 'all'} id={optionId(index)} role="option" aria-selected={isSelected}
            className={`category-select-option${index === active ? ' is-active' : ''}${isSelected ? ' is-selected' : ''}`}
            // mousedown keeps focus in the search box so the outside-click handler and blur do not race the click.
            onMouseDown={(event) => event.preventDefault()} onMouseEnter={() => setActive(index)} onClick={() => choose(option)}>
            <span>{option.categoryName}</span>{isSelected && <span className="category-select-check" aria-hidden="true">✓</span>}
          </li>;
        })}
      </ul>
      {!matches.length && <p className="category-select-empty" role="status">Không tìm thấy danh mục phù hợp.</p>}
    </div>}
  </div>;
}
