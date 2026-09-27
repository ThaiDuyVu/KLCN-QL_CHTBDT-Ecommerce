import { commerceRequest } from '../../orders/api/orderApi';

function query(values) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value));
  });
  return params.toString();
}

export const warrantyApi = {
  myWarranties(page = 0, signal) {
    return commerceRequest(`/warranties/mine?${query({ page, size: 20 })}`, { signal });
  },
  myWarranty(id, signal) {
    return commerceRequest(`/warranties/mine/${encodeURIComponent(id)}`, { signal });
  },
  myLookup(code, signal) {
    return commerceRequest(`/warranties/mine/lookup?${query({ code })}`, { signal });
  },
  createTicket(warrantyId, issueDescription) {
    return commerceRequest(`/warranties/mine/${encodeURIComponent(warrantyId)}/tickets`, {
      method: 'POST', body: { issueDescription },
    });
  },
  myTickets(page = 0, signal) {
    return commerceRequest(`/warranties/mine/tickets?${query({ page, size: 20 })}`, { signal });
  },
  myTicket(id, signal) {
    return commerceRequest(`/warranties/mine/tickets/${encodeURIComponent(id)}`, { signal });
  },
  warranties(page = 0, signal) {
    return commerceRequest(`/warranties?${query({ page, size: 20 })}`, { signal });
  },
  warranty(id, signal) {
    return commerceRequest(`/warranties/${encodeURIComponent(id)}`, { signal });
  },
  lookup(code, signal) {
    return commerceRequest(`/warranties/lookup?${query({ code })}`, { signal });
  },
  tickets(filters = {}, signal) {
    return commerceRequest(`/warranties/tickets?${query({ ...filters, size: 20 })}`, { signal });
  },
  ticket(id, signal) {
    return commerceRequest(`/warranties/tickets/${encodeURIComponent(id)}`, { signal });
  },
  updateTicket(id, status, resolutionNote) {
    return commerceRequest(`/warranties/tickets/${encodeURIComponent(id)}`, {
      method: 'PATCH', body: { status, resolutionNote: resolutionNote?.trim() || null },
    });
  },
};
