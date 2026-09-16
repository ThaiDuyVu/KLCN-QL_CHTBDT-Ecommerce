package com.example.backend.inventory.repository;

import com.example.backend.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByWarehouse_WarehouseIdAndVariant_VariantId(
            UUID warehouseId,
            UUID variantId
    );

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
