package com.example.backend.warranty.repository;

import com.example.backend.warranty.entity.WarrantyTicket;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface WarrantyTicketRepository extends JpaRepository<WarrantyTicket, UUID>, JpaSpecificationExecutor<WarrantyTicket> {
    Page<WarrantyTicket> findByCustomerId(UUID customerId, Pageable pageable);
    Optional<WarrantyTicket> findByTicketIdAndCustomerId(UUID ticketId, UUID customerId);
    List<WarrantyTicket> findByWarrantyIdOrderByCreatedAtDescTicketIdDesc(UUID warrantyId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from WarrantyTicket t where t.ticketId = :ticketId")
    Optional<WarrantyTicket> lockById(@Param("ticketId") UUID ticketId);
}
