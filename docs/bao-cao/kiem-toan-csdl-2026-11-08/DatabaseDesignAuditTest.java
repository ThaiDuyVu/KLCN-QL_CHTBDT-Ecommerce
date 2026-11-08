package com.example.backend.returns;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.example.backend.order.service.InStoreSaleService;
import com.example.backend.order.dto.InStoreSaleRequest;
import com.example.backend.invoice.service.InvoiceService;
import com.example.backend.product.service.ProductVariantService;
import com.example.backend.product.dto.ProductVariantRequest;
import com.example.backend.warranty.service.WarrantyProvisioningService;
import com.example.backend.warranty.exception.WarrantyException;
import com.example.backend.order.entity.Order;
import com.example.backend.order.entity.OrderItem;
import java.util.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Diagnostic probes: PASS means the observed weakness was reproduced, not business correctness. */
class DatabaseDesignAuditTest extends ReturnIntegrationTest {
    @Autowired InStoreSaleService sales;
    @Autowired InvoiceService invoices;
    @Autowired ProductVariantService variantService;
    @Autowired WarrantyProvisioningService warrantyProvisioning;
    @Autowired PlatformTransactionManager txManager;

    UUID owner() { return jdbc.queryForObject("SELECT customer_id FROM orders WHERE order_id=?", UUID.class, order); }
    UUID otherOwner() { return jdbc.queryForObject("SELECT customer_id FROM customers WHERE user_id=?", UUID.class, otherUser); }

    @Test void D01_returnedRestockedSerialCannotBeSoldAgain() throws Exception {
        partialReturnIsApprovedReceivedRestockedAndRefundedAndReportsAreNet();
        assertThatThrownBy(() -> sales.sell(managerUser,
            new InStoreSaleRequest(owner(),warehouse,List.of(new InStoreSaleRequest.Line(phoneVariant,1,List.of("SN-X1"))),null),
            List.of("MANAGER")))
            .isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("uq_order_items_serial_id");
        assertThat(jdbc.queryForObject("SELECT status FROM serial_numbers WHERE serial_id=?",String.class,serial)).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders",Integer.class)).isEqualTo(1);
    }

    @Test void D02_voidedWarrantyStillRejectsANewSaleLifecycle() {
        jdbc.update("UPDATE warranties SET status='VOIDED' WHERE serial_id=?",serial);
        Order next = new Order(); next.setCustomerId(otherOwner());
        OrderItem line = new OrderItem();line.setOrderItemId(UUID.randomUUID());line.setVariantId(phoneVariant);line.setSerialId(serial);
        assertThatThrownBy(() -> new TransactionTemplate(txManager).execute(s -> {
            warrantyProvisioning.createForDeliveredOrder(next,List.of(line));return null;
        })).isInstanceOf(WarrantyException.class).hasMessageContaining("không khớp");
    }

    @Test void D03_updatingVariantProductAndSkuRewritesHistoricalInvoiceIdentity() {
        var before=invoices.mineForOrder(customerUser,order);
        UUID category=jdbc.queryForObject("SELECT category_id FROM products LIMIT 1",UUID.class);
        UUID brand=jdbc.queryForObject("SELECT brand_id FROM products LIMIT 1",UUID.class);
        UUID different=jdbc.queryForObject("INSERT INTO products(category_id,brand_id,product_name,status) VALUES (?,?,'Laptop KHAC','ACTIVE') RETURNING product_id",UUID.class,category,brand);
        ProductVariantRequest r=new ProductVariantRequest();r.setProductId(different);r.setSku("LAPTOP-NEW");
        r.setPrice(new BigDecimal("10000000"));r.setCostPrice(new BigDecimal("8000000"));
        variantService.updateVariant(phoneVariant,r);
        var after=invoices.mineForOrder(customerUser,order);
        assertThat(before.lines()).anyMatch(l -> l.sku().equals("DT-X") && l.productName().equals("Điện thoại X"));
        assertThat(after.lines()).anyMatch(l -> l.sku().equals("LAPTOP-NEW") && l.productName().equals("Laptop KHAC"));
        assertThat(after.totalAmount()).isEqualByComparingTo(before.totalAmount());
    }

    @Test void D04_databaseAllowsWrongWarrantyOwner() {
        assertThat(jdbc.update("UPDATE warranties SET customer_id=? WHERE serial_id=?",otherOwner(),serial)).isEqualTo(1);
    }

    @Test void D05_databaseAllowsWrongSoldSerialVariant() {
        assertThat(jdbc.update("UPDATE order_items SET variant_id=? WHERE order_item_id=?",caseVariant,phoneLine)).isEqualTo(1);
    }

    @Test void D06_databaseAllowsChatHistoryUnderAnotherCustomer() {
        UUID session=jdbc.queryForObject("INSERT INTO chat_sessions(customer_id,status) VALUES (?,'ACTIVE') RETURNING session_id",UUID.class,owner());
        assertThat(jdbc.update("INSERT INTO chat_history(session_id,customer_id,sender_type,message) VALUES (?,?,'USER','synthetic audit')",session,otherOwner())).isEqualTo(1);
    }

    @Test void D07_databaseChecksHeaderFormulaButNotItsLineSum() {
        assertThat(jdbc.update("UPDATE orders SET subtotal=1,total_amount=1 WHERE order_id=?",order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sum(unit_price*quantity) FROM order_items WHERE order_id=?",BigDecimal.class,order)).isEqualByComparingTo("10400000");
    }

    @Test void D08_databaseAllowsInventorySerialDrift() {
        assertThat(jdbc.update("UPDATE inventory SET quantity=quantity+1 WHERE variant_id=?",phoneVariant)).isEqualTo(1);
    }

    @Test void D09_databaseAllowsReturnStateWithoutReplacementOrder() throws Exception {
        request(customerUser,body(phoneLine,1)).andExpect(status().isCreated());
        assertThat(jdbc.update("UPDATE return_requests SET status='EXCHANGED' WHERE return_id=?",returnId())).isEqualTo(1);
    }

    @Test void D10_databaseAllowsReturnLineToPointAtAnotherSoldItem() throws Exception {
        request(customerUser,body(phoneLine,1)).andExpect(status().isCreated());
        assertThat(jdbc.update("UPDATE return_items SET order_item_id=? WHERE return_id=?",caseLine,returnId())).isEqualTo(1);
    }

    @Test void D11_databaseAllowsSamePhoneAcrossRegisteredCustomers() {
        assertThat(jdbc.update("UPDATE users SET phone='0900000999' WHERE user_id IN (?,?)",customerUser,otherUser)).isEqualTo(2);
    }

    @Test void D12_databaseAllowsCategoryCycle() {
        UUID cat=jdbc.queryForObject("SELECT category_id FROM products LIMIT 1",UUID.class);
        assertThat(jdbc.update("UPDATE categories SET parent_id=category_id WHERE category_id=?",cat)).isEqualTo(1);
    }

    @Test void D13_controlNegativeInventoryAndWrongHeaderFormulaAreBlocked() {
        assertThatThrownBy(() -> jdbc.update("UPDATE inventory SET quantity=-1 WHERE variant_id=?",phoneVariant)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE orders SET total_amount=7 WHERE order_id=?",order)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void D14_cancelledReplacementLeavesFinalExchangeCreditUnresolved() throws Exception {
        request(customerUser,exchangeBody(caseLine,2,headsetVariant,1)).andExpect(status().isCreated());
        UUID id=approveAndReceive("RESTOCK");
        move(id,salesUser,"SALES_STAFF","{\"status\":\"EXCHANGED\"}").andExpect(status().isOk());
        UUID replacement=jdbc.queryForObject("SELECT exchange_order_id FROM return_requests WHERE return_id=?",UUID.class,id);
        orderService.status(managerUser,false,replacement,com.example.backend.order.entity.OrderStatus.CANCELLED);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE order_id=?",String.class,replacement)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM return_requests WHERE return_id=?",String.class,id)).isEqualTo("EXCHANGED");
        assertThat(jdbc.queryForObject("SELECT refund_amount FROM return_requests WHERE return_id=?",BigDecimal.class,id)).isEqualByComparingTo("400000");
        move(id,managerUser,"MANAGER","{\"status\":\"REFUNDED\"}").andExpect(status().isConflict());
        request(customerUser,body(caseLine,2)).andExpect(status().isConflict());
    }

    @Autowired com.example.backend.auth.service.UserService userService;
    @Test void D15_userUpdateServiceBypassesCustomerUniquePhoneRule() {
        jdbc.update("UPDATE users SET phone='0900000999' WHERE user_id=?",customerUser);
        var r=new com.example.backend.auth.dto.UpdateUserRequest();
        r.setDisplayName("Khách thử");r.setEmail("other-audit@example.test");r.setPhone("0900000999");
        userService.updateUser(otherUser,r);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers c JOIN users u USING(user_id) WHERE u.phone='0900000999'",Integer.class)).isEqualTo(2);
    }

    @Autowired com.example.backend.product.service.SpecificationService specifications;
    @Test void D16_specificationServiceAllowsContradictoryDuplicateKeys() {
        UUID product=jdbc.queryForObject("SELECT product_id FROM product_variants WHERE variant_id=?",UUID.class,phoneVariant);
        var a=new com.example.backend.product.dto.SpecificationRequest();a.setSpecKey("CPU");a.setSpecValue("Intel i5");
        var b=new com.example.backend.product.dto.SpecificationRequest();b.setSpecKey("CPU");b.setSpecValue("AMD Ryzen 7");
        specifications.createSpecification(product,a);specifications.createSpecification(product,b);
        assertThat(specifications.getSpecifications(product)).filteredOn(s->s.getSpecKey().equals("CPU")).hasSize(2);
    }
}
