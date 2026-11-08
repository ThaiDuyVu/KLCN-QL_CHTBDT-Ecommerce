import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, expect, it, vi } from 'vitest';
import WarrantyDetailPage from './WarrantyDetailPage';
import { warrantyApi } from '../api/warrantyApi';

vi.mock('../api/warrantyApi', () => ({
  warrantyApi: { myWarranty: vi.fn(), createTicket: vi.fn(), warranty: vi.fn(), staffCreateTicket: vi.fn() },
}));

const warranty = {
  productName: 'Synthetic device', sku: 'TEST-SKU', serialNumber: 'TEST-SERIAL',
  customerName: 'Synthetic', orderCode: 'TEST-ORDER', startDate: '2026-10-21',
  endDate: '2027-10-21', status: 'ACTIVE', eligible: true, tickets: [],
};

beforeEach(() => {
  warrantyApi.myWarranty.mockReset();
  warrantyApi.createTicket.mockReset();
  warrantyApi.myWarranty.mockResolvedValueOnce(warranty);
});

async function submitIssue() {
  render(<MemoryRouter initialEntries={['/my-warranties/test-warranty']}>
    <Routes><Route path="/my-warranties/:warrantyId" element={<WarrantyDetailPage customer />} /></Routes>
  </MemoryRouter>);
  const user = userEvent.setup();
  const input = await screen.findByRole('textbox', { name: 'Mô tả lỗi' });
  await user.type(input, '  Màn hình bị lỗi  ');
  await user.click(screen.getByRole('button', { name: 'Gửi yêu cầu bảo hành' }));
  return input;
}

it('refreshes tickets and replaces the form with the open-ticket notice after submission', async () => {
  warrantyApi.createTicket.mockResolvedValue({ ticketId: 'test-ticket' });
  warrantyApi.myWarranty.mockResolvedValueOnce({ ...warranty, tickets: [{
    ticketId: 'test-ticket', ticketCode: 'WT-TEST', status: 'RECEIVED',
    issueDescription: 'Màn hình bị lỗi', createdAt: '2026-10-27',
  }] });

  await submitIssue();

  expect(await screen.findByText('Đã gửi yêu cầu bảo hành.')).toBeInTheDocument();
  expect(screen.getAllByText('WT-TEST').length).toBeGreaterThan(0);
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  expect(warrantyApi.createTicket).toHaveBeenCalledExactlyOnceWith('test-warranty', 'Màn hình bị lỗi');
  expect(warrantyApi.myWarranty).toHaveBeenCalledTimes(2);
  expect(screen.queryByRole('button', { name: 'Gửi yêu cầu bảo hành' })).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Xem phiếu đang xử lý' })).toHaveAttribute('href', '/my-warranty-tickets/test-ticket');
});

it('does not offer a new request while the device already has a ticket in progress', async () => {
  warrantyApi.myWarranty.mockReset();
  warrantyApi.myWarranty.mockResolvedValueOnce({ ...warranty, tickets: [
    { ticketId: 'open', ticketCode: 'WT-OPEN', status: 'IN_PROGRESS', issueDescription: 'Lỗi pin', createdAt: '2026-10-28' },
    { ticketId: 'done', ticketCode: 'WT-DONE', status: 'COMPLETED', issueDescription: 'Lỗi loa', createdAt: '2026-10-01' },
  ] });
  render(<MemoryRouter initialEntries={['/my-warranties/test-warranty']}>
    <Routes><Route path="/my-warranties/:warrantyId" element={<WarrantyDetailPage customer />} /></Routes>
  </MemoryRouter>);

  expect(await screen.findByRole('link', { name: 'Xem phiếu đang xử lý' })).toHaveAttribute('href', '/my-warranty-tickets/open');
  expect(screen.queryByRole('textbox', { name: 'Mô tả lỗi' })).not.toBeInTheDocument();
});

it('preserves the issue and shows the API error when ticket creation fails', async () => {
  warrantyApi.createTicket.mockRejectedValue(new Error('Không thể tiếp nhận yêu cầu'));

  const input = await submitIssue();

  expect(await screen.findByRole('alert')).toHaveTextContent('Không thể tiếp nhận yêu cầu');
  expect(input).toHaveValue('  Màn hình bị lỗi  ');
  expect(screen.queryByText('Đã gửi yêu cầu bảo hành.')).not.toBeInTheDocument();
  expect(warrantyApi.createTicket).toHaveBeenCalledTimes(1);
  expect(warrantyApi.myWarranty).toHaveBeenCalledTimes(1);
  expect(screen.getByRole('button', { name: 'Gửi yêu cầu bảo hành' })).toBeEnabled();
});

it('staff opens a counter ticket through the staff endpoint and sees the new ticket code', async () => {
  warrantyApi.warranty.mockReset();
  warrantyApi.warranty.mockResolvedValueOnce(warranty).mockResolvedValueOnce({ ...warranty, tickets: [{
    ticketId: 'counter-ticket', ticketCode: 'WT-COUNTER', status: 'RECEIVED', issueDescription: 'Không lên nguồn',
    createdAt: '2026-11-06', employeeName: 'Đỗ Văn Kỹ',
  }] });
  warrantyApi.staffCreateTicket.mockResolvedValue({ ticketId: 'counter-ticket', ticketCode: 'WT-COUNTER' });
  render(<MemoryRouter initialEntries={['/warranties/test-warranty']}>
    <Routes><Route path="/warranties/:warrantyId" element={<WarrantyDetailPage />} /></Routes>
  </MemoryRouter>);
  const user = userEvent.setup();
  await user.type(await screen.findByRole('textbox', { name: 'Tình trạng lỗi khách báo' }), 'Không lên nguồn');
  await user.click(screen.getByRole('button', { name: 'Lập phiếu tiếp nhận' }));
  expect(await screen.findByText('Đã lập phiếu tiếp nhận WT-COUNTER.')).toBeInTheDocument();
  expect(warrantyApi.staffCreateTicket).toHaveBeenCalledExactlyOnceWith('test-warranty', 'Không lên nguồn');
  expect(warrantyApi.createTicket).not.toHaveBeenCalled();
  expect(screen.getByText(/Đỗ Văn Kỹ/)).toBeInTheDocument();
});
