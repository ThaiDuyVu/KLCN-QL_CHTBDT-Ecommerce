import { commerceRequest } from '../../orders/api/orderApi';

// At most 3 report requests in flight. Abort queued work when filters change.
let active = 0;
const queue = [];
function drain() {
  while (active < 3 && queue.length) {
    const job = queue.shift();
    job.signal?.removeEventListener('abort', job.cancel);
    if (job.signal?.aborted) { job.reject(job.signal.reason); continue; }
    active += 1;
    job.run().then(job.resolve, job.reject).finally(() => { active -= 1; drain(); });
  }
}
function schedule(run, signal) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) { reject(signal.reason); return; }
    const job = { run, signal, resolve, reject };
    job.cancel = () => {
      const index = queue.indexOf(job);
      if (index >= 0) queue.splice(index, 1);
      reject(signal.reason);
    };
    signal?.addEventListener('abort', job.cancel, { once: true });
    queue.push(job);
    drain();
  });
}

export const reportApi = {
  get(resource, filters, signal) {
    const query = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => {
      if (value !== '' && value != null) query.set(key, value);
    });
    return schedule(() => commerceRequest(`/reports/${resource}?${query}`, { signal }), signal);
  },
};
