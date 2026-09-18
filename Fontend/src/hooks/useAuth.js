import { useContext } from 'react';
import { AuthContext } from '../auth/AuthContext';

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth phải được sử dụng bên trong AuthProvider.');
  return context;
}
