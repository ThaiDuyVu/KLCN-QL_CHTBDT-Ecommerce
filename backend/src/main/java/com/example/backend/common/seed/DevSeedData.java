package com.example.backend.common.seed;

import java.util.List;

public final class DevSeedData {

    public static final String WAREHOUSE_NAME = "Kho phát triển";
    public static final String SECONDARY_WAREHOUSE_NAME = "Chi nhánh dự phòng";
    public static final String SUPPLIER_CODE = "DEV-SUPPLIER-ELECTRONICS";
    public static final String EMPLOYEE_CODE = "DEV-MANAGER";
    public static final String GOODS_RECEIPT_CODE = "DEV-GR-SERIAL-IMEI-001";

    public static final String IPHONE_15_BLACK_128 = "DEV-IP15-BLK-128";
    public static final String IPHONE_15_BLUE_256 = "DEV-IP15-BLU-256";
    public static final String GALAXY_S24_GRAY_256 = "DEV-S24-GRY-256";
    public static final String GALAXY_S24_BLACK_256 = "DEV-S24-BLK-256";
    public static final String REDMI_NOTE_13_BLACK_128 = "DEV-RN13-BLK-128";
    public static final String MACBOOK_AIR_M2_256 = "DEV-MBA-M2-256";
    public static final String DELL_INSPIRON_15_512 = "DEV-DELL-I15-512";
    public static final String ASUS_VIVOBOOK_15_512 = "DEV-ASUS-V15-512";

    public static final List<String> SEEDED_VARIANT_SKUS = List.of(
            IPHONE_15_BLACK_128,
            IPHONE_15_BLUE_256,
            GALAXY_S24_GRAY_256,
            GALAXY_S24_BLACK_256,
            REDMI_NOTE_13_BLACK_128,
            MACBOOK_AIR_M2_256,
            DELL_INSPIRON_15_512,
            ASUS_VIVOBOOK_15_512
    );

    private DevSeedData() {
    }
}
