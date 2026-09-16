package com.example.backend.warehouse.controller;

import com.example.backend.auth.service.CustomUserDetailsService;
import com.example.backend.auth.service.JwtService;
import com.example.backend.common.security.AuthCookieProperties;
import com.example.backend.warehouse.dto.request.CreateWarehouseRequest;
import com.example.backend.warehouse.dto.request.UpdateWarehouseRequest;
import com.example.backend.warehouse.dto.response.WarehousePageResponse;
import com.example.backend.warehouse.dto.response.WarehouseResponse;
import com.example.backend.warehouse.entity.WarehouseStatus;
import com.example.backend.warehouse.exception.WarehouseNotFoundException;
import com.example.backend.warehouse.service.WarehouseService;
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

@WebMvcTest(WarehouseController.class)
class WarehouseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WarehouseService warehouseService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private AuthCookieProperties authCookieProperties;

    @Test
    @WithMockUser
    void getWarehouses_shouldReturn200WhenAuthenticated() throws Exception {
        WarehousePageResponse response = new WarehousePageResponse(
                List.of(warehouseResponse()), 0, 20, 1, 1
        );
        when(warehouseService.getWarehouses(0, 20)).thenReturn(response);

        mockMvc.perform(get("/api/warehouses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].warehouseName").value("Kho trung tâm"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser
    void getWarehouseById_shouldReturn200WhenWarehouseExists() throws Exception {
        WarehouseResponse response = warehouseResponse();
        when(warehouseService.getWarehouseById(response.getWarehouseId()))
                .thenReturn(response);

        mockMvc.perform(get("/api/warehouses/{warehouseId}", response.getWarehouseId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warehouseId").value(response.getWarehouseId().toString()))
                .andExpect(jsonPath("$.warehouseName").value("Kho trung tâm"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser
    void getWarehouseById_shouldReturn404WhenWarehouseDoesNotExist() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseService.getWarehouseById(warehouseId)).thenThrow(
                new WarehouseNotFoundException("Không tìm thấy kho")
        );

        mockMvc.perform(get("/api/warehouses/{warehouseId}", warehouseId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void createWarehouse_shouldReturn201WithCsrf() throws Exception {
        when(warehouseService.createWarehouse(any(CreateWarehouseRequest.class)))
                .thenReturn(warehouseResponse());

        mockMvc.perform(post("/api/warehouses")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("Kho trung tâm")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warehouseName").value("Kho trung tâm"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser
    void updateWarehouse_shouldReturn200AndKeepPersistedStatus() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        WarehouseResponse response = warehouseResponse();
        when(warehouseService.updateWarehouse(eq(warehouseId), any(UpdateWarehouseRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/warehouses/{warehouseId}", warehouseId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("Kho đã cập nhật")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser
    void updateWarehouse_shouldReturn404WhenWarehouseDoesNotExist() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseService.updateWarehouse(eq(warehouseId), any(UpdateWarehouseRequest.class)))
                .thenThrow(new WarehouseNotFoundException("Không tìm thấy kho"));

        mockMvc.perform(put("/api/warehouses/{warehouseId}", warehouseId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("Kho trung tâm")))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void createWarehouse_shouldReturn400WhenRequiredSchemaFieldIsMissing() throws Exception {
        mockMvc.perform(post("/api/warehouses")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"Quận 1, TP. Hồ Chí Minh\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void createWarehouse_shouldReturn400WhenWarehouseNameExceedsSchemaLength() throws Exception {
        String overlongName = "a".repeat(256);

        mockMvc.perform(post("/api/warehouses")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(overlongName)))
                .andExpect(status().isBadRequest());
    }

    private WarehouseResponse warehouseResponse() {
        return new WarehouseResponse(
                UUID.randomUUID(),
                "Kho trung tâm",
                "Quận 1, TP. Hồ Chí Minh",
                WarehouseStatus.ACTIVE
        );
    }

    private String requestBody(String warehouseName) {
        return "{"
                + "\"warehouseName\":\"" + warehouseName + "\","
                + "\"address\":\"Quận 1, TP. Hồ Chí Minh\""
                + "}";
    }
}
