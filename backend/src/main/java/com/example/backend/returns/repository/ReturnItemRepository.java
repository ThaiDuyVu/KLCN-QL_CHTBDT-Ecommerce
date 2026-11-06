package com.example.backend.returns.repository;

import com.example.backend.returns.entity.ReturnItem;
import com.example.backend.returns.entity.ReturnStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReturnItemRepository extends JpaRepository<ReturnItem, UUID> {
    List<ReturnItem> findByReturnIdIn(Collection<UUID> returnIds);

    /** [orderItemId, quantity] already claimed by returns of the order in the given statuses. */
    @Query("select ri.orderItemId, sum(ri.quantity) from ReturnItem ri, ReturnRequest r"
            + " where r.returnId = ri.returnId and r.orderId = :orderId and r.status in :statuses group by ri.orderItemId")
    List<Object[]> quantitiesByOrderItem(@Param("orderId") UUID orderId, @Param("statuses") Collection<ReturnStatus> statuses);
}
