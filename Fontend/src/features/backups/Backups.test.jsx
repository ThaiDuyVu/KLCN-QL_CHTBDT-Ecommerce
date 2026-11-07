import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { AuthContext } from '../../auth/AuthContext';
import { backupApi } from './backupApi';
import BackupsPage from './BackupsPage';

vi.mock('./backupApi', () => ({ backupApi: { list: vi.fn(), create: vi.fn(), restore: vi.fn(), remove: vi.fn(), downloadUrl: (n) => `/api/admin/backups/${n}/download` } }));
const row = { name: 'backup-20261106-100000.dump', sizeBytes: 2048000, createdAt: '2026-11-06T03:00:00Z', automatic: false };
const mount = () => render(<AuthContext.Provider value={{ user: { roleName: 'ADMIN' }, invalidateSession: vi.fn() }}><BackupsPage /></AuthContext.Provider>);
beforeEach(() => { vi.resetAllMocks(); backupApi.list.mockResolvedValue([row]); });
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it('creates a backup and lists downloadable files', async () => {
  backupApi.create.mockResolvedValue({ ...row, name: 'backup-20261106-110000.dump' });
  mount();
  expect(await screen.findByRole('link', { name: 'Tải về' })).toHaveAttribute('href', '/api/admin/backups/backup-20261106-100000.dump/download');
  fireEvent.click(screen.getByRole('button', { name: 'Sao lưu ngay' }));
  expect(await screen.findByText(/Đã sao lưu: backup-20261106-110000.dump/)).toBeInTheDocument();
});

it('restores only after the admin retypes the exact backup name', async () => {
  backupApi.restore.mockResolvedValue({ restored: row.name, safetyBackup: { name: 'backup-20261106-120000-pre-restore.dump' } });
  vi.spyOn(window, 'prompt').mockReturnValueOnce('backup-sai.dump').mockReturnValueOnce(` ${row.name} `);
  mount();
  fireEvent.click(await screen.findByRole('button', { name: 'Khôi phục' }));
  expect(await screen.findByText('Tên xác nhận không khớp, chưa khôi phục.')).toBeInTheDocument();
  expect(backupApi.restore).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Khôi phục' }));
  await waitFor(() => expect(backupApi.restore).toHaveBeenCalledWith(row.name, row.name));
  expect(await screen.findByText(/backup-20261106-120000-pre-restore.dump/)).toBeInTheDocument();
});
