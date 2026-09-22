package com.example.backend.goodsreceipt.repository;

import com.example.backend.goodsreceipt.entity.GoodsReceiptItemDevice;
import com.example.backend.goodsreceipt.entity.GoodsReceiptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface GoodsReceiptItemDeviceRepository extends JpaRepository<GoodsReceiptItemDevice, UUID> {
    @Query("select (count(d)>0) from GoodsReceiptItemDevice d where d.serialNumber=:value and d.receiptItem.receipt.status<>:cancelled")
    boolean existsInActiveReceipt(@Param("value") String value, @Param("cancelled") GoodsReceiptStatus cancelled);

    @Query("select (count(d)>0) from GoodsReceiptItemDevice d where d.serialNumber=:value and d.receiptItem.receipt.status<>:cancelled and d.receiptItem.receipt.receiptId<>:receiptId")
    boolean existsInOtherActiveReceipt(@Param("value") String value, @Param("cancelled") GoodsReceiptStatus cancelled,
                                       @Param("receiptId") UUID excludedReceiptId);
}
