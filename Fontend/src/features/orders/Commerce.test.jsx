import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router';
import { AuthContext } from '../../auth/AuthContext';
import { cartApi } from '../cart/api/cartApi';
import { orderApi } from './api/orderApi';
import { installmentApi } from './api/installmentApi';
import { vnpayApi } from './api/vnpayApi';
import { redirectToVnpay } from './components/vnpayFormat';
import CartPage from '../cart/pages/CartPage';
import CheckoutPage from '../cart/pages/CheckoutPage';
import OrderDetailPage from './pages/OrderDetailPage';
import VnpayResultPage from './pages/VnpayResultPage';
import AddToCart from '../cart/components/AddToCart';
import { CartContext } from '../cart/cartContext';
import { WarehouseContext } from '../warehouses/warehouseContext';
vi.mock('../cart/api/cartApi', () => ({cartApi:{get:vi.fn(),add:vi.fn(),quantity:vi.fn(),remove:vi.fn()}}));
vi.mock('./api/orderApi', () => ({orderApi:{checkout:vi.fn(),detail:vi.fn(),cancel:vi.fn(),status:vi.fn()}}));
vi.mock('./api/installmentApi', () => ({installmentApi:{activeProviders:vi.fn()}}));
vi.mock('./api/vnpayApi', () => ({vnpayApi:{config:vi.fn(),paymentUrl:vi.fn(),synchronize:vi.fn()}}));
vi.mock('./components/vnpayFormat', async (importOriginal) => ({...(await importOriginal()),redirectToVnpay:vi.fn()}));
const cart={cartId:'cart',warehouseId:'warehouse',warehouseName:'Chi nhánh thử',subtotal:2000000,items:[{cartItemId:'item',variantId:'variant',productName:'Thiết bị thử',sku:'SKU',quantity:1,unitPrice:2000000,lineTotal:2000000}]};
const order={orderId:'order',orderCode:'ORD-TEST',status:'PENDING',recipientName:'Khách thử',recipientPhone:'0901234567',shippingAddress:'TP.HCM',items:[],totalAmount:2000000,payment:{paymentMethod:'COD',status:'PENDING'},allowedStatuses:['CANCELLED']};
const cartContext={cart,itemCount:1,isLoading:false,error:null,applyCart:vi.fn(),clearCart:vi.fn(),refreshCart:vi.fn()};
const warehouseContext={warehouses:[{warehouseId:'warehouse',warehouseName:'Chi nhánh thử'}],selectedWarehouse:{warehouseId:'warehouse',warehouseName:'Chi nhánh thử'},selectedWarehouseId:'warehouse',selectWarehouse:vi.fn().mockResolvedValue(true),isLoading:false,isChanging:false,error:''};
function mount(child,path='/'){return render(<MemoryRouter initialEntries={[path]}><AuthContext.Provider value={{user:{userId:'customer',roleName:'CUSTOMER'},invalidateSession:vi.fn()}}><CartContext.Provider value={cartContext}><WarehouseContext.Provider value={warehouseContext}>{child}</WarehouseContext.Provider></CartContext.Provider></AuthContext.Provider></MemoryRouter>);}
beforeEach(()=>{vi.resetAllMocks();cartContext.applyCart=vi.fn();cartContext.clearCart=vi.fn();warehouseContext.selectWarehouse=vi.fn().mockResolvedValue(true);vi.spyOn(window,'confirm').mockReturnValue(true);cartApi.get.mockResolvedValue(cart);orderApi.detail.mockResolvedValue(order);vnpayApi.config.mockResolvedValue({enabled:true});installmentApi.activeProviders.mockResolvedValue([{providerId:'provider-1',providerName:'Đơn vị nội bộ'}]);});
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
  await waitFor(()=>expect(screen.getByRole('option',{name:'VNPAY · Sandbox'})).toBeEnabled());expect(screen.getByRole('option',{name:'Trả góp nội bộ'})).toBeEnabled();
  fireEvent.click(screen.getByRole('button',{name:'Đặt hàng COD'}));
  await waitFor(()=>expect(orderApi.checkout).toHaveBeenCalledWith({recipientName:'Khách thử',recipientPhone:'0901234567',shippingAddress:'TP.HCM',note:null,paymentMethod:'COD'}));
  expect(cartContext.clearCart).toHaveBeenCalledTimes(1);
  expect(await screen.findByText('Đặt hàng thành công. Thanh toán COD khi nhận hàng.')).toBeInTheDocument();
});
it('checkout installment sends only application fields and shows the remaining amount',async()=>{
  orderApi.checkout.mockResolvedValue(order);
  mount(<Routes><Route index element={<CheckoutPage/>}/><Route path="my-orders/:orderId" element={<OrderDetailPage customer/>}/></Routes>);
  fireEvent.change(await screen.findByLabelText('Phương thức thanh toán'),{target:{value:'INSTALLMENT'}});
  await screen.findByRole('option',{name:'Đơn vị nội bộ'});
  fireEvent.change(screen.getByLabelText('Tên người nhận'),{target:{value:'Khách thử'}});
  fireEvent.change(screen.getByLabelText('Số điện thoại'),{target:{value:'0901234567'}});
  fireEvent.change(screen.getByLabelText('Địa chỉ giao hàng'),{target:{value:'TP.HCM'}});
  fireEvent.change(screen.getByLabelText('Đơn vị trả góp'),{target:{value:'provider-1'}});
  fireEvent.change(screen.getByLabelText('Kỳ hạn (tháng)'),{target:{value:'12'}});
  fireEvent.change(screen.getByLabelText('Tiền trả trước'),{target:{value:'500000'}});
  expect(screen.getByText(/1\.500\.000/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'Gửi hồ sơ trả góp'}));
  await waitFor(()=>expect(orderApi.checkout).toHaveBeenCalledWith({recipientName:'Khách thử',recipientPhone:'0901234567',shippingAddress:'TP.HCM',note:null,paymentMethod:'INSTALLMENT',providerId:'provider-1',termMonths:12,downPayment:500000}));
  expect(await screen.findByText('Đã tạo đơn và gửi hồ sơ trả góp. Đơn sẽ được xác nhận sau khi hồ sơ được duyệt.')).toBeInTheDocument();
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
it('checkout VNPAY sends no amount and redirects using the server payment URL',async()=>{
  const url='https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef=demo';
  orderApi.checkout.mockResolvedValue({...order,payment:{paymentMethod:'VNPAY',status:'PENDING',paymentUrl:url}});
  mount(<Routes><Route index element={<CheckoutPage/>}/><Route path="my-orders/:orderId" element={<OrderDetailPage customer/>}/></Routes>);
  await waitFor(()=>expect(screen.getByRole('option',{name:'VNPAY · Sandbox'})).toBeEnabled());
  fireEvent.change(screen.getByLabelText('Phương thức thanh toán'),{target:{value:'VNPAY'}});
  fireEvent.change(screen.getByLabelText('Tên người nhận'),{target:{value:'Khách thử'}});
  fireEvent.change(screen.getByLabelText('Số điện thoại'),{target:{value:'0901234567'}});
  fireEvent.change(screen.getByLabelText('Địa chỉ giao hàng'),{target:{value:'TP.HCM'}});
  fireEvent.click(screen.getByRole('button',{name:'Đặt hàng và thanh toán VNPAY'}));
  await waitFor(()=>expect(orderApi.checkout).toHaveBeenCalledWith({recipientName:'Khách thử',recipientPhone:'0901234567',shippingAddress:'TP.HCM',note:null,paymentMethod:'VNPAY'}));
  expect(redirectToVnpay).toHaveBeenCalledWith(url);expect(cartContext.clearCart).toHaveBeenCalledTimes(1);
});
it('disables VNPAY when backend sandbox configuration is disabled',async()=>{
  vnpayApi.config.mockResolvedValue({enabled:false});mount(<CheckoutPage/>);
  expect(await screen.findByRole('option',{name:'VNPAY · Sandbox · Chưa bật'})).toBeDisabled();
  expect(screen.getByRole('button',{name:'Đặt hàng COD'})).toBeEnabled();
});
it('does not treat a success query parameter as a paid Payment',async()=>{
  const id='11111111-1111-1111-1111-111111111111';
  orderApi.detail.mockResolvedValue({...order,orderId:id,payment:{paymentMethod:'VNPAY',status:'PENDING',amount:2000000}});
  mount(<VnpayResultPage/>,`/payments/vnpay/result?orderId=${id}&gatewayResult=SUCCESS&responseCode=00`);
  expect(await screen.findByRole('heading',{name:'Đang chờ xác nhận thanh toán'})).toBeInTheDocument();
  expect(screen.queryByRole('heading',{name:'Thanh toán đã được xác nhận'})).not.toBeInTheDocument();
  expect(orderApi.detail).toHaveBeenCalledWith(true,id,expect.any(AbortSignal));
  expect(vnpayApi.synchronize).not.toHaveBeenCalled();
});
it('refreshes the actual order payment after customer reconciliation',async()=>{
  const pending={...order,payment:{paymentMethod:'VNPAY',status:'PENDING',amount:2000000},allowedStatuses:[]};
  orderApi.detail.mockResolvedValue(pending);
  vnpayApi.synchronize.mockImplementation(async()=>{
    orderApi.detail.mockResolvedValue({...pending,payment:{...pending.payment,status:'PAID',transactionCode:'VNPAY-12345'}});
  });
  mount(<Routes><Route path="my-orders/:orderId" element={<OrderDetailPage customer/>}/></Routes>,'/my-orders/order');
  fireEvent.click(await screen.findByRole('button',{name:'Kiểm tra kết quả thanh toán'}));
  expect(await screen.findByText('Thanh toán: VNPAY · Đã thanh toán')).toBeInTheDocument();
  expect(vnpayApi.synchronize).toHaveBeenCalledWith('order');
});
it('keeps pending payment visible when reconciliation is unavailable',async()=>{
  orderApi.detail.mockResolvedValue({...order,payment:{paymentMethod:'VNPAY',status:'PENDING',amount:2000000},allowedStatuses:[]});
  vnpayApi.synchronize.mockRejectedValue(new Error('VNPAY giới hạn truy vấn lặp lại.'));
  mount(<Routes><Route path="my-orders/:orderId" element={<OrderDetailPage customer/>}/></Routes>,'/my-orders/order');
  fireEvent.click(await screen.findByRole('button',{name:'Kiểm tra kết quả thanh toán'}));
  expect(await screen.findByRole('alert')).toHaveTextContent('VNPAY giới hạn truy vấn lặp lại.');
  expect(await screen.findByText('Thanh toán: VNPAY · Chờ thanh toán')).toBeInTheDocument();
});
