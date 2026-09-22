package com.example.backend.inventory.repository;

import com.example.backend.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID>, JpaSpecificationExecutor<Inventory> {
    interface ProductAvailability {
        UUID getProductId();
        Long getAvailableQuantity();
    }

    interface VariantAvailability {
        UUID getVariantId();
        Long getAvailableQuantity();
    }
    @Override
    @EntityGraph(attributePaths = {"warehouse", "variant", "variant.product"})
    Page<Inventory> findAll(Specification<Inventory> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"warehouse", "variant", "variant.product"})
    Optional<Inventory> findById(UUID id);
    boolean existsByVariant_VariantIdAndQuantityGreaterThan(UUID variantId, Integer quantity);
    Optional<Inventory> findByWarehouse_WarehouseIdAndVariant_VariantId(
            UUID warehouseId,
            UUID variantId
    );

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.warehouse.warehouseId=:warehouseId and i.variant.variantId=:variantId")
    Optional<Inventory> lockByWarehouseAndVariant(@Param("warehouseId") UUID warehouseId,
                                                   @Param("variantId") UUID variantId);

    @Query("""
            select i.variant.product.productId as productId,
                   sum(i.quantity - i.reservedQuantity) as availableQuantity
            from Inventory i
            where i.warehouse.warehouseId = :warehouseId
              and i.variant.product.productId in :productIds
            group by i.variant.product.productId
            """)
    List<ProductAvailability> findProductAvailability(@Param("warehouseId") UUID warehouseId,
                                                       @Param("productIds") Collection<UUID> productIds);

    @Query("""
            select i.variant.variantId as variantId,
                   (i.quantity - i.reservedQuantity) as availableQuantity
            from Inventory i
            where i.warehouse.warehouseId = :warehouseId
              and i.variant.variantId in :variantIds
            """)
    List<VariantAvailability> findVariantAvailability(@Param("warehouseId") UUID warehouseId,
                                                       @Param("variantIds") Collection<UUID> variantIds);

    @Modifying
    @Query(value = """
            INSERT INTO inventory (
                warehouse_id,
                variant_id,
                quantity,
                reserved_quantity,
                updated_at
            )
            VALUES (:warehouseId, :variantId, :quantity, 0, CURRENT_TIMESTAMP)
            ON CONFLICT (warehouse_id, variant_id)
            DO UPDATE SET
                quantity = inventory.quantity + EXCLUDED.quantity,
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int incrementQuantity(
            @Param("warehouseId") UUID warehouseId,
            @Param("variantId") UUID variantId,
            @Param("quantity") int quantity
    );
}
