import { useEffect, useState } from 'react';
import { useAuth } from '../../../hooks/useAuth';
export default function useManagementRequest(key, load) {
  const { invalidateSession } = useAuth();
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState(null);
  const requestKey = `${key}:${revision}`;
  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal).then((data) => {
      if (!controller.signal.aborted) setResult({ key: requestKey, data });
    }).catch((error) => {
      if (controller.signal.aborted) return;
      if (error.status === 401) invalidateSession();
      setResult({ key: requestKey, error });
    });
    return () => controller.abort();
  }, [requestKey, load, invalidateSession]);
  const current = result?.key === requestKey ? result : null;
  return { data: current?.data, error: current?.error, loading: !current, reload: () => setRevision((v) => v + 1) };
}
