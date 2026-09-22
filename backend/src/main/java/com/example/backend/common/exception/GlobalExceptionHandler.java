package com.example.backend.common.exception;

import com.example.backend.auth.exception.*;
import com.example.backend.brand.exception.BrandInUseException;
import com.example.backend.brand.exception.BrandNameAlreadyExistsException;
import com.example.backend.brand.exception.BrandNotFoundException;
import com.example.backend.brand.exception.InvalidBrandPaginationException;
import com.example.backend.category.exception.CategoryInUseException;
import com.example.backend.category.exception.CategoryNotFoundException;
import com.example.backend.category.exception.InvalidCategoryParentException;
import com.example.backend.goodsreceipt.exception.GoodsReceiptNotFoundException;
import com.example.backend.goodsreceipt.exception.GoodsReceiptReferenceNotFoundException;
import com.example.backend.goodsreceipt.exception.GoodsReceiptTotalAmountExceededException;
import com.example.backend.goodsreceipt.exception.InvalidGoodsReceiptStatusTransitionException;
import com.example.backend.goodsreceipt.exception.ReceiptCodeAlreadyExistsException;
import com.example.backend.product.exception.ProductInUseException;
import com.example.backend.product.exception.ProductNotFoundException;
import com.example.backend.product.exception.ProductReferenceNotFoundException;
import com.example.backend.product.exception.InvalidProductPaginationException;
import com.example.backend.product.exception.InvalidProductVariantPaginationException;
import com.example.backend.product.exception.ProductVariantInUseException;
import com.example.backend.product.exception.ProductVariantNotFoundException;
import com.example.backend.product.exception.ProductVariantSkuAlreadyExistsException;
import com.example.backend.product.exception.ProductImageNotFoundException;
import com.example.backend.product.exception.ProductImageConflictException;
import com.example.backend.product.exception.SpecificationNotFoundException;
import com.example.backend.supplier.exception.SupplierCodeAlreadyExistsException;
import com.example.backend.supplier.exception.SupplierNotFoundException;
import com.example.backend.warehouse.exception.WarehouseNotFoundException;
import com.example.backend.inventory.exception.InventoryNotFoundException;
import com.example.backend.inventory.exception.InventoryConflictException;
import com.example.backend.inventory.serial.exception.SerialNotFoundException;
import com.example.backend.inventory.serial.exception.SerialConflictException;
import com.example.backend.goodsreceipt.exception.InvalidReceiptDeviceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(com.example.backend.warranty.exception.WarrantyException.class)
    public ResponseEntity<ApiErrorResponse> handleWarranty(
            com.example.backend.warranty.exception.WarrantyException exception) {
        return ResponseEntity.status(exception.getStatus()).body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler({InventoryNotFoundException.class, SerialNotFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleStockNotFound(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler({InventoryConflictException.class, SerialConflictException.class})
    public ResponseEntity<ApiErrorResponse> handleStockConflict(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(InvalidReceiptDeviceException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidReceiptDevice(InvalidReceiptDeviceException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorResponse(exception.getMessage()));
    }
    @ExceptionHandler(com.example.backend.order.exception.CommerceException.class)
    public ResponseEntity<ApiErrorResponse> handleCommerce(com.example.backend.order.exception.CommerceException exception) {
        return ResponseEntity.status(exception.getStatus()).body(new ApiErrorResponse(exception.getMessage()));
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDenied(org.springframework.security.access.AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Bạn không có quyền thực hiện thao tác này");
    }

    @ExceptionHandler(InvalidUserManagementRequestException.class)
    public ResponseEntity<String> handleInvalidUserManagement(InvalidUserManagementRequestException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }


    @ExceptionHandler(SpecificationNotFoundException.class)
    public ResponseEntity<String> handleSpecificationNotFound(SpecificationNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }

    @ExceptionHandler(ProductImageNotFoundException.class)
    public ResponseEntity<String> handleImageNotFound(ProductImageNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }

    @ExceptionHandler(ProductImageConflictException.class)
    public ResponseEntity<String> handleImageConflict(ProductImageConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
    }

    @ExceptionHandler(InvalidProductVariantPaginationException.class)
    public ResponseEntity<String> handleInvalidVariantPagination(InvalidProductVariantPaginationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    @ExceptionHandler(ProductVariantNotFoundException.class)
    public ResponseEntity<String> handleVariantNotFound(ProductVariantNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }

    @ExceptionHandler({ProductVariantSkuAlreadyExistsException.class, ProductVariantInUseException.class})
    public ResponseEntity<String> handleVariantConflict(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
    }

    @ExceptionHandler(InvalidBrandPaginationException.class)
    public ResponseEntity<String> handleInvalidBrandPagination(InvalidBrandPaginationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    @ExceptionHandler(BrandNotFoundException.class)
    public ResponseEntity<String> handleBrandNotFound(BrandNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }

    @ExceptionHandler(BrandNameAlreadyExistsException.class)
    public ResponseEntity<String> handleBrandNameAlreadyExists(BrandNameAlreadyExistsException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
    }

    @ExceptionHandler(BrandInUseException.class)
    public ResponseEntity<String> handleBrandInUse(BrandInUseException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<String> handleCategoryNotFound(CategoryNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }

    @ExceptionHandler(InvalidCategoryParentException.class)
    public ResponseEntity<String> handleInvalidCategoryParent(InvalidCategoryParentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    @ExceptionHandler(CategoryInUseException.class)
    public ResponseEntity<String> handleCategoryInUse(CategoryInUseException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .distinct()
                .sorted()
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorResponse(message));
    }

    @ExceptionHandler(InvalidProductPaginationException.class)
    public ResponseEntity<String> handleInvalidProductPagination(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    @ExceptionHandler({ProductNotFoundException.class, ProductReferenceNotFoundException.class})
    public ResponseEntity<String> handleProductNotFound(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }

    @ExceptionHandler(ProductInUseException.class)
    public ResponseEntity<String> handleProductInUse(ProductInUseException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
    }

    @ExceptionHandler({
            GoodsReceiptNotFoundException.class,
            GoodsReceiptReferenceNotFoundException.class
    })
    public ResponseEntity<String> handleGoodsReceiptNotFound(
            RuntimeException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(exception.getMessage());
    }

    @ExceptionHandler(ReceiptCodeAlreadyExistsException.class)
    public ResponseEntity<String> handleReceiptCodeAlreadyExists(
            ReceiptCodeAlreadyExistsException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(exception.getMessage());
    }

    @ExceptionHandler(GoodsReceiptTotalAmountExceededException.class)
    public ResponseEntity<String> handleGoodsReceiptTotalAmountExceeded(
            GoodsReceiptTotalAmountExceededException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(exception.getMessage());
    }

    @ExceptionHandler(InvalidGoodsReceiptStatusTransitionException.class)
    public ResponseEntity<String> handleInvalidGoodsReceiptStatusTransition(
            InvalidGoodsReceiptStatusTransitionException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(exception.getMessage());
    }

    @ExceptionHandler(WarehouseNotFoundException.class)
    public ResponseEntity<String> handleWarehouseNotFound(
            WarehouseNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(exception.getMessage());
    }

    @ExceptionHandler(SupplierNotFoundException.class)
    public ResponseEntity<String> handleSupplierNotFound(
            SupplierNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(exception.getMessage());
    }

    @ExceptionHandler(SupplierCodeAlreadyExistsException.class)
    public ResponseEntity<String> handleSupplierCodeAlreadyExists(
            SupplierCodeAlreadyExistsException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(exception.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<String> handleUserNotFound(
            UserNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(exception.getMessage());
    }
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<String> handleEmailAlreadyExists(
            EmailAlreadyExistsException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(exception.getMessage());
    }

    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<String> handleRoleNotFound(
            RoleNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(exception.getMessage());
    }

    @ExceptionHandler(PermissionNotFoundException.class)
    public ResponseEntity<String> handlePermissionNotFound(
            PermissionNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(exception.getMessage());
    }

    @ExceptionHandler(ProtectedPermissionException.class)
    public ResponseEntity<String> handleProtectedPermission(
            ProtectedPermissionException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(exception.getMessage());
    }
    @ExceptionHandler(UserRoleNotFoundException.class)
    public ResponseEntity<String> handleUserRoleNotFound(
            UserRoleNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(exception.getMessage());
    }

    @ExceptionHandler(InvalidJwtTokenException.class)
    public ResponseEntity<String> handleInvalidJwtToken(
            InvalidJwtTokenException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<String> handleInvalidCredentials(
            InvalidCredentialsException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(exception.getMessage());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<String> handleInvalidRefreshToken(
            InvalidRefreshTokenException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(exception.getMessage());
    }

    @ExceptionHandler(RefreshTokenReuseDetectedException.class)
    public ResponseEntity<String> handleRefreshTokenReuseDetected(
            RefreshTokenReuseDetectedException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(exception.getMessage());
    }
}
