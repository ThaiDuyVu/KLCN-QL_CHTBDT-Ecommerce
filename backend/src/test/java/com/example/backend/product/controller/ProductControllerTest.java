package com.example.backend.product.controller;

import com.example.backend.product.entity.ProductStatus;
import com.example.backend.auth.service.CustomUserDetailsService;
import com.example.backend.auth.service.JwtService;
import com.example.backend.common.security.AuthCookieProperties;
import com.example.backend.product.dto.ProductRequest;
import com.example.backend.product.dto.ProductResponse;
import com.example.backend.product.dto.ProductPageResponse;
import com.example.backend.product.exception.InvalidProductPaginationException;
import com.example.backend.product.exception.ProductInUseException;
import com.example.backend.product.exception.ProductNotFoundException;
import com.example.backend.product.exception.ProductReferenceNotFoundException;
import com.example.backend.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ProductService productService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private AuthCookieProperties authCookieProperties;

    @Test
    @WithMockUser
    void getProducts_usesSafeDefaultsWithoutReturningAnArray() throws Exception {
        when(productService.getProducts(0, 20)).thenReturn(new ProductPageResponse(List.of(response()), 0, 20, 25, 2));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productName").value("Thiết bị mẫu"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @WithMockUser
    void getProducts_forwardsExplicitPageAndSize() throws Exception {
        when(productService.getProducts(2, 10)).thenReturn(new ProductPageResponse(List.of(), 2, 10, 15, 2));

        mockMvc.perform(get("/api/v1/products").param("page", "2").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    @WithMockUser
    void getProducts_returns400ForInvalidPagination() throws Exception {
        when(productService.getProducts(0, 101)).thenThrow(new InvalidProductPaginationException("Size phải từ 1 đến 100"));

        mockMvc.perform(get("/api/v1/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Size phải từ 1 đến 100"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "2147483648"})
    @WithMockUser
    void getProducts_rejectsNonIntegerParameters(String page) throws Exception {
        mockMvc.perform(get("/api/v1/products").param("page", page))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(productService);
    }

    @Test
    @WithMockUser
    void create_returns201AndPreservesJsonContract() throws Exception {
        when(productService.createProduct(any(ProductRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/v1/products").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Thiết bị mẫu", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productName").value("Thiết bị mẫu"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"productName\":\"Thiết bị mẫu\"}",
            "{\"productName\":\"Thiết bị mẫu\",\"categoryId\":\"10000000-0000-0000-0000-000000000001\"}",
            "{\"productName\":\"Thiết bị mẫu\",\"brandId\":\"10000000-0000-0000-0000-000000000002\"}"})
    @WithMockUser
    void create_rejectsMissingRequiredFields(String json) throws Exception {
        mockMvc.perform(post("/api/v1/products").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(productService);
    }

    @Test
    @WithMockUser
    void create_rejectsOversizedNameAndStatus() throws Exception {
        mockMvc.perform(post("/api/v1/products").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("x".repeat(256), null)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/products").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Thiết bị mẫu", "x".repeat(31))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(productService);
    }

    @Test
    @WithMockUser
    void update_validatesTheSameSchemaConstraints() throws Exception {
        mockMvc.perform(put("/api/v1/products/{id}", UUID.randomUUID()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(productService);
    }

    @Test
    @WithMockUser
    void get_returns404ForMissingProduct() throws Exception {
        UUID id = UUID.randomUUID();
        when(productService.getProductById(id)).thenThrow(new ProductNotFoundException("Không tìm thấy sản phẩm"));

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Không tìm thấy sản phẩm"));
    }

    @Test
    @WithMockUser
    void create_returns404ForMissingReference() throws Exception {
        when(productService.createProduct(any())).thenThrow(new ProductReferenceNotFoundException("Không tìm thấy danh mục"));

        mockMvc.perform(post("/api/v1/products").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Thiết bị mẫu", null)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void delete_returns409ForReferencedProduct() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ProductInUseException("Sản phẩm đang được tham chiếu", new IllegalStateException()))
                .when(productService).deleteProduct(id);

        mockMvc.perform(delete("/api/v1/products/{id}", id).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().string("Sản phẩm đang được tham chiếu"));
    }

    @Test
    @WithMockUser
    void delete_returnsExistingSuccessContractWithoutTypo() throws Exception {
        mockMvc.perform(delete("/api/v1/products/{id}", UUID.randomUUID()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("Xóa sản phẩm thành công!"));
    }

    private ProductResponse response() {
        return new ProductResponse(UUID.randomUUID(), "Thiết bị mẫu", null,
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("10000000-0000-0000-0000-000000000002"), ProductStatus.ACTIVE);
    }

    private String body(String name, String status) {
        return "{\"productName\":\"" + name + "\","
                + "\"categoryId\":\"10000000-0000-0000-0000-000000000001\","
                + "\"brandId\":\"10000000-0000-0000-0000-000000000002\""
                + (status == null ? "" : ",\"status\":\"" + status + "\"") + "}";
    }
}
