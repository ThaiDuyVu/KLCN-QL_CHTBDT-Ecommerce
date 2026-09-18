import { useEffect, useState } from 'react';
import { useAuth } from '../../../hooks/useAuth';

export default function useProductRequest(resourceKey, load) {
  const { invalidateSession } = useAuth();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState(null);
  const requestKey = `${resourceKey}:${attempt}`;

  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal).then((data) => {
      if (!controller.signal.aborted) setResult({ key: requestKey, data, error: null });
    }).catch((error) => {
      if (controller.signal.aborted) return;
      if (error.status === 401) invalidateSession();
      setResult({ key: requestKey, data: null, error });
    });
    return () => controller.abort();
  }, [load, requestKey, invalidateSession]);

  const current = result?.key === requestKey ? result : null;
  return {
    data: current?.data,
    error: current?.error,
    isLoading: !current,
    retry: () => setAttempt((value) => value + 1),
  };
}
