import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, expect, it, vi } from 'vitest';
import WarrantyDetailPage from './WarrantyDetailPage';
import { warrantyApi } from '../api/warrantyApi';

vi.mock('../api/warrantyApi', () => ({
  warrantyApi: { myWarranty: vi.fn(), createTicket: vi.fn() },
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

it('refreshes tickets and clears the form after asynchronous submission without an error', async () => {
  warrantyApi.createTicket.mockResolvedValue({ ticketId: 'test-ticket' });
  warrantyApi.myWarranty.mockResolvedValueOnce({ ...warranty, tickets: [{
    ticketId: 'test-ticket', ticketCode: 'WT-TEST', status: 'RECEIVED',
    issueDescription: 'Màn hình bị lỗi', createdAt: '2026-10-27',
  }] });

  const input = await submitIssue();

  expect(await screen.findByText('Đã gửi yêu cầu bảo hành.')).toBeInTheDocument();
  expect(screen.getByText('WT-TEST')).toBeInTheDocument();
  expect(input).toHaveValue('');
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  expect(warrantyApi.createTicket).toHaveBeenCalledExactlyOnceWith('test-warranty', 'Màn hình bị lỗi');
  expect(warrantyApi.myWarranty).toHaveBeenCalledTimes(2);
  expect(screen.getByRole('button', { name: 'Gửi yêu cầu bảo hành' })).toBeEnabled();
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
