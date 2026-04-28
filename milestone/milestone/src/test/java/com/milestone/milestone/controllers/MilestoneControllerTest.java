package com.milestone.milestone.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.milestone.milestone.dto.MilestoneFeedbackRequest;
import com.milestone.milestone.dto.MilestoneRequest;
import com.milestone.milestone.dto.MilestoneResponse;
import com.milestone.milestone.models.MilestoneStatus;
import com.milestone.milestone.security.CurrentUser;
import com.milestone.milestone.security.JwtIdentityService;
import com.milestone.milestone.services.MilestoneService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MilestoneController.class)
class MilestoneControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MilestoneService milestoneService;

    @MockBean
    private JwtIdentityService jwtIdentityService;

    @Test
    void createReturnsCreatedMilestone() throws Exception {
        when(jwtIdentityService.parseRequired("Bearer token")).thenReturn(currentUser());
        when(milestoneService.create(any(MilestoneRequest.class), eq(currentUser()))).thenReturn(sampleResponse(MilestoneStatus.PENDING));

        mockMvc.perform(post("/api")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getByIdReturnsMilestone() throws Exception {
        when(jwtIdentityService.parseRequired("Bearer token")).thenReturn(currentUser());
        when(milestoneService.getById(20L, currentUser())).thenReturn(sampleResponse(MilestoneStatus.SUBMITTED));

        mockMvc.perform(get("/api/20").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void listReturnsMilestonesWhenContractIdProvided() throws Exception {
        when(jwtIdentityService.parseRequired("Bearer token")).thenReturn(currentUser());
        when(milestoneService.getByContractId(10L, currentUser())).thenReturn(List.of(sampleResponse(MilestoneStatus.PENDING)));

        mockMvc.perform(get("/api")
                        .header("Authorization", "Bearer token")
                        .param("contractId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contractId").value(10));
    }

    @Test
    void listReturnsConflictWhenContractIdMissing() throws Exception {
        mockMvc.perform(get("/api").header("Authorization", "Bearer token"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void updateReturnsUpdatedMilestone() throws Exception {
        when(jwtIdentityService.parseRequired("Bearer token")).thenReturn(currentUser());
        when(milestoneService.update(eq(20L), any(MilestoneRequest.class), eq(currentUser())))
                .thenReturn(sampleResponse(MilestoneStatus.REVISION_REQUESTED));

        mockMvc.perform(put("/api/20")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVISION_REQUESTED"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        when(jwtIdentityService.parseRequired("Bearer token")).thenReturn(currentUser());
        doNothing().when(milestoneService).delete(20L, currentUser());

        mockMvc.perform(delete("/api/20").header("Authorization", "Bearer token"))
                .andExpect(status().isNoContent());

        verify(milestoneService).delete(20L, currentUser());
    }

    @Test
    void approveReturnsApprovedMilestone() throws Exception {
        when(jwtIdentityService.parseRequired("Bearer token")).thenReturn(currentUser());
        when(milestoneService.approve(20L, currentUser())).thenReturn(sampleResponse(MilestoneStatus.APPROVED));

        mockMvc.perform(patch("/api/20/approve").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void requestRevisionReturnsRevisionRequestedMilestone() throws Exception {
        when(jwtIdentityService.parseRequired("Bearer token")).thenReturn(currentUser());
        when(milestoneService.requestRevision(eq(20L), any(MilestoneFeedbackRequest.class), eq(currentUser())))
                .thenReturn(sampleResponse(MilestoneStatus.REVISION_REQUESTED));

        mockMvc.perform(patch("/api/20/request-revision")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MilestoneFeedbackRequest("Please revise", 3L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVISION_REQUESTED"));
    }

    @Test
    void markFundedReturnsFundedMilestone() throws Exception {
        when(milestoneService.markFunded(20L)).thenReturn(sampleResponse(MilestoneStatus.FUNDED));

        mockMvc.perform(post("/api/20/mark-funded"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FUNDED"));
    }

    @Test
    void markPaidReturnsPaidMilestone() throws Exception {
        when(milestoneService.markPaid(20L)).thenReturn(sampleResponse(MilestoneStatus.PAID));

        mockMvc.perform(post("/api/20/mark-paid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void submitReturnsSubmittedMilestone() throws Exception {
        when(jwtIdentityService.parseRequired("Bearer token")).thenReturn(currentUser());
        when(milestoneService.submit(20L, currentUser())).thenReturn(sampleResponse(MilestoneStatus.SUBMITTED));

        mockMvc.perform(patch("/api/20/submit").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
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
