import { createContext, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { authApi } from './api/authApi';

export const AuthContext = createContext(null);

// Only metadata lives in React; tokens stay in backend-managed HttpOnly cookies.
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [sessionError, setSessionError] = useState(null);
  const [isSigningOut, setIsSigningOut] = useState(false);
  const operation = useRef(0);

  useEffect(() => {
    let active = true;
    const version = operation.current;
    authApi.restoreSession().then((session) => {
      if (active && version === operation.current) setUser(session);
    }).catch((error) => {
      if (active && version === operation.current) setSessionError(error);
    }).finally(() => {
      if (active && version === operation.current) setIsLoading(false);
    });
    return () => { active = false; };
  }, []);

  const signIn = useCallback(async (credentials) => {
    operation.current += 1;
    const session = await authApi.login(credentials);
    setUser(session);
    setSessionError(null);
    setIsLoading(false);
    return session;
  }, []);

  const signOut = useCallback(async () => {
    operation.current += 1;
    setIsSigningOut(true);
    try {
      await authApi.logout();
      setUser(null);
      setSessionError(null);
    } finally {
      setIsSigningOut(false);
    }
  }, []);

  const invalidateSession = useCallback(() => {
    operation.current += 1;
    setUser(null);
    setIsLoading(false);
  }, []);

  const refreshSession = useCallback(async () => {
    const version = ++operation.current;
    const session = await authApi.restoreSession();
    if (version === operation.current) { setUser(session); setSessionError(null); }
    return session;
  }, []);

  const value = useMemo(() => ({
    user, isAuthenticated: Boolean(user), isLoading, sessionError, signIn, signOut, isSigningOut, invalidateSession, refreshSession,
  }), [user, isLoading, sessionError, signIn, signOut, isSigningOut, invalidateSession, refreshSession]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
