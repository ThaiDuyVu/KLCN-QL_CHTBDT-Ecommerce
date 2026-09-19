import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router';
import { AuthContext } from '../../auth/AuthContext';
import { cartApi } from '../cart/api/cartApi';
import { orderApi } from './api/orderApi';
import CartPage from '../cart/pages/CartPage';
import CheckoutPage from '../cart/pages/CheckoutPage';
import OrderDetailPage from './pages/OrderDetailPage';
import AddToCart from '../cart/components/AddToCart';
import { CartContext } from '../cart/cartContext';
vi.mock('../cart/api/cartApi', () => ({cartApi:{get:vi.fn(),add:vi.fn(),quantity:vi.fn(),remove:vi.fn()}}));
vi.mock('./api/orderApi', () => ({orderApi:{checkout:vi.fn(),detail:vi.fn(),cancel:vi.fn(),status:vi.fn()}}));
const cart={cartId:'cart',subtotal:2000000,items:[{cartItemId:'item',variantId:'variant',productName:'Thiết bị thử',sku:'SKU',quantity:1,unitPrice:2000000,lineTotal:2000000}]};
const order={orderId:'order',orderCode:'ORD-TEST',status:'PENDING',recipientName:'Khách thử',recipientPhone:'0901234567',shippingAddress:'TP.HCM',items:[],totalAmount:2000000,payment:{paymentMethod:'COD',status:'PENDING'},allowedStatuses:['CANCELLED']};
const cartContext={cart,itemCount:1,isLoading:false,error:null,applyCart:vi.fn(),clearCart:vi.fn(),refreshCart:vi.fn()};
function mount(child){return render(<MemoryRouter><AuthContext.Provider value={{user:{userId:'customer',roleName:'CUSTOMER'},invalidateSession:vi.fn()}}><CartContext.Provider value={cartContext}>{child}</CartContext.Provider></AuthContext.Provider></MemoryRouter>);}
beforeEach(()=>{vi.resetAllMocks();cartContext.applyCart=vi.fn();cartContext.clearCart=vi.fn();vi.spyOn(window,'confirm').mockReturnValue(true);cartApi.get.mockResolvedValue(cart);orderApi.detail.mockResolvedValue(order);});
afterEach(()=>{cleanup();vi.restoreAllMocks();});
it('adds selected variant and quantity',async()=>{
  cartApi.add.mockResolvedValue(cart);mount(<AddToCart variantId="variant"/>);
  fireEvent.change(screen.getByLabelText('Số lượng'),{target:{value:'2'}});fireEvent.click(screen.getByRole('button',{name:'Thêm vào giỏ'}));
  await waitFor(()=>expect(cartApi.add).toHaveBeenCalledWith('variant',2));expect(cartContext.applyCart).toHaveBeenCalledWith(cart);expect(await screen.findByText('Xem giỏ hàng')).toBeInTheDocument();
});
it('changes quantities and deletes cart items with confirmation',async()=>{
  cartApi.quantity.mockResolvedValue(cart);cartApi.remove.mockResolvedValue({...cart,items:[]});mount(<CartPage/>);
  fireEvent.change(await screen.findByLabelText('Số lượng SKU'),{target:{value:'3'}});fireEvent.click(screen.getByRole('button',{name:'Cập nhật'}));
  await waitFor(()=>expect(cartApi.quantity).toHaveBeenCalledWith('item',3));await screen.findByRole('button',{name:'Xóa'});
  cartApi.get.mockResolvedValue({...cart,items:[]});fireEvent.click(screen.getByRole('button',{name:'Xóa'}));
  await waitFor(()=>expect(cartApi.remove).toHaveBeenCalledWith('item'));expect(await screen.findByText('Giỏ hàng trống.')).toBeInTheDocument();
});
it('checkout only sends shipping details and selected COD without prices',async()=>{
  orderApi.checkout.mockResolvedValue(order);
  mount(<Routes><Route index element={<CheckoutPage/>}/><Route path="my-orders/:orderId" element={<OrderDetailPage customer/>}/></Routes>);
  fireEvent.change(await screen.findByLabelText('Tên người nhận'),{target:{value:'  Khách thử  '}});fireEvent.change(screen.getByLabelText('Số điện thoại'),{target:{value:'0901234567'}});fireEvent.change(screen.getByLabelText('Địa chỉ giao hàng'),{target:{value:'TP.HCM'}});
  expect(screen.getByRole('option',{name:'VNPAY · Coming soon'})).toBeDisabled();expect(screen.getByRole('option',{name:'Trả góp · Coming soon'})).toBeDisabled();
  fireEvent.click(screen.getByRole('button',{name:'Đặt hàng COD'}));
  await waitFor(()=>expect(orderApi.checkout).toHaveBeenCalledWith({recipientName:'Khách thử',recipientPhone:'0901234567',shippingAddress:'TP.HCM',note:null,paymentMethod:'COD'}));
  expect(cartContext.clearCart).toHaveBeenCalledTimes(1);
  expect(await screen.findByText('Đặt hàng thành công. Thanh toán COD khi nhận hàng.')).toBeInTheDocument();
});
it('pending unpaid orders can cancel',async()=>{
  mount(<Routes><Route path="*" element={<OrderDetailPage customer/>}/></Routes>);
  orderApi.cancel.mockResolvedValue({...order,status:'CANCELLED',allowedStatuses:[]});fireEvent.click(await screen.findByRole('button',{name:'Hủy đơn'}));
  orderApi.detail.mockResolvedValue({...order,status:'CANCELLED',allowedStatuses:[]});await waitFor(()=>expect(orderApi.cancel).toHaveBeenCalledTimes(1));
  await screen.findByText('Không có thao tác trạng thái phù hợp ở bước này.');
});
it('shows stock errors and preserves checkout form for correction',async()=>{
  orderApi.checkout.mockRejectedValue(new Error('Không đủ stock'));mount(<CheckoutPage/>);await screen.findByLabelText('Tên người nhận');
  fireEvent.submit(screen.getByRole('button',{name:'Đặt hàng COD'}).closest('form'));
  expect(await screen.findByRole('alert')).toHaveTextContent('Không đủ stock');expect(screen.getByLabelText('Địa chỉ giao hàng')).toBeInTheDocument();
});
it('hides customer cancel for paid orders',async()=>{
  orderApi.detail.mockResolvedValue({...order,payment:{paymentMethod:'COD',status:'PAID'},allowedStatuses:[]});
  mount(<Routes><Route path="*" element={<OrderDetailPage customer/>}/></Routes>);
  expect(await screen.findByText('Thanh toán: COD · Đã thanh toán')).toBeInTheDocument();expect(screen.queryByRole('button',{name:'Hủy đơn'})).not.toBeInTheDocument();
});
it('management exposes only transitions returned by the server',async()=>{
  orderApi.detail.mockResolvedValue({...order,status:'CONFIRMED',allowedStatuses:['PROCESSING','CANCELLED']});orderApi.status.mockResolvedValue({...order,status:'PROCESSING'});
  mount(<Routes><Route path="*" element={<OrderDetailPage/>}/></Routes>);
  fireEvent.click(await screen.findByRole('button',{name:'Bắt đầu xử lý'}));await waitFor(()=>expect(orderApi.status).toHaveBeenCalledWith(undefined,'PROCESSING'));
  expect(screen.queryByRole('button',{name:'Xác nhận đã giao'})).not.toBeInTheDocument();
});
