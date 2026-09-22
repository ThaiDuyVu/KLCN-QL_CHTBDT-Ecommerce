package com.example.backend.goodsreceipt.controller;

import com.example.backend.auth.service.CustomUserDetailsService;
import com.example.backend.auth.service.JwtService;
import com.example.backend.common.security.AuthCookieProperties;
import com.example.backend.common.security.CorsProperties;
import com.example.backend.common.security.SecurityConfig;
import com.example.backend.goodsreceipt.dto.request.CreateGoodsReceiptRequest;
import com.example.backend.goodsreceipt.dto.response.GoodsReceiptItemResponse;
import com.example.backend.goodsreceipt.dto.response.GoodsReceiptPageResponse;
import com.example.backend.goodsreceipt.dto.response.GoodsReceiptResponse;
import com.example.backend.goodsreceipt.dto.response.GoodsReceiptSummaryResponse;
import com.example.backend.goodsreceipt.exception.GoodsReceiptNotFoundException;
import com.example.backend.goodsreceipt.exception.GoodsReceiptReferenceNotFoundException;
import com.example.backend.goodsreceipt.exception.GoodsReceiptTotalAmountExceededException;
import com.example.backend.goodsreceipt.exception.InvalidGoodsReceiptStatusTransitionException;
import com.example.backend.goodsreceipt.exception.ReceiptCodeAlreadyExistsException;
import com.example.backend.goodsreceipt.entity.GoodsReceiptStatus;
import com.example.backend.goodsreceipt.service.GoodsReceiptService;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GoodsReceiptController.class)
@Import({SecurityConfig.class, GoodsReceiptControllerTest.SecurityTestConfiguration.class})
class GoodsReceiptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoodsReceiptService goodsReceiptService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void getGoodsReceipts_shouldReturn200WhenAuthenticated() throws Exception {
        GoodsReceiptPageResponse response = new GoodsReceiptPageResponse(
                List.of(summaryResponse()), 0, 20, 1, 1
        );
        when(goodsReceiptService.getGoodsReceipts(0, 20)).thenReturn(response);

        mockMvc.perform(get("/api/goods-receipts")
                        .with(authenticatedAs("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].receiptCode").value("GR-001"))
                .andExpect(jsonPath("$.content[0].totalAmount").value(350000.00))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getGoodsReceiptById_shouldReturn200WithItems() throws Exception {
        GoodsReceiptResponse response = goodsReceiptResponse();
        when(goodsReceiptService.getGoodsReceiptById(response.getReceiptId()))
                .thenReturn(response);

        mockMvc.perform(get("/api/goods-receipts/{receiptId}", response.getReceiptId())
                        .with(authenticatedAs("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptId").value(response.getReceiptId().toString()))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitCost").value(100000.00));
    }

    @Test
    void getGoodsReceiptById_shouldReturn404WhenReceiptDoesNotExist() throws Exception {
        UUID receiptId = UUID.randomUUID();
        when(goodsReceiptService.getGoodsReceiptById(receiptId))
                .thenThrow(new GoodsReceiptNotFoundException("Không tìm thấy phiếu nhập"));

        mockMvc.perform(get("/api/goods-receipts/{receiptId}", receiptId)
                        .with(authenticatedAs("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void createGoodsReceipt_shouldReturn201WithCsrf() throws Exception {
        when(goodsReceiptService.createGoodsReceipt(any(CreateGoodsReceiptRequest.class)))
                .thenReturn(goodsReceiptResponse());

        mockMvc.perform(post("/api/goods-receipts")
                        .with(authenticatedAs("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiptCode").value("GR-001"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.totalAmount").value(350000.00))
                .andExpect(jsonPath("$.items[0].variantId").exists());
    }

    @Test
    void createGoodsReceipt_shouldReturn409WhenReceiptCodeAlreadyExists() throws Exception {
        when(goodsReceiptService.createGoodsReceipt(any(CreateGoodsReceiptRequest.class)))
                .thenThrow(new ReceiptCodeAlreadyExistsException("Mã phiếu nhập đã tồn tại"));

        mockMvc.perform(post("/api/goods-receipts")
                        .with(authenticatedAs("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isConflict());
    }

    @Test
    void createGoodsReceipt_shouldReturn404WhenReferenceDoesNotExist() throws Exception {
        when(goodsReceiptService.createGoodsReceipt(any(CreateGoodsReceiptRequest.class)))
                .thenThrow(new GoodsReceiptReferenceNotFoundException("Không tìm thấy nhân viên"));

        mockMvc.perform(post("/api/goods-receipts")
                        .with(authenticatedAs("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createGoodsReceipt_shouldReturn400WhenCalculatedTotalExceedsNumericCapacity() throws Exception {
        when(goodsReceiptService.createGoodsReceipt(any(CreateGoodsReceiptRequest.class)))
                .thenThrow(new GoodsReceiptTotalAmountExceededException(
                        "Tổng tiền phiếu nhập vượt quá NUMERIC(15,2)"
                ));

        mockMvc.perform(post("/api/goods-receipts")
                        .with(authenticatedAs("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createGoodsReceipt_shouldReturn400WhenSchemaBackedFieldsAreInvalid() throws Exception {
        String invalidRequest = "{"
                + "\"receiptCode\":\"GR-001\","
                + "\"supplierId\":\"" + UUID.randomUUID() + "\","
                + "\"warehouseId\":\"" + UUID.randomUUID() + "\","
                + "\"employeeId\":\"" + UUID.randomUUID() + "\","
                + "\"items\":[{"
                + "\"variantId\":\"" + UUID.randomUUID() + "\","
                + "\"quantity\":0,"
                + "\"unitCost\":-1"
                + "}]"
                + "}";

        mockMvc.perform(post("/api/goods-receipts")
                        .with(authenticatedAs("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateGoodsReceiptStatus_shouldReturn200ForValidTransition() throws Exception {
        UUID receiptId = UUID.randomUUID();
        GoodsReceiptResponse response = goodsReceiptResponse(GoodsReceiptStatus.CONFIRMED);
        when(goodsReceiptService.updateGoodsReceiptStatus(receiptId, GoodsReceiptStatus.CONFIRMED))
                .thenReturn(response);

        mockMvc.perform(patch("/api/goods-receipts/{receiptId}/status", receiptId)
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void updateGoodsReceiptStatus_shouldReturn200ForManager() throws Exception {
        UUID receiptId = UUID.randomUUID();
        GoodsReceiptResponse response = goodsReceiptResponse(GoodsReceiptStatus.CANCELLED);
        when(goodsReceiptService.updateGoodsReceiptStatus(receiptId, GoodsReceiptStatus.CANCELLED))
                .thenReturn(response);

        mockMvc.perform(patch("/api/goods-receipts/{receiptId}/status", receiptId)
                        .with(authenticatedAs("MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void updateGoodsReceiptStatus_shouldReturn403ForUnrelatedAuthority() throws Exception {
        mockMvc.perform(patch("/api/goods-receipts/{receiptId}/status", UUID.randomUUID())
                        .with(authenticatedAs("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(goodsReceiptService);
    }

    @Test
    void updateGoodsReceiptStatus_shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(patch("/api/goods-receipts/{receiptId}/status", UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(goodsReceiptService);
    }

    @Test
    void updateGoodsReceiptStatus_shouldReturn409ForInvalidTransition() throws Exception {
        UUID receiptId = UUID.randomUUID();
        when(goodsReceiptService.updateGoodsReceiptStatus(receiptId, GoodsReceiptStatus.CONFIRMED))
                .thenThrow(new InvalidGoodsReceiptStatusTransitionException("Chuyển trạng thái không hợp lệ"));

        mockMvc.perform(patch("/api/goods-receipts/{receiptId}/status", receiptId)
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateGoodsReceiptStatus_shouldReturn400ForMissingOrUnsupportedStatus() throws Exception {
        UUID receiptId = UUID.randomUUID();

        mockMvc.perform(patch("/api/goods-receipts/{receiptId}/status", receiptId)
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/goods-receipts/{receiptId}/status", receiptId)
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createGoodsReceipt_shouldReturn400WhenRequiredHeaderFieldIsMissing() throws Exception {
        mockMvc.perform(post("/api/goods-receipts")
                        .with(authenticatedAs("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiptCode\":\"GR-001\"}"))
                .andExpect(status().isBadRequest());
    }

    private GoodsReceiptSummaryResponse summaryResponse() {
        GoodsReceiptResponse response = goodsReceiptResponse();
        return new GoodsReceiptSummaryResponse(
                response.getReceiptId(),
                response.getReceiptCode(),
                response.getSupplierId(),
                response.getWarehouseId(),
                response.getEmployeeId(),
                response.getReceiptDate(),
                response.getTotalAmount(),
                response.getStatus()
        );
    }

    private GoodsReceiptResponse goodsReceiptResponse() {
        return goodsReceiptResponse(GoodsReceiptStatus.DRAFT);
    }

    private GoodsReceiptResponse goodsReceiptResponse(GoodsReceiptStatus status) {
        return new GoodsReceiptResponse(
                UUID.randomUUID(),
                "GR-001",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-09-12T10:00:00+07:00"),
                new BigDecimal("350000.00"),
                status,
                List.of(new GoodsReceiptItemResponse(
                        UUID.randomUUID(), UUID.randomUUID(), 2, new BigDecimal("100000.00")
                ))
        );
    }

    private static RequestPostProcessor authenticatedAs(String authority) {
        return user("test-user")
                .authorities(new SimpleGrantedAuthority(authority));
    }

    private String validRequestBody() {
        return "{"
                + "\"receiptCode\":\"GR-001\","
                + "\"supplierId\":\"" + UUID.randomUUID() + "\","
                + "\"warehouseId\":\"" + UUID.randomUUID() + "\","
                + "\"employeeId\":\"" + UUID.randomUUID() + "\","
                + "\"items\":[{"
                + "\"variantId\":\"" + UUID.randomUUID() + "\","
                + "\"quantity\":2,"
                + "\"unitCost\":100000.00"
                + "}]"
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
