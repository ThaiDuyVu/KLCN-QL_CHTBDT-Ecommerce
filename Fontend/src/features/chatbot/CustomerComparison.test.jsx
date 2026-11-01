import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { MessageBubble } from './CustomerChatParts';
import { mergeHistory } from './customerChat';

const comparison = {
  category_name: 'Laptop',
  products: [
    { product_id: 'p-xps', product_name: 'Dell XPS 13', category_name: 'Laptop' },
    { product_id: 'p-mba', product_name: 'MacBook Air M2', category_name: 'Laptop' },
  ],
  rows: [
    { key: 'price', label: 'Giá', values: ['32.990.000 đ', '22.990.000 đ'], best: [1] },
    { key: 'ram', label: 'RAM', values: ['16GB / 32GB', '8GB'], best: [0] },
    { key: 'cpu', label: 'CPU', values: ['Intel Core Ultra', 'Apple M2'], best: [] },
  ],
  summary: ['MacBook Air M2 có giá khởi điểm thấp hơn 10.000.000 đ.'],
};

function renderBubble(item) {
  return render(<MemoryRouter><MessageBubble item={{ id: 'a1', role: 'ASSISTANT', text: 'So sánh…', ...item }} onSuggestion={() => {}} /></MemoryRouter>);
}

describe('comparison table', () => {
  it('shows each product as a column and each specification as a row', () => {
    renderBubble({ comparison });
    const table = within(screen.getByRole('region', { name: 'Bảng so sánh Dell XPS 13 và MacBook Air M2' })).getByRole('table');
    expect(within(table).getByRole('columnheader', { name: 'Dell XPS 13' })).toBeInTheDocument();
    expect(within(table).getByRole('link', { name: 'MacBook Air M2' })).toHaveAttribute('href', '/products/p-mba');
    const ram = within(table).getByRole('row', { name: /RAM/ });
    expect(within(ram).getAllByRole('cell').map((cell) => cell.textContent)).toEqual(['16GB / 32GBTốt hơn', '8GB']);
  });

  it('marks only measurably better values; text specifications are never judged', () => {
    renderBubble({ comparison });
    const cpu = screen.getByRole('row', { name: /CPU/ });
    expect(within(cpu).queryByText('Tốt hơn')).not.toBeInTheDocument();
    const price = screen.getByRole('row', { name: /Giá/ });
    expect(within(within(price).getAllByRole('cell')[1]).getByText('Tốt hơn')).toBeInTheDocument();
  });

  it('renders nothing for a malformed comparison', () => {
    renderBubble({ comparison: { products: [], rows: [] } });
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('keeps the table when history is re-synchronised', () => {
    const live = [{ id: 'x', role: 'ASSISTANT', text: 'So sánh…', comparison }];
    const [row] = mergeHistory([{ id: 's1', sender_type: 'ASSISTANT', message: 'So sánh…', created_at: '2026-10-26T10:00:00Z' }], live);
    expect(row.comparison).toBe(comparison);
  });
});
