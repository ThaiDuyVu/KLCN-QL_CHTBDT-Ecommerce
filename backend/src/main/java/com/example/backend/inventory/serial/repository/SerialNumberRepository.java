package com.example.backend.inventory.serial.repository;

import com.example.backend.inventory.serial.entity.SerialNumber;
import com.example.backend.inventory.serial.entity.SerialStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface SerialNumberRepository extends JpaRepository<SerialNumber, UUID>, JpaSpecificationExecutor<SerialNumber> {
    @Override
    @EntityGraph(attributePaths = {"warehouse", "variant", "variant.product"})
    Page<SerialNumber> findAll(Specification<SerialNumber> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"warehouse", "variant", "variant.product"})
    Optional<SerialNumber> findById(UUID id);
    boolean existsByVariant_VariantId(UUID variantId);
    boolean existsBySerialNumber(String serialNumber);
    Optional<SerialNumber> findBySerialNumber(String serialNumber);
    Optional<SerialNumber> findFirstByImeis_ImeiNumber(String imeiNumber);
    long countByVariant_VariantIdAndWarehouse_WarehouseIdAndStatus(UUID variantId, UUID warehouseId, SerialStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SerialNumber s where s.serialId in :ids order by s.serialId")
    List<SerialNumber> findAllByIdForUpdate(@Param("ids") Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SerialNumber s where s.variant.variantId=:variantId and s.warehouse.warehouseId=:warehouseId and s.status=:status order by s.serialId")
    List<SerialNumber> findAvailableForUpdate(@Param("variantId") UUID variantId,
                                               @Param("warehouseId") UUID warehouseId,
                                               @Param("status") SerialStatus status,
                                               Pageable pageable);

    @Query("select distinct s from SerialNumber s left join fetch s.imeis where s.serialId in :ids")
    List<SerialNumber> findAllWithImeisByIdIn(@Param("ids") Collection<UUID> ids);

    @Query("select distinct s from SerialNumber s join fetch s.variant v join fetch v.product "
            + "join fetch s.warehouse left join fetch s.imeis where s.serialId in :ids")
    List<SerialNumber> findAllWithDetailsByIdIn(@Param("ids") Collection<UUID> ids);
}
