import { useState } from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import SearchableCategorySelect from './SearchableCategorySelect';

const CATEGORIES = [
  { categoryId: 'c-gaming', categoryName: 'Laptop Gaming' },
  { categoryId: 'c-office', categoryName: 'Laptop Văn phòng' },
  { categoryId: 'c-phone', categoryName: 'Điện thoại' },
  { categoryId: 'c-tablet', categoryName: 'Máy tính bảng' },
  { categoryId: 'c-acc', categoryName: 'Phụ kiện' },
];

function Harness({ categories = CATEGORIES, initial = '', onChange = () => {} }) {
  const [value, setValue] = useState(initial);
  return <>
    <SearchableCategorySelect categories={categories} value={value} onChange={(id) => { setValue(id); onChange(id); }} />
    <output data-testid="value">{value}</output>
    <button type="button">Bên ngoài</button>
  </>;
}

const trigger = () => screen.getByRole('button', { name: /Danh mục/ });
const searchBox = () => screen.getByRole('combobox', { name: 'Tìm danh mục' });
const optionNames = () => within(screen.getByRole('listbox')).getAllByRole('option').map((option) => option.textContent.replace('✓', ''));

describe('SearchableCategorySelect', () => {
  it('lists "Tất cả" plus every category and focuses the search box when opened', async () => {
    const user = userEvent.setup(); render(<Harness />);
    expect(trigger()).toHaveAttribute('aria-haspopup', 'listbox');
    expect(trigger()).toHaveAttribute('aria-expanded', 'false');
    await user.click(trigger());
    expect(trigger()).toHaveAttribute('aria-expanded', 'true');
    expect(searchBox()).toHaveFocus();
    expect(optionNames()).toEqual(['Tất cả', ...CATEGORIES.map((item) => item.categoryName)]);
  });

  it('filters by partial name, case- and accent-insensitively, trimming spaces', async () => {
    const user = userEvent.setup(); render(<Harness />);
    await user.click(trigger());
    await user.type(searchBox(), 'laptop');
    expect(optionNames()).toEqual(['Tất cả', 'Laptop Gaming', 'Laptop Văn phòng']);
    await user.clear(searchBox()); await user.type(searchBox(), '  dien thoai ');
    expect(optionNames()).toEqual(['Tất cả', 'Điện thoại']);
    await user.clear(searchBox()); await user.type(searchBox(), 'LAPTOP VĂN');
    expect(optionNames()).toEqual(['Tất cả', 'Laptop Văn phòng']);
    await user.clear(searchBox()); await user.type(searchBox(), 'may tinh  bang');
    expect(optionNames()).toEqual(['Tất cả', 'Máy tính bảng']);
  });

  it('shows an empty state and never creates a category', async () => {
    const user = userEvent.setup(); const onChange = vi.fn(); render(<Harness onChange={onChange} />);
    await user.click(trigger()); await user.type(searchBox(), 'tivi');
    expect(screen.getByText('Không tìm thấy danh mục phù hợp.')).toBeInTheDocument();
    expect(optionNames()).toEqual(['Tất cả']);
    await user.keyboard('{Enter}');
    expect(onChange).not.toHaveBeenCalled(); expect(screen.getByTestId('value')).toHaveTextContent('');
  });

  it('typing never changes the selected categoryId and does not auto-pick the first match', async () => {
    const user = userEvent.setup(); const onChange = vi.fn(); render(<Harness initial="c-acc" onChange={onChange} />);
    await user.click(trigger()); await user.type(searchBox(), 'laptop');
    expect(searchBox()).not.toHaveAttribute('aria-activedescendant');
    await user.keyboard('{Enter}');
    expect(onChange).not.toHaveBeenCalled(); expect(screen.getByTestId('value')).toHaveTextContent('c-acc');
  });

  it('selecting with the mouse sets the categoryId, closes and shows the name', async () => {
    const user = userEvent.setup(); const onChange = vi.fn(); render(<Harness onChange={onChange} />);
    await user.click(trigger()); await user.type(searchBox(), 'dien');
    await user.click(screen.getByRole('option', { name: 'Điện thoại' }));
    expect(onChange).toHaveBeenCalledWith('c-phone');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    expect(trigger()).toHaveTextContent('Điện thoại'); expect(trigger()).toHaveFocus();
  });

  it('"Tất cả" clears the filter', async () => {
    const user = userEvent.setup(); const onChange = vi.fn(); render(<Harness initial="c-phone" onChange={onChange} />);
    expect(trigger()).toHaveTextContent('Điện thoại');
    await user.click(trigger()); await user.click(screen.getByRole('option', { name: 'Tất cả' }));
    expect(onChange).toHaveBeenCalledWith(''); expect(trigger()).toHaveTextContent('Tất cả');
  });

  it('marks the selected category when reopened and starts with a fresh search', async () => {
    const user = userEvent.setup(); render(<Harness />);
    await user.click(trigger()); await user.type(searchBox(), 'gaming'); await user.click(screen.getByRole('option', { name: 'Laptop Gaming' }));
    await user.click(trigger());
    expect(searchBox()).toHaveValue('');
    const option = screen.getByRole('option', { name: /Laptop Gaming/ });
    expect(option).toHaveAttribute('aria-selected', 'true');
    expect(searchBox()).toHaveAttribute('aria-activedescendant', option.id);
    expect(screen.getAllByRole('option').filter((item) => item.getAttribute('aria-selected') === 'true')).toHaveLength(1);
  });

  it('works fully from the keyboard: Tab, Enter/Space to open, arrows, Enter to choose', async () => {
    const user = userEvent.setup(); const onChange = vi.fn(); render(<Harness onChange={onChange} />);
    await user.tab(); expect(trigger()).toHaveFocus();
    await user.keyboard('{Enter}'); expect(searchBox()).toHaveFocus();
    await user.keyboard('{Escape}'); expect(trigger()).toHaveFocus();
    await user.keyboard(' '); expect(searchBox()).toHaveFocus();
    await user.type(searchBox(), 'laptop');
    await user.keyboard('{ArrowDown}{ArrowDown}');
    expect(searchBox()).toHaveAttribute('aria-activedescendant', screen.getByRole('option', { name: 'Laptop Gaming' }).id);
    await user.keyboard('{ArrowDown}{ArrowUp}{ArrowDown}{Enter}');
    expect(onChange).toHaveBeenCalledWith('c-office'); expect(trigger()).toHaveTextContent('Laptop Văn phòng');
    expect(trigger()).toHaveFocus();
  });

  it('Escape closes without changing the selection', async () => {
    const user = userEvent.setup(); const onChange = vi.fn(); render(<Harness initial="c-tablet" onChange={onChange} />);
    await user.click(trigger()); await user.type(searchBox(), 'phu'); await user.keyboard('{ArrowDown}{ArrowDown}');
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled(); expect(trigger()).toHaveTextContent('Máy tính bảng');
  });

  it('clicking outside closes without changing the selection; Tab does not trap focus', async () => {
    const user = userEvent.setup(); const onChange = vi.fn(); render(<Harness initial="c-acc" onChange={onChange} />);
    await user.click(trigger()); await user.type(searchBox(), 'lap');
    await user.click(screen.getByRole('button', { name: 'Bên ngoài' }));
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument(); expect(onChange).not.toHaveBeenCalled();
    await user.click(trigger()); await user.tab();
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    expect(searchBox).toThrow();
  });

  it('stays usable with 400 synthetic categories', async () => {
    const user = userEvent.setup(); const onChange = vi.fn();
    const many = Array.from({ length: 400 }, (_, index) => ({ categoryId: `c${index}`, categoryName: `${index % 2 ? 'Điện thoại' : 'Phụ kiện'} nhóm ${index}` }));
    render(<Harness categories={many} onChange={onChange} />);
    await user.click(trigger());
    expect(screen.getAllByRole('option')).toHaveLength(401);
    await user.type(searchBox(), 'dien thoai nhom 39');
    expect(optionNames()).toEqual(['Tất cả', 'Điện thoại nhóm 39', ...['391', '393', '395', '397', '399'].map((n) => `Điện thoại nhóm ${n}`)]);
    await user.keyboard('{End}{Enter}');
    expect(onChange).toHaveBeenCalledWith('c399');
  });
});
