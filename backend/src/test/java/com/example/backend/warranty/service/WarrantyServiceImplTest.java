package com.example.backend.warranty.service;

import com.example.backend.auth.entity.Customer;
import com.example.backend.cart.repository.CustomerCartRepository;
import com.example.backend.warranty.dto.CreateWarrantyTicketRequest;
import com.example.backend.warranty.entity.Warranty;
import com.example.backend.warranty.entity.WarrantyStatus;
import com.example.backend.warranty.entity.WarrantyTicket;
import com.example.backend.warranty.entity.WarrantyTicketStatus;
import com.example.backend.warranty.exception.WarrantyException;
import com.example.backend.warranty.repository.WarrantyRepository;
import com.example.backend.warranty.repository.WarrantyTicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarrantyServiceImplTest {

    @Mock
    private WarrantyRepository warranties;

    @Mock
    private WarrantyTicketRepository tickets;

    @Mock
    private CustomerCartRepository customers;

    @InjectMocks
    private WarrantyServiceImpl service;

    @Test
    void createTicket_shouldRejectWhenDeviceAlreadyHasOpenTicket() {
        UUID userId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Warranty warranty = activeWarranty(customerId);
        WarrantyTicket open = new WarrantyTicket();
        open.setTicketCode("WT-DANG-XU-LY");
        open.setStatus(WarrantyTicketStatus.IN_PROGRESS);

        Customer customer = mock(Customer.class);
        when(customer.getCustomerId()).thenReturn(customerId);
        when(customers.findByUser_UserId(userId)).thenReturn(Optional.of(customer));
        when(warranties.lockByWarrantyIdAndCustomerId(warranty.getWarrantyId(), customerId))
                .thenReturn(Optional.of(warranty));
        when(tickets.findFirstByWarrantyIdAndStatusInOrderByCreatedAtDesc(warranty.getWarrantyId(),
                List.of(WarrantyTicketStatus.RECEIVED, WarrantyTicketStatus.IN_PROGRESS)))
                .thenReturn(Optional.of(open));

        WarrantyException error = assertThrows(WarrantyException.class, () -> service.createTicket(
                userId, warranty.getWarrantyId(), new CreateWarrantyTicketRequest("Máy không lên nguồn")));

        assertEquals(409, error.getStatus());
        assertTrue(error.getMessage().contains("WT-DANG-XU-LY"));
        verify(tickets, never()).saveAndFlush(any());
    }

    private Warranty activeWarranty(UUID customerId) {
        Warranty warranty = new Warranty();
        warranty.setWarrantyId(UUID.randomUUID());
        warranty.setSerialId(UUID.randomUUID());
        warranty.setCustomerId(customerId);
        warranty.setStatus(WarrantyStatus.ACTIVE);
        warranty.setStartDate(LocalDate.now().minusDays(10));
        warranty.setEndDate(LocalDate.now().plusMonths(12));
        return warranty;
    }
}
