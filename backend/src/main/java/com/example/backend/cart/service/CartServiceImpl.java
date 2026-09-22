package com.example.backend.cart.service;
import com.example.backend.cart.dto.*;
import com.example.backend.cart.entity.*;
import com.example.backend.cart.repository.*;
import com.example.backend.order.exception.CommerceException;
import com.example.backend.inventory.repository.InventoryRepository;
import com.example.backend.product.entity.*;
import com.example.backend.product.repository.ProductVariantRepository;
import com.example.backend.warehouse.entity.WarehouseStatus;
import com.example.backend.warehouse.repository.WarehouseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class CartServiceImpl implements CartService {
    private final CustomerCartRepository customers;
    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProductVariantRepository variants;
    private final InventoryRepository inventory;
    private final WarehouseRepository warehouses;
    public CartServiceImpl(CustomerCartRepository customers, CartRepository carts, CartItemRepository items,
                           ProductVariantRepository variants, InventoryRepository inventory,
                           WarehouseRepository warehouses) {
        this.customers = customers; this.carts = carts; this.items = items; this.variants = variants;
        this.inventory = inventory; this.warehouses = warehouses;
    }
    private Cart current(UUID userId, boolean create) {
        var customer = customers.lockByUserId(userId).orElseThrow(() -> new CommerceException(403, "Tài khoản chưa có hồ sơ customer"));
        var cart = carts.findFirstByCustomerIdAndStatusOrderByCreatedAtAscCartIdAsc(customer.getCustomerId(), "ACTIVE").orElse(null);
        if (cart == null && create) {
            cart = new Cart(); cart.setCustomerId(customer.getCustomerId()); cart.setStatus("ACTIVE");
            cart.setCreatedAt(OffsetDateTime.now()); cart.setUpdatedAt(OffsetDateTime.now()); carts.saveAndFlush(cart);
        }
        return cart;
    }
    public CartResponse get(UUID userId) { return response(current(userId, false)); }
    public CartResponse selectWarehouse(UUID userId, SelectCartWarehouseRequest request) {
        var warehouse = warehouses.findById(request.getWarehouseId())
                .orElseThrow(() -> new CommerceException(404, "Không tìm thấy chi nhánh"));
        if (warehouse.getStatus() != WarehouseStatus.ACTIVE) {
            throw new CommerceException(409, "Chi nhánh đã ngừng hoạt động");
        }
        var cart = current(userId, true);
        if (Objects.equals(cart.getWarehouseId(), warehouse.getWarehouseId())) return response(cart);
        boolean hasItems = items.countByCartId(cart.getCartId()) > 0;
        if (hasItems && !request.isClearItems()) {
            throw new CommerceException(409,
                    "Giỏ hàng đang có sản phẩm tại chi nhánh khác; cần xác nhận xóa giỏ trước khi đổi chi nhánh");
        }
        if (hasItems) {
            items.deleteByCartId(cart.getCartId());
            items.flush();
        }
        cart.setWarehouseId(warehouse.getWarehouseId());
        touch(cart);
        return response(cart);
    }
    public CartResponse add(UUID userId, CartItemRequest request) {
        var cart = current(userId, false);
        requireWarehouse(cart);
        var variant = variants.findById(request.getVariantId()).orElseThrow(() -> new CommerceException(404, "Không tìm thấy variant"));
        ensurePurchasable(variant);
        var item = items.findByCartIdAndVariantId(cart.getCartId(), variant.getVariantId()).orElse(null);
        if (item == null) {
            item = new CartItem(); item.setCartId(cart.getCartId()); item.setVariantId(variant.getVariantId()); item.setQuantity(0);
        }
        long quantity = (long) item.getQuantity() + request.getQuantity();
        if (quantity > Integer.MAX_VALUE) throw new CommerceException(400, "Số lượng vượt giới hạn");
        validateAvailable(cart.getWarehouseId(), variant.getVariantId(), (int) quantity);
        item.setQuantity((int) quantity); item.setUnitPrice(variant.getPrice()); items.saveAndFlush(item); touch(cart);
        return response(cart);
    }
    public CartResponse quantity(UUID userId, UUID itemId, UpdateCartQuantityRequest request) {
        var cart = current(userId, false); var item = ownedItem(cart, itemId);
        requireWarehouse(cart);
        validateAvailable(cart.getWarehouseId(), item.getVariantId(), request.getQuantity());
        item.setQuantity(request.getQuantity()); items.saveAndFlush(item); touch(cart); return response(cart);
    }
    public CartResponse remove(UUID userId, UUID itemId) {
        var cart = current(userId, false); var item = ownedItem(cart, itemId);
        items.delete(item); items.flush(); touch(cart); return response(cart);
    }
    private CartItem ownedItem(Cart cart, UUID id) {
        var item = items.findById(id).orElseThrow(() -> new CommerceException(404, "Không tìm thấy cart item"));
        if (cart == null || !cart.getCartId().equals(item.getCartId())) throw new CommerceException(404, "Không tìm thấy cart item");
        return item;
    }
    private void touch(Cart cart) { cart.setUpdatedAt(OffsetDateTime.now()); carts.save(cart); }
    private void requireWarehouse(Cart cart) {
        if (cart == null || cart.getWarehouseId() == null) {
            throw new CommerceException(409, "Vui lòng chọn chi nhánh trước khi thêm sản phẩm vào giỏ");
        }
    }
    private void validateAvailable(UUID warehouseId, UUID variantId, int requested) {
        var row = inventory.lockByWarehouseAndVariant(warehouseId, variantId)
                .orElseThrow(() -> new CommerceException(409, "Sản phẩm hết hàng tại chi nhánh đã chọn"));
        int available = row.getQuantity() - row.getReservedQuantity();
        if (available < requested) {
            throw new CommerceException(409, "Chi nhánh chỉ còn " + available + " sản phẩm cho variant " + variantId);
        }
    }
    public static void ensurePurchasable(ProductVariant variant) {
        if (variant.getStatus() != ProductVariantStatus.ACTIVE || variant.getProduct().getStatus() != ProductStatus.ACTIVE)
            throw new CommerceException(409, "Sản phẩm/variant đã ngừng bán: " + variant.getSku());
    }
    private CartResponse response(Cart cart) {
        var response = new CartResponse(); response.setCartId(cart == null ? null : cart.getCartId());
        response.setWarehouseId(cart == null ? null : cart.getWarehouseId());
        if (cart != null && cart.getWarehouseId() != null) {
            warehouses.findById(cart.getWarehouseId()).ifPresent(w -> response.setWarehouseName(w.getWarehouseName()));
        }
        var links = cart == null ? List.<CartItem>of() : items.findByCartIdOrderByVariantIdAsc(cart.getCartId());
        var products = variants.findByVariantIdIn(links.stream().map(CartItem::getVariantId).toList()).stream().collect(Collectors.toMap(ProductVariant::getVariantId, v -> v));
        Map<UUID, Long> availability = cart == null || cart.getWarehouseId() == null || links.isEmpty() ? Map.of()
                : inventory.findVariantAvailability(cart.getWarehouseId(), links.stream().map(CartItem::getVariantId).toList())
                .stream().collect(Collectors.toMap(InventoryRepository.VariantAvailability::getVariantId,
                        InventoryRepository.VariantAvailability::getAvailableQuantity));
        List<CartItemResponse> result = new ArrayList<>(); BigDecimal subtotal = BigDecimal.ZERO;
        for (var link : links) {
            var variant = products.get(link.getVariantId()); var item = new CartItemResponse();
            item.setCartItemId(link.getCartItemId()); item.setVariantId(link.getVariantId()); item.setQuantity(link.getQuantity());
            item.setSku(variant.getSku()); item.setProductName(variant.getProduct().getProductName());
            item.setUnitPrice(variant.getPrice()); item.setLineTotal(variant.getPrice().multiply(BigDecimal.valueOf(link.getQuantity())));
            if (cart.getWarehouseId() != null) item.setAvailableQuantity(
                    Math.toIntExact(availability.getOrDefault(link.getVariantId(), 0L)));
            subtotal = subtotal.add(item.getLineTotal()); result.add(item);
        }
        response.setItems(result); response.setSubtotal(subtotal); return response;
    }
}
