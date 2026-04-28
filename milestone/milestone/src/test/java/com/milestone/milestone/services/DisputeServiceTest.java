package com.milestone.milestone.services;

import com.milestone.milestone.dto.DisputeCreateRequest;
import com.milestone.milestone.dto.DisputeDecisionRequest;
import com.milestone.milestone.dto.DisputeResponse;
import com.milestone.milestone.models.Contract;
import com.milestone.milestone.models.ContractDispute;
import com.milestone.milestone.models.ContractStatus;
import com.milestone.milestone.models.DisputeReason;
import com.milestone.milestone.models.DisputeStatus;
import com.milestone.milestone.models.MessageSenderRole;
import com.milestone.milestone.models.Milestone;
import com.milestone.milestone.repositories.ContractDisputeRepository;
import com.milestone.milestone.repositories.ContractRepository;
import com.milestone.milestone.repositories.MilestoneRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {

    @Mock
    private ContractDisputeRepository disputeRepository;

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private DisputeService disputeService;

    @Test
    void createOpensDisputeForValidParticipant() {
        when(contractRepository.findById(10L)).thenReturn(Optional.of(activeContract()));
        when(milestoneRepository.findById(20L)).thenReturn(Optional.of(sampleMilestone()));
        when(disputeRepository.save(any(ContractDispute.class))).thenAnswer(invocation -> {
            ContractDispute dispute = invocation.getArgument(0);
            dispute.setId(7L);
            dispute.setCreatedAt(LocalDateTime.now());
            dispute.setUpdatedAt(LocalDateTime.now());
            return dispute;
        });

        DisputeResponse response = disputeService.create(sampleCreateRequest());

        assertEquals(7L, response.id());
        assertEquals(DisputeStatus.OPEN, response.status());
        assertEquals("Late milestone", response.title());
    }

    @Test
    void createRejectsCancelledContract() {
        Contract contract = activeContract();
        contract.setStatus(ContractStatus.CANCELLED);
        when(contractRepository.findById(10L)).thenReturn(Optional.of(contract));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> disputeService.create(sampleCreateRequest())
        );

        assertEquals("Disputes cannot be created for inactive contracts", exception.getMessage());
    }

    @Test
    void createRejectsMismatchedParticipant() {
        when(contractRepository.findById(10L)).thenReturn(Optional.of(activeContract()));

        DisputeCreateRequest request = new DisputeCreateRequest(
                10L,
                20L,
                30L,
                DisputeReason.DELAY,
                MessageSenderRole.CLIENT,
                99L,
                "Other User",
                "Late milestone",
                "The milestone is late"
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> disputeService.create(request)
        );

        assertEquals("Dispute actor does not match the contract participants", exception.getMessage());
    }

    @Test
    void createRejectsMilestoneFromAnotherContract() {
        Milestone milestone = sampleMilestone();
        milestone.setContractId(999L);

        when(contractRepository.findById(10L)).thenReturn(Optional.of(activeContract()));
        when(milestoneRepository.findById(20L)).thenReturn(Optional.of(milestone));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> disputeService.create(sampleCreateRequest())
        );

        assertEquals("Milestone does not belong to the contract", exception.getMessage());
    }

    @Test
    void listReturnsDisputesByContract() {
        when(contractRepository.findById(10L)).thenReturn(Optional.of(activeContract()));
        when(disputeRepository.findByContractIdOrderByUpdatedAtDesc(10L)).thenReturn(List.of(sampleDispute()));

        List<DisputeResponse> result = disputeService.list(10L, null);

        assertEquals(1, result.size());
        assertEquals(DisputeStatus.OPEN, result.get(0).status());
    }

    @Test
    void hasBlockingDisputeDelegatesToRepository() {
        when(contractRepository.findById(10L)).thenReturn(Optional.of(activeContract()));
        when(disputeRepository.existsByContractIdAndStatusIn(10L, List.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW)))
                .thenReturn(true);

        boolean result = disputeService.hasBlockingDispute(10L);

        assertEquals(true, result);
    }

    @Test
    void moveToReviewUpdatesOpenDispute() {
        ContractDispute dispute = sampleDispute();
        when(disputeRepository.findById(7L)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(ContractDispute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DisputeResponse response = disputeService.moveToReview(7L, new DisputeDecisionRequest("Needs review"));

        assertEquals(DisputeStatus.UNDER_REVIEW, response.status());
        assertEquals("Needs review", response.resolutionNote());
    }

    @Test
    void moveToReviewRejectsClosedDispute() {
        ContractDispute dispute = sampleDispute();
        dispute.setStatus(DisputeStatus.RESOLVED);
        when(disputeRepository.findById(7L)).thenReturn(Optional.of(dispute));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> disputeService.moveToReview(7L, new DisputeDecisionRequest("Needs review"))
        );

        assertEquals("Only open disputes can move to review", exception.getMessage());
    }

    @Test
    void resolveClosesDispute() {
        ContractDispute dispute = sampleDispute();
        dispute.setStatus(DisputeStatus.UNDER_REVIEW);
        when(disputeRepository.findById(7L)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(ContractDispute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DisputeResponse response = disputeService.resolve(7L, new DisputeDecisionRequest("Resolved"));

        assertEquals(DisputeStatus.RESOLVED, response.status());
        assertEquals("Resolved", response.resolutionNote());
        assertNotNull(response.resolvedAt());
    }

    @Test
    void rejectClosesDisputeAsRejected() {
        ContractDispute dispute = sampleDispute();
        dispute.setStatus(DisputeStatus.UNDER_REVIEW);
        when(disputeRepository.findById(7L)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(ContractDispute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DisputeResponse response = disputeService.reject(7L, new DisputeDecisionRequest("Rejected"));

        assertEquals(DisputeStatus.REJECTED, response.status());
        assertEquals("Rejected", response.resolutionNote());
        assertNotNull(response.resolvedAt());
    }

    private DisputeCreateRequest sampleCreateRequest() {
        return new DisputeCreateRequest(
                10L,
                20L,
                30L,
                DisputeReason.DELAY,
                MessageSenderRole.CLIENT,
                3L,
                "Client User",
                "Late milestone",
                "The milestone is late"
        );
    }

    private Contract activeContract() {
        return Contract.builder()
                .id(10L)
                .clientId(3L)
                .freelancerId(8L)
                .title("Website")
                .scope("Build website")
                .totalBudget(new BigDecimal("1000.00"))
                .clientName("Client User")
                .freelancerName("Freelancer User")
                .status(ContractStatus.ACTIVE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Milestone sampleMilestone() {
        return Milestone.builder()
                .id(20L)
                .contractId(10L)
                .title("Delivery")
                .amount(new BigDecimal("200.00"))
                .build();
    }

    private ContractDispute sampleDispute() {
        return ContractDispute.builder()
                .id(7L)
                .contractId(10L)
                .milestoneId(20L)
                .paymentId(30L)
                .reason(DisputeReason.DELAY)
                .status(DisputeStatus.OPEN)
                .openedByRole(MessageSenderRole.CLIENT)
                .openedById(3L)
                .openedByName("Client User")
                .title("Late milestone")
                .description("The milestone is late")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
