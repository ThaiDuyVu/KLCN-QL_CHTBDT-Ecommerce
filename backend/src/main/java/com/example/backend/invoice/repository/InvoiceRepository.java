package com.example.backend.invoice.repository;

import com.example.backend.invoice.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Optional<Invoice> findByOrderId(UUID orderId);
    List<Invoice> findByOrderIdIn(Collection<UUID> orderIds);
    @Query("select max(i.invoiceNumber) from Invoice i where i.invoiceNumber like :prefix")
    Optional<String> maxNumberLike(@Param("prefix") String prefix);
    Page<Invoice> findByInvoiceNumberContainingIgnoreCase(String keyword, Pageable pageable);
    @Query("select i from Invoice i where i.orderId in (select o.orderId from Order o where o.warehouseId = :warehouseId)"
            + " and lower(i.invoiceNumber) like lower(concat('%', :keyword, '%'))")
    Page<Invoice> findByBranch(@Param("warehouseId") UUID warehouseId, @Param("keyword") String keyword, Pageable pageable);
}
