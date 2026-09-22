import { Route, Routes } from 'react-router';
import RequireAuth from './auth/RequireAuth';
import RequireRole from './auth/RequireRole';
import AppLayout from './components/layout/AppLayout';
import { MANAGEMENT_ROLES, ROLES } from './config/projectConfig';
import ProductsPage from './features/products/pages/ProductsPage';
import ProductDetailPage from './features/products/pages/ProductDetailPage';
import CategoriesPage from './features/categories/pages/CategoriesPage';
import OrdersPage from './features/orders/pages/OrdersPage';
import OrderDetailPage from './features/orders/pages/OrderDetailPage';
import CartPage from './features/cart/pages/CartPage';
import CheckoutPage from './features/cart/pages/CheckoutPage';
import CustomersPage from './features/customers/pages/CustomersPage';
import InventoryPage from './features/inventory/pages/InventoryPage';
import GoodsReceiptsPage from './features/inventory/pages/GoodsReceiptsPage';
import GoodsReceiptFormPage from './features/inventory/pages/GoodsReceiptFormPage';
import GoodsReceiptDetailPage from './features/inventory/pages/GoodsReceiptDetailPage';
import SerialsPage from './features/inventory/pages/SerialsPage';
import SerialDetailPage from './features/inventory/pages/SerialDetailPage';
import UserManagementPage from './features/user-management/pages/UserManagementPage';
import MyWarrantiesPage from './features/warranty/pages/MyWarrantiesPage';
import WarrantyDetailPage from './features/warranty/pages/WarrantyDetailPage';
import WarrantyLookupPage from './features/warranty/pages/WarrantyLookupPage';
import WarrantyTicketsPage from './features/warranty/pages/WarrantyTicketsPage';
import WarrantyTicketDetailPage from './features/warranty/pages/WarrantyTicketDetailPage';
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
        <Route path="forbidden" element={<ForbiddenPage />} />
        <Route element={<RequireAuth />}>
          <Route path="categories" element={<CategoriesPage />} />
          <Route path="products" element={<ProductsPage />} />
          <Route path="products/:productId" element={<ProductDetailPage />} />
          <Route path="account" element={<AccountPage />} />
          <Route element={<RequireRole roles={[ROLES.CUSTOMER]} />}>
            <Route path="cart" element={<CartPage />} />
            <Route path="checkout" element={<CheckoutPage />} />
            <Route path="my-orders" element={<OrdersPage customer />} />
            <Route path="my-orders/:orderId" element={<OrderDetailPage customer />} />
            <Route path="my-warranties" element={<MyWarrantiesPage />} />
            <Route path="my-warranties/:warrantyId" element={<WarrantyDetailPage customer />} />
            <Route path="my-warranty-tickets" element={<WarrantyTicketsPage customer />} />
            <Route path="my-warranty-tickets/:ticketId" element={<WarrantyTicketDetailPage customer />} />
          </Route>
          <Route element={<RequireRole roles={MANAGEMENT_ROLES} />}>
            <Route path="orders" element={<OrdersPage />} />
            <Route path="orders/:orderId" element={<OrderDetailPage />} />
            <Route path="customers" element={<CustomersPage />} />
            <Route path="inventory" element={<InventoryPage />} />
            <Route path="goods-receipts" element={<GoodsReceiptsPage />} />
            <Route path="goods-receipts/new" element={<GoodsReceiptFormPage />} />
            <Route path="goods-receipts/:receiptId" element={<GoodsReceiptDetailPage />} />
            <Route path="goods-receipts/:receiptId/edit" element={<GoodsReceiptFormPage />} />
            <Route path="serials" element={<SerialsPage />} />
            <Route path="serials/:serialId" element={<SerialDetailPage />} />
            <Route path="warranties" element={<WarrantyLookupPage />} />
            <Route path="warranties/:warrantyId" element={<WarrantyDetailPage />} />
            <Route path="warranty-tickets" element={<WarrantyTicketsPage />} />
            <Route path="warranty-tickets/:ticketId" element={<WarrantyTicketDetailPage />} />
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
