package com.example.backend.goodsreceipt.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

public class ReceiptDeviceRequest {
    @NotBlank(message = "Serial number là bắt buộc")
    @Size(max = 255, message = "Serial number không được vượt quá 255 ký tự")
    private String serialNumber;
    private List<@NotBlank(message = "IMEI không được để trống") @Size(max = 50, message = "IMEI không được vượt quá 50 ký tự") String> imeiNumbers = new ArrayList<>();
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public List<String> getImeiNumbers() { return imeiNumbers; }
    public void setImeiNumbers(List<String> imeiNumbers) { this.imeiNumbers = imeiNumbers; }
}
