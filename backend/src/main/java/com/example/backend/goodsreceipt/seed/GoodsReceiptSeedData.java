package com.example.backend.goodsreceipt.seed;

import com.example.backend.auth.entity.Employee;
import com.example.backend.auth.repository.EmployeeRepository;
import com.example.backend.common.seed.DevSeedData;
import com.example.backend.goodsreceipt.dto.request.CreateGoodsReceiptItemRequest;
import com.example.backend.goodsreceipt.dto.request.CreateGoodsReceiptRequest;
import com.example.backend.goodsreceipt.dto.request.ReceiptDeviceRequest;
import com.example.backend.goodsreceipt.dto.response.GoodsReceiptResponse;
import com.example.backend.goodsreceipt.entity.GoodsReceipt;
import com.example.backend.goodsreceipt.entity.GoodsReceiptStatus;
import com.example.backend.goodsreceipt.repository.GoodsReceiptRepository;
import com.example.backend.goodsreceipt.service.GoodsReceiptService;
import com.example.backend.inventory.serial.repository.ImeiRepository;
import com.example.backend.inventory.serial.repository.SerialNumberRepository;
import com.example.backend.product.entity.ProductTrackingType;
import com.example.backend.product.entity.ProductVariant;
import com.example.backend.product.repository.ProductVariantRepository;
import com.example.backend.supplier.entity.Supplier;
import com.example.backend.supplier.repository.SupplierRepository;
import com.example.backend.warehouse.entity.Warehouse;
import com.example.backend.warehouse.repository.WarehouseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@Order(55)
@Profile("dev")
@ConditionalOnProperty(name = "app.seed.goods-receipt.enabled", havingValue = "true")
public class GoodsReceiptSeedData implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(GoodsReceiptSeedData.class);

    private static final String IP15_BLACK_SERIAL_1 = "DEV-IP15-BLK-128-SN-001";
    private static final String IP15_BLACK_SERIAL_2 = "DEV-IP15-BLK-128-SN-002";
    private static final String IP15_BLUE_SERIAL_1 = "DEV-IP15-BLU-256-SN-001";
    private static final String IP15_BLUE_SERIAL_2 = "DEV-IP15-BLU-256-SN-002";
    private static final String S24_GRAY_SERIAL_1 = "DEV-S24-GRY-256-SN-001";
    private static final String S24_GRAY_SERIAL_2 = "DEV-S24-GRY-256-SN-002";
    private static final String S24_BLACK_SERIAL_1 = "DEV-S24-BLK-256-SN-001";
    private static final String S24_BLACK_SERIAL_2 = "DEV-S24-BLK-256-SN-002";
    private static final String RN13_BLACK_SERIAL_1 = "DEV-RN13-BLK-128-SN-001";
    private static final String RN13_BLACK_SERIAL_2 = "DEV-RN13-BLK-128-SN-002";
    private static final String MBA_SERIAL_1 = "DEV-MBA-M2-256-SN-001";
    private static final String MBA_SERIAL_2 = "DEV-MBA-M2-256-SN-002";
    private static final String DELL_SERIAL_1 = "DEV-DELL-I15-512-SN-001";
    private static final String DELL_SERIAL_2 = "DEV-DELL-I15-512-SN-002";
    private static final String ASUS_SERIAL_1 = "DEV-ASUS-V15-512-SN-001";
    private static final String ASUS_SERIAL_2 = "DEV-ASUS-V15-512-SN-002";

    private static final String IMEI_1 = "356789010000014";
    private static final String IMEI_2 = "356789010000022";
    private static final String IMEI_3 = "356789010000030";
    private static final String IMEI_4 = "356789010000048";
    private static final String IMEI_5 = "356789010000055";
    private static final String IMEI_6 = "356789010000063";
    private static final String IMEI_7 = "356789010000071";
    private static final String IMEI_8 = "356789010000089";
    private static final String IMEI_9 = "356789010000097";
    private static final String IMEI_10 = "356789010000105";

    private static final List<String> SEEDED_SERIALS = List.of(
            IP15_BLACK_SERIAL_1, IP15_BLACK_SERIAL_2,
            IP15_BLUE_SERIAL_1, IP15_BLUE_SERIAL_2,
            S24_GRAY_SERIAL_1, S24_GRAY_SERIAL_2,
            S24_BLACK_SERIAL_1, S24_BLACK_SERIAL_2,
            RN13_BLACK_SERIAL_1, RN13_BLACK_SERIAL_2,
            MBA_SERIAL_1, MBA_SERIAL_2,
            DELL_SERIAL_1, DELL_SERIAL_2,
            ASUS_SERIAL_1, ASUS_SERIAL_2
    );
    private static final List<String> SEEDED_IMEIS = List.of(
            IMEI_1, IMEI_2, IMEI_3, IMEI_4, IMEI_5,
            IMEI_6, IMEI_7, IMEI_8, IMEI_9, IMEI_10
    );

    private final GoodsReceiptService goodsReceiptService;
    private final GoodsReceiptRepository goodsReceipts;
    private final ProductVariantRepository variants;
    private final WarehouseRepository warehouses;
    private final SupplierRepository suppliers;
    private final EmployeeRepository employees;
    private final SerialNumberRepository serialNumbers;
    private final ImeiRepository imeis;

    public GoodsReceiptSeedData(
            GoodsReceiptService goodsReceiptService,
            GoodsReceiptRepository goodsReceipts,
            ProductVariantRepository variants,
            WarehouseRepository warehouses,
            SupplierRepository suppliers,
            EmployeeRepository employees,
            SerialNumberRepository serialNumbers,
            ImeiRepository imeis
    ) {
        this.goodsReceiptService = goodsReceiptService;
        this.goodsReceipts = goodsReceipts;
        this.variants = variants;
        this.warehouses = warehouses;
        this.suppliers = suppliers;
        this.employees = employees;
        this.serialNumbers = serialNumbers;
        this.imeis = imeis;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Optional<GoodsReceipt> existingReceipt = goodsReceipts.findByReceiptCode(DevSeedData.GOODS_RECEIPT_CODE);
        if (existingReceipt.isPresent()) {
            confirmExistingDraft(existingReceipt.get());
            return;
        }

        ProductVariant iphoneBlack = findTrackedVariant(
                DevSeedData.IPHONE_15_BLACK_128, ProductTrackingType.IMEI);
        ProductVariant iphoneBlue = findTrackedVariant(
                DevSeedData.IPHONE_15_BLUE_256, ProductTrackingType.IMEI);
        ProductVariant galaxyGray = findTrackedVariant(
                DevSeedData.GALAXY_S24_GRAY_256, ProductTrackingType.IMEI);
        ProductVariant galaxyBlack = findTrackedVariant(
                DevSeedData.GALAXY_S24_BLACK_256, ProductTrackingType.IMEI);
        ProductVariant redmiBlack = findTrackedVariant(
                DevSeedData.REDMI_NOTE_13_BLACK_128, ProductTrackingType.IMEI);
        ProductVariant macbook = findTrackedVariant(
                DevSeedData.MACBOOK_AIR_M2_256, ProductTrackingType.SERIAL);
        ProductVariant dell = findTrackedVariant(
                DevSeedData.DELL_INSPIRON_15_512, ProductTrackingType.SERIAL);
        ProductVariant asus = findTrackedVariant(
                DevSeedData.ASUS_VIVOBOOK_15_512, ProductTrackingType.SERIAL);
        if (java.util.stream.Stream.of(
                iphoneBlack, iphoneBlue, galaxyGray, galaxyBlack, redmiBlack, macbook, dell, asus
        ).anyMatch(java.util.Objects::isNull)) {
            return;
        }

        Optional<Employee> employee = employees.findByEmployeeCode(DevSeedData.EMPLOYEE_CODE);
        Optional<Warehouse> warehouse = warehouses.findFirstByWarehouseNameOrderByWarehouseIdAsc(
                DevSeedData.WAREHOUSE_NAME);
        Optional<Supplier> supplier = suppliers.findBySupplierCode(DevSeedData.SUPPLIER_CODE);
        if (employee.isEmpty() || warehouse.isEmpty() || supplier.isEmpty()) {
            log.warn("Goods Receipt seed skipped: enable auth, warehouse and supplier dev seeds first");
            return;
        }
        if (seededDeviceAlreadyExists()) {
            log.warn("Goods Receipt seed skipped: deterministic serial/IMEI exists without receipt {}",
                    DevSeedData.GOODS_RECEIPT_CODE);
            return;
        }

        CreateGoodsReceiptRequest request = new CreateGoodsReceiptRequest();
        request.setReceiptCode(DevSeedData.GOODS_RECEIPT_CODE);
        request.setWarehouseId(warehouse.get().getWarehouseId());
        request.setSupplierId(supplier.get().getSupplierId());
        request.setEmployeeId(employee.get().getEmployeeId());
        request.setItems(List.of(
                imeiItem(iphoneBlack, IP15_BLACK_SERIAL_1, IMEI_1, IP15_BLACK_SERIAL_2, IMEI_2),
                imeiItem(iphoneBlue, IP15_BLUE_SERIAL_1, IMEI_3, IP15_BLUE_SERIAL_2, IMEI_4),
                imeiItem(galaxyGray, S24_GRAY_SERIAL_1, IMEI_5, S24_GRAY_SERIAL_2, IMEI_6),
                imeiItem(galaxyBlack, S24_BLACK_SERIAL_1, IMEI_7, S24_BLACK_SERIAL_2, IMEI_8),
                imeiItem(redmiBlack, RN13_BLACK_SERIAL_1, IMEI_9, RN13_BLACK_SERIAL_2, IMEI_10),
                serialItem(macbook, MBA_SERIAL_1, MBA_SERIAL_2),
                serialItem(dell, DELL_SERIAL_1, DELL_SERIAL_2),
                serialItem(asus, ASUS_SERIAL_1, ASUS_SERIAL_2)
        ));

        GoodsReceiptResponse draft = goodsReceiptService.createGoodsReceipt(request);
        GoodsReceiptResponse confirmed = goodsReceiptService.updateGoodsReceiptStatus(
                draft.getReceiptId(), GoodsReceiptStatus.CONFIRMED);
        log.info("Development Goods Receipt seed confirmed: {} with {} variants and {} devices",
                confirmed.getReceiptCode(), confirmed.getItems().size(), SEEDED_SERIALS.size());
    }

    private void confirmExistingDraft(GoodsReceipt receipt) {
        if (receipt.getStatus() == GoodsReceiptStatus.CONFIRMED) {
            log.info("Development Goods Receipt seed already exists: {}", DevSeedData.GOODS_RECEIPT_CODE);
            return;
        }
        if (receipt.getStatus() != GoodsReceiptStatus.DRAFT) {
            log.warn("Goods Receipt seed skipped: receipt {} is {}",
                    DevSeedData.GOODS_RECEIPT_CODE, receipt.getStatus());
            return;
        }
        goodsReceiptService.updateGoodsReceiptStatus(receipt.getReceiptId(), GoodsReceiptStatus.CONFIRMED);
        log.info("Development Goods Receipt seed resumed and confirmed: {}", DevSeedData.GOODS_RECEIPT_CODE);
    }

    private ProductVariant findTrackedVariant(String sku, ProductTrackingType expectedType) {
        Optional<ProductVariant> variant = variants.findBySku(sku);
        if (variant.isEmpty()) {
            log.warn("Goods Receipt seed skipped: required SKU {} is missing", sku);
            return null;
        }
        if (variant.get().getTrackingType() != expectedType) {
            log.warn("Goods Receipt seed skipped: SKU {} must use {}, currently {}",
                    sku, expectedType, variant.get().getTrackingType());
            return null;
        }
        return variant.get();
    }

    private boolean seededDeviceAlreadyExists() {
        return SEEDED_SERIALS.stream().anyMatch(serialNumbers::existsBySerialNumber)
                || SEEDED_IMEIS.stream().anyMatch(imeis::existsByImeiNumber);
    }

    private CreateGoodsReceiptItemRequest imeiItem(
            ProductVariant variant,
            String serial1,
            String imei1,
            String serial2,
            String imei2
    ) {
        return receiptItem(variant, List.of(
                device(serial1, List.of(imei1)),
                device(serial2, List.of(imei2))
        ));
    }

    private CreateGoodsReceiptItemRequest serialItem(
            ProductVariant variant,
            String serial1,
            String serial2
    ) {
        return receiptItem(variant, List.of(
                device(serial1, List.of()),
                device(serial2, List.of())
        ));
    }

    private CreateGoodsReceiptItemRequest receiptItem(
            ProductVariant variant,
            List<ReceiptDeviceRequest> devices
    ) {
        CreateGoodsReceiptItemRequest item = new CreateGoodsReceiptItemRequest();
        item.setVariantId(variant.getVariantId());
        item.setQuantity(devices.size());
        item.setUnitCost(variant.getCostPrice());
        item.setDevices(devices);
        return item;
    }

    private ReceiptDeviceRequest device(String serialNumber, List<String> imeiNumbers) {
        ReceiptDeviceRequest device = new ReceiptDeviceRequest();
        device.setSerialNumber(serialNumber);
        device.setImeiNumbers(imeiNumbers);
        return device;
    }
}
