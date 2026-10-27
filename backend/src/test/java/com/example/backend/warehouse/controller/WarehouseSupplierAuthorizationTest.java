package com.example.backend.warehouse.controller;

import com.example.backend.auth.service.CustomUserDetailsService;
import com.example.backend.auth.service.JwtService;
import com.example.backend.common.security.AuthCookieProperties;
import com.example.backend.common.security.CorsProperties;
import com.example.backend.common.security.SecurityConfig;
import com.example.backend.supplier.controller.SupplierController;
import com.example.backend.supplier.dto.request.CreateSupplierRequest;
import com.example.backend.supplier.dto.response.SupplierPageResponse;
import com.example.backend.supplier.dto.response.SupplierResponse;
import com.example.backend.supplier.entity.SupplierStatus;
import com.example.backend.supplier.service.SupplierService;
import com.example.backend.warehouse.dto.request.CreateWarehouseRequest;
import com.example.backend.warehouse.dto.response.WarehousePageResponse;
import com.example.backend.warehouse.dto.response.WarehouseResponse;
import com.example.backend.warehouse.entity.WarehouseStatus;
import com.example.backend.warehouse.service.WarehouseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({WarehouseController.class, SupplierController.class})
@Import({SecurityConfig.class, WarehouseSupplierAuthorizationTest.SecurityTestConfiguration.class})
class WarehouseSupplierAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WarehouseService warehouseService;

    @MockitoBean
    private SupplierService supplierService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void customer_canListWarehousesToChooseBranch() throws Exception {
        when(warehouseService.getWarehouses(0, 20)).thenReturn(
                new WarehousePageResponse(List.of(warehouseResponse()), 0, 20, 1, 1)
        );

        mockMvc.perform(get("/api/warehouses").with(authenticatedAs("CUSTOMER")))
                .andExpect(status().isOk());
    }

    @Test
    void customer_cannotCreateOrUpdateWarehouse() throws Exception {
        mockMvc.perform(post("/api/warehouses")
                        .with(authenticatedAs("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(warehouseBody()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/warehouses/{warehouseId}", UUID.randomUUID())
                        .with(authenticatedAs("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(warehouseBody()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(warehouseService);
    }

    @Test
    void customer_cannotReadOrWriteSuppliers() throws Exception {
        mockMvc.perform(get("/api/suppliers").with(authenticatedAs("CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/suppliers/{supplierId}", UUID.randomUUID()).with(authenticatedAs("CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/suppliers")
                        .with(authenticatedAs("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplierBody()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(supplierService);
    }

    @Test
    void staff_canReadSuppliersButCannotManageSuppliersOrWarehouses() throws Exception {
        when(supplierService.getSuppliers(0, 20)).thenReturn(
                new SupplierPageResponse(List.of(supplierResponse()), 0, 20, 1, 1)
        );

        mockMvc.perform(get("/api/suppliers").with(authenticatedAs("STAFF")))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/suppliers/{supplierId}", UUID.randomUUID())
                        .with(authenticatedAs("STAFF"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplierBody()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/warehouses")
                        .with(authenticatedAs("STAFF"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(warehouseBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void warehouseKeeper_readsSuppliersButOtherJobTitlesDoNot() throws Exception {
        when(supplierService.getSuppliers(0, 20)).thenReturn(
                new SupplierPageResponse(List.of(supplierResponse()), 0, 20, 1, 1)
        );

        mockMvc.perform(get("/api/suppliers").with(authenticatedAs("WAREHOUSE_KEEPER")))
                .andExpect(status().isOk());
        for (String role : List.of("SALES_STAFF", "WARRANTY_TECHNICIAN", "DELIVERY_STAFF")) {
            mockMvc.perform(get("/api/suppliers").with(authenticatedAs(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/suppliers")
                        .with(authenticatedAs("WAREHOUSE_KEEPER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplierBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void manager_canCreateSupplierAndWarehouse() throws Exception {
        when(supplierService.createSupplier(any(CreateSupplierRequest.class))).thenReturn(supplierResponse());
        when(warehouseService.createWarehouse(any(CreateWarehouseRequest.class))).thenReturn(warehouseResponse());

        mockMvc.perform(post("/api/suppliers")
                        .with(authenticatedAs("MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplierBody()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/warehouses")
                        .with(authenticatedAs("MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(warehouseBody()))
                .andExpect(status().isCreated());
    }

    @Test
    void admin_canUpdateWarehouse() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseService.updateWarehouse(eq(warehouseId), any())).thenReturn(warehouseResponse());

        mockMvc.perform(put("/api/warehouses/{warehouseId}", warehouseId)
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(warehouseBody()))
                .andExpect(status().isOk());
    }

    private static RequestPostProcessor authenticatedAs(String authority) {
        return user("test-user")
                .authorities(new SimpleGrantedAuthority(authority));
    }

    private WarehouseResponse warehouseResponse() {
        return new WarehouseResponse(
                UUID.randomUUID(),
                "Kho trung tâm",
                "Quận 1, TP. Hồ Chí Minh",
                WarehouseStatus.ACTIVE
        );
    }

    private SupplierResponse supplierResponse() {
        return new SupplierResponse(
                UUID.randomUUID(),
                "SUP-001",
                "Nhà cung cấp A",
                "0900000000",
                "supplier@example.com",
                "Quận 1, TP. Hồ Chí Minh",
                SupplierStatus.ACTIVE
        );
    }

    private String warehouseBody() {
        return "{"
                + "\"warehouseName\":\"Kho trung tâm\","
                + "\"address\":\"Quận 1, TP. Hồ Chí Minh\""
                + "}";
    }

    private String supplierBody() {
        return "{"
                + "\"supplierCode\":\"SUP-001\","
                + "\"supplierName\":\"Nhà cung cấp A\","
                + "\"phone\":\"0900000000\","
                + "\"email\":\"supplier@example.com\","
                + "\"address\":\"Quận 1, TP. Hồ Chí Minh\""
                + "}";
    }

    @TestConfiguration
    static class SecurityTestConfiguration {

        @Bean
        AuthCookieProperties authCookieProperties() {
            AuthCookieProperties properties = new AuthCookieProperties();
            properties.setAccessTokenCookieName("access_token");
            properties.setRefreshTokenCookieName("refresh_token");
            properties.setCsrfCookieName("XSRF-TOKEN");
            properties.setCsrfHeaderName("X-XSRF-TOKEN");
            properties.setSameSite("Lax");
            properties.setAccessTokenPath("/");
            properties.setRefreshTokenPath("/");
            properties.setAccessTokenMaxAge(Duration.ofMinutes(15));
            properties.setRefreshTokenMaxAge(Duration.ofDays(7));
            return properties;
        }

        @Bean
        CorsProperties corsProperties() {
            CorsProperties properties = new CorsProperties();
            properties.setAllowedOrigins(List.of("http://localhost"));
            return properties;
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }
}
