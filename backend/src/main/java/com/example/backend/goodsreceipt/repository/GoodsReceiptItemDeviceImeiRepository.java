package com.example.backend.goodsreceipt.repository;

import com.example.backend.goodsreceipt.entity.GoodsReceiptItemDeviceImei;
import com.example.backend.goodsreceipt.entity.GoodsReceiptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface GoodsReceiptItemDeviceImeiRepository extends JpaRepository<GoodsReceiptItemDeviceImei, UUID> {
    @Query("select (count(i)>0) from GoodsReceiptItemDeviceImei i where i.imeiNumber=:value and i.device.receiptItem.receipt.status<>:cancelled")
    boolean existsInActiveReceipt(@Param("value") String value, @Param("cancelled") GoodsReceiptStatus cancelled);

    @Query("select (count(i)>0) from GoodsReceiptItemDeviceImei i where i.imeiNumber=:value and i.device.receiptItem.receipt.status<>:cancelled and i.device.receiptItem.receipt.receiptId<>:receiptId")
    boolean existsInOtherActiveReceipt(@Param("value") String value, @Param("cancelled") GoodsReceiptStatus cancelled,
                                       @Param("receiptId") UUID excludedReceiptId);
}
