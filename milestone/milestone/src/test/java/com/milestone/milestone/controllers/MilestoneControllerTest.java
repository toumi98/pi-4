package com.milestone.milestone.controllers;

import com.milestone.milestone.dto.MilestoneFeedbackRequest;
import com.milestone.milestone.dto.MilestoneRequest;
import com.milestone.milestone.dto.MilestoneResponse;
import com.milestone.milestone.models.MilestoneStatus;
import com.milestone.milestone.security.CurrentUser;
import com.milestone.milestone.security.JwtIdentityService;
import com.milestone.milestone.services.MilestoneService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MilestoneControllerTest {

    @Mock
    private MilestoneService milestoneService;

    @Mock
    private JwtIdentityService jwtIdentityService;

    @InjectMocks
    private MilestoneController controller;

    @Test
    void createDelegatesToService() {
        when(jwtIdentityService.parseRequired("Bearer token")).thenReturn(currentUser());
        when(milestoneService.create(any(MilestoneRequest.class), eq(currentUser()))).thenReturn(sampleResponse(MilestoneStatus.PENDING));

        MilestoneResponse response = controller.create("Bearer token", sampleRequest());

        assertEquals(MilestoneStatus.PENDING, response.status());
    }

    @Test
    void getByIdDelegatesToService() {
        when(jwtIdentityService.parseRequired("Bearer token")).thenReturn(currentUser());
        when(milestoneService.getById(20L, currentUser())).thenReturn(sampleResponse(MilestoneStatus.SUBMITTED));

        MilestoneResponse response = controller.getById("Bearer token", 20L);

        assertEquals(20L, response.id());
        assertEquals(MilestoneStatus.SUBMITTED, response.status());
    }

    @Test
    void listDelegatesWhenContractIdPresent() {
        when(jwtIdentityService.parseRequired("Bearer token")).thenReturn(currentUser());
        when(milestoneService.getByContractId(10L, currentUser())).thenReturn(List.of(sampleResponse(MilestoneStatus.PENDING)));

        List<MilestoneResponse> response = controller.list("Bearer token", 10L);

        assertEquals(1, response.size());
        assertEquals(10L, response.get(0).contractId());
    }

    @Test
    void listThrowsWhenContractIdMissing() {
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> controller.list("Bearer token", null)
        );

        assertEquals("contractId is required for now", exception.getMessage());
    }

    @Test
    void updateDelegatesToService() {
        when(jwtIdentityService.parseRequired("Bearer token")).thenReturn(currentUser());
        when(milestoneService.update(eq(20L), any(MilestoneRequest.class), eq(currentUser())))
                .thenReturn(sampleResponse(MilestoneStatus.REVISION_REQUESTED));

        MilestoneResponse response = controller.update("Bearer token", 20L, sampleRequest());

        assertEquals(MilestoneStatus.REVISION_REQUESTED, response.status());
    }

    @Test
    void deleteDelegatesToService() {
        when(jwtIdentityService.parseRequired("Bearer token")).thenReturn(currentUser());
        doNothing().when(milestoneService).delete(20L, currentUser());

        controller.delete("Bearer token", 20L);

        verify(milestoneService).delete(20L, currentUser());
    }

    @Test
    void approveDelegatesToService() {
        when(jwtIdentityService.parseRequired("Bearer token")).thenReturn(currentUser());
        when(milestoneService.approve(20L, currentUser())).thenReturn(sampleResponse(MilestoneStatus.APPROVED));

        MilestoneResponse response = controller.approve("Bearer token", 20L);

        assertEquals(MilestoneStatus.APPROVED, response.status());
    }

    @Test
    void requestRevisionDelegatesToService() {
        MilestoneFeedbackRequest request = new MilestoneFeedbackRequest("Please revise", 3L);
        when(jwtIdentityService.parseRequired("Bearer token")).thenReturn(currentUser());
        when(milestoneService.requestRevision(eq(20L), eq(request), eq(currentUser())))
                .thenReturn(sampleResponse(MilestoneStatus.REVISION_REQUESTED));

        MilestoneResponse response = controller.requestRevision("Bearer token", 20L, request);

        assertEquals(MilestoneStatus.REVISION_REQUESTED, response.status());
    }

    @Test
    void markFundedDelegatesToService() {
        when(milestoneService.markFunded(20L)).thenReturn(sampleResponse(MilestoneStatus.FUNDED));

        MilestoneResponse response = controller.markFunded(20L);

        assertEquals(MilestoneStatus.FUNDED, response.status());
    }

    @Test
    void markPaidDelegatesToService() {
        when(milestoneService.markPaid(20L)).thenReturn(sampleResponse(MilestoneStatus.PAID));

        MilestoneResponse response = controller.markPaid(20L);

        assertEquals(MilestoneStatus.PAID, response.status());
    }

    @Test
    void submitDelegatesToService() {
        when(jwtIdentityService.parseRequired("Bearer token")).thenReturn(currentUser());
        when(milestoneService.submit(20L, currentUser())).thenReturn(sampleResponse(MilestoneStatus.SUBMITTED));

        MilestoneResponse response = controller.submit("Bearer token", 20L);

        assertEquals(MilestoneStatus.SUBMITTED, response.status());
    }

    private CurrentUser currentUser() {
        return new CurrentUser(3L, "client@test.com", "CLIENT");
    }

    private MilestoneRequest sampleRequest() {
        return new MilestoneRequest(
                10L,
                "API delivery",
                "Deliver REST endpoints",
                new BigDecimal("500.00"),
                LocalDate.now().plusDays(7)
        );
    }

    private MilestoneResponse sampleResponse(MilestoneStatus status) {
        return new MilestoneResponse(
                20L,
                10L,
                "API delivery",
                "Deliver REST endpoints",
                new BigDecimal("500.00"),
                LocalDate.now().plusDays(7),
                status,
                0,
                null,
                LocalDateTime.now(),
                null,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
