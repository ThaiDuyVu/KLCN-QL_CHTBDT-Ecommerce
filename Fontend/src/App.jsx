import { Route, Routes } from 'react-router';
import RequireAuth from './auth/RequireAuth';
import RequireRole from './auth/RequireRole';
import AppLayout from './components/layout/AppLayout';
import { MANAGEMENT_ROLES, ROLES } from './config/projectConfig';
import ProductsPage from './features/products/pages/ProductsPage';
import ProductDetailPage from './features/products/pages/ProductDetailPage';
import CategoriesPage from './features/categories/pages/CategoriesPage';
import OrdersPage from './features/orders/pages/OrdersPage';
import CustomersPage from './features/customers/pages/CustomersPage';
import InventoryPage from './features/inventory/pages/InventoryPage';
import UserManagementPage from './features/user-management/pages/UserManagementPage';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import AccountPage from './pages/AccountPage';
import ForbiddenPage from './pages/ForbiddenPage';
import NotFoundPage from './pages/NotFoundPage';

export default function App() {
  return (
    <Routes>
      <Route path="login" element={<LoginPage />} />
      <Route element={<AppLayout />}>
        <Route index element={<HomePage />} />
        <Route path="categories" element={<CategoriesPage />} />
        <Route path="forbidden" element={<ForbiddenPage />} />
        <Route element={<RequireAuth />}>
          <Route path="products" element={<ProductsPage />} />
          <Route path="products/:productId" element={<ProductDetailPage />} />
          <Route path="account" element={<AccountPage />} />
          <Route element={<RequireRole roles={MANAGEMENT_ROLES} />}>
            <Route path="orders" element={<OrdersPage />} />
            <Route path="customers" element={<CustomersPage />} />
            <Route path="inventory" element={<InventoryPage />} />
          </Route>
          <Route element={<RequireRole roles={[ROLES.ADMIN]} />}>
            <Route path="user-management" element={<UserManagementPage />} />
          </Route>
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
