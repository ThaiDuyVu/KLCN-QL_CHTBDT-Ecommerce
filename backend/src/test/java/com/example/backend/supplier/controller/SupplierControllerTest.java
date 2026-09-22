package com.example.backend.supplier.controller;

import com.example.backend.auth.service.CustomUserDetailsService;
import com.example.backend.auth.service.JwtService;
import com.example.backend.common.security.AuthCookieProperties;
import com.example.backend.supplier.dto.request.CreateSupplierRequest;
import com.example.backend.supplier.dto.request.UpdateSupplierRequest;
import com.example.backend.supplier.dto.response.SupplierPageResponse;
import com.example.backend.supplier.dto.response.SupplierResponse;
import com.example.backend.supplier.entity.SupplierStatus;
import com.example.backend.supplier.exception.SupplierCodeAlreadyExistsException;
import com.example.backend.supplier.exception.SupplierNotFoundException;
import com.example.backend.supplier.service.SupplierService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SupplierController.class)
class SupplierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupplierService supplierService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private AuthCookieProperties authCookieProperties;

    @Test
    @WithMockUser
    void getSuppliers_shouldReturn200WhenAuthenticated() throws Exception {
        SupplierPageResponse response = new SupplierPageResponse(
                List.of(supplierResponse()), 0, 20, 1, 1
        );
        when(supplierService.getSuppliers(0, 20)).thenReturn(response);

        mockMvc.perform(get("/api/suppliers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].supplierCode").value("SUP-001"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser
    void getSupplierById_shouldReturn404WhenSupplierDoesNotExist() throws Exception {
        UUID supplierId = UUID.randomUUID();
        when(supplierService.getSupplierById(supplierId)).thenThrow(
                new SupplierNotFoundException("Không tìm thấy nhà cung cấp")
        );

        mockMvc.perform(get("/api/suppliers/{supplierId}", supplierId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void createSupplier_shouldReturn201WithCsrf() throws Exception {
        when(supplierService.createSupplier(any(CreateSupplierRequest.class)))
                .thenReturn(supplierResponse());

        mockMvc.perform(post("/api/suppliers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("SUP-001", "Nhà cung cấp A")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.supplierCode").value("SUP-001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser
    void createSupplier_shouldReturn409WhenSupplierCodeAlreadyExists() throws Exception {
        when(supplierService.createSupplier(any(CreateSupplierRequest.class)))
                .thenThrow(new SupplierCodeAlreadyExistsException("Mã nhà cung cấp đã tồn tại"));

        mockMvc.perform(post("/api/suppliers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("SUP-001", "Nhà cung cấp A")))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser
    void updateSupplier_shouldReturn200AndKeepPersistedStatus() throws Exception {
        UUID supplierId = UUID.randomUUID();
        SupplierResponse response = supplierResponse();
        when(supplierService.updateSupplier(eq(supplierId), any(UpdateSupplierRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/suppliers/{supplierId}", supplierId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("SUP-001", "Nhà cung cấp đã cập nhật")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser
    void updateSupplier_shouldReturn404WhenSupplierDoesNotExist() throws Exception {
        UUID supplierId = UUID.randomUUID();
        when(supplierService.updateSupplier(eq(supplierId), any(UpdateSupplierRequest.class)))
                .thenThrow(new SupplierNotFoundException("Không tìm thấy nhà cung cấp"));

        mockMvc.perform(put("/api/suppliers/{supplierId}", supplierId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("SUP-001", "Nhà cung cấp A")))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void createSupplier_shouldReturn400WhenRequiredSchemaFieldIsMissing() throws Exception {
        mockMvc.perform(post("/api/suppliers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierCode\":\"SUP-001\"}"))
                .andExpect(status().isBadRequest());
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

    private String requestBody(String supplierCode, String supplierName) {
        return "{"
                + "\"supplierCode\":\"" + supplierCode + "\","
                + "\"supplierName\":\"" + supplierName + "\","
                + "\"phone\":\"0900000000\","
                + "\"email\":\"supplier@example.com\","
                + "\"address\":\"Quận 1, TP. Hồ Chí Minh\""
                + "}";
    }
}
