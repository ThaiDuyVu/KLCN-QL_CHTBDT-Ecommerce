package com.example.backend.common.seed;

import java.util.List;

public final class DevSeedData {

    public static final String WAREHOUSE_NAME = "Kho phát triển";
    public static final String SECONDARY_WAREHOUSE_NAME = "Chi nhánh dự phòng";
    public static final String SUPPLIER_CODE = "DEV-SUPPLIER-ELECTRONICS";
    public static final String EMPLOYEE_CODE = "DEV-MANAGER";
    public static final String GOODS_RECEIPT_CODE = "DEV-GR-SERIAL-IMEI-001";
    public static final String CATALOG_EXPANSION_RECEIPT_CODE = "DEV-GR-CATALOG-EXPANSION-002";

    public static final String IPHONE_15_BLACK_128 = "DEV-IP15-BLK-128";
    public static final String IPHONE_15_BLUE_256 = "DEV-IP15-BLU-256";
    public static final String GALAXY_S24_GRAY_256 = "DEV-S24-GRY-256";
    public static final String GALAXY_S24_BLACK_256 = "DEV-S24-BLK-256";
    public static final String REDMI_NOTE_13_BLACK_128 = "DEV-RN13-BLK-128";
    public static final String MACBOOK_AIR_M2_256 = "DEV-MBA-M2-256";
    public static final String DELL_INSPIRON_15_512 = "DEV-DELL-I15-512";
    public static final String ASUS_VIVOBOOK_15_512 = "DEV-ASUS-V15-512";

    public static final List<String> CATALOG_EXPANSION_VARIANT_SKUS = List.of(
            "DEV-IP16-BLK-128", "DEV-IP16-BLU-256", "DEV-IP16-PNK-512",
            "DEV-IP14-BLK-128", "DEV-IP14-BLU-256", "DEV-IP14-RED-256",
            "DEV-A55-NVY-128", "DEV-A55-LIL-256", "DEV-A55-ICE-256",
            "DEV-ZF6-GRY-256", "DEV-ZF6-YLW-256", "DEV-ZF6-BLU-512",
            "DEV-X14TP-BLK-256", "DEV-X14TP-BLU-512", "DEV-X14TP-GRY-512",
            "DEV-S23-BLK-128", "DEV-S23-GRN-256", "DEV-S23-CRM-256",
            "DEV-X15-BLK-256", "DEV-X15-WHT-512", "DEV-X15-GRN-512",
            "DEV-MBP14-M3-SG-512", "DEV-MBP14-M3-SL-512", "DEV-MBP14-M3-SG-1TB",
            "DEV-XPS13-SL-512", "DEV-XPS13-GR-1TB", "DEV-XPS13-SL-1TB",
            "DEV-ROGG14-GR-512", "DEV-ROGG14-WH-1TB", "DEV-ROGG14-GR-1TB"
    );

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
