package com.example.backend.order.repository;
import com.example.backend.order.entity.Payment;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByOrderId(UUID orderId);
    @Query("select p.orderId from Payment p where p.paymentId = :id")
    Optional<UUID> findOrderIdByPaymentId(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.orderId = :id order by p.paymentId")
    List<Payment> lockByOrderId(@Param("id") UUID id);
    @Query("select p.orderId from Payment p, Order o where o.orderId = p.orderId"
            + " and o.status = com.example.backend.order.entity.OrderStatus.PENDING"
            + " and p.paymentMethod = com.example.backend.order.entity.PaymentMethod.VNPAY"
            + " and p.status = com.example.backend.order.entity.PaymentStatus.PENDING"
            + " and p.transactionCode is null and p.paymentDate < :cutoff order by p.paymentDate")
    List<UUID> findUnpaidVnpayOrderIdsBefore(@Param("cutoff") java.time.OffsetDateTime cutoff, org.springframework.data.domain.Pageable page);
}
