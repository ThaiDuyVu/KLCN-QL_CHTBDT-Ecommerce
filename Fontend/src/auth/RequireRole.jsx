import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from '../hooks/useAuth';
import SessionLoading from '../components/ui/SessionLoading';

export default function RequireRole({ roles }) {
  const { user, isLoading } = useAuth();
  const location = useLocation();
  if (isLoading) return <SessionLoading />;
  if (!user) return <Navigate to="/login" state={{ from: location }} replace />;
  return roles.includes(user.roleName) ? <Outlet /> : <Navigate to="/forbidden" replace />;
}
