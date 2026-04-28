package com.milestone.milestone.services;

import com.milestone.milestone.client.UserClient;
import com.milestone.milestone.dto.ContractRequest;
import com.milestone.milestone.dto.ContractResponse;
import com.milestone.milestone.dto.UserSummary;
import com.milestone.milestone.models.Contract;
import com.milestone.milestone.models.ContractStatus;
import com.milestone.milestone.models.Milestone;
import com.milestone.milestone.repositories.ContractRepository;
import com.milestone.milestone.repositories.MilestoneRepository;
import com.milestone.milestone.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @Mock
    private ContractRepository repo;

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private UserClient userClient;

    @InjectMocks
    private ContractService contractService;

    @Test
    void createBuildsPendingAcceptanceContract() {
        ContractRequest request = sampleRequest();
        CurrentUser actor = new CurrentUser(3L, "client@test.com", "CLIENT");

        when(userClient.getUserById(3L)).thenReturn(sampleUser(3L, "Client", "Owner", true, true));
        when(userClient.getUserById(8L)).thenReturn(sampleUser(8L, "Free", "Lancer", true, true));
        when(repo.save(any(Contract.class))).thenAnswer(invocation -> {
            Contract contract = invocation.getArgument(0);
            contract.setId(15L);
            return contract;
        });

        ContractResponse response = contractService.create(request, actor);

        ArgumentCaptor<Contract> contractCaptor = ArgumentCaptor.forClass(Contract.class);
        verify(repo).save(contractCaptor.capture());
        Contract saved = contractCaptor.getValue();

        assertEquals(ContractStatus.PENDING_ACCEPTANCE, saved.getStatus());
        assertEquals("Client Owner", saved.getClientName());
        assertEquals("Free Lancer", saved.getFreelancerName());
        assertEquals(15L, response.id());
        assertEquals(3L, response.clientId());
    }

    @Test
    void createRejectsInactiveFreelancer() {
        ContractRequest request = sampleRequest();
        CurrentUser actor = new CurrentUser(3L, "client@test.com", "CLIENT");

        when(userClient.getUserById(3L)).thenReturn(sampleUser(3L, "Client", "Owner", true, true));
        when(userClient.getUserById(8L)).thenReturn(sampleUser(8L, "Free", "Lancer", true, false));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> contractService.create(request, actor)
        );

        assertEquals("Freelancer account is not active", exception.getMessage());
        verify(repo, never()).save(any(Contract.class));
    }

    @Test
    void listReturnsClientContractsForClientActor() {
        CurrentUser actor = new CurrentUser(3L, "client@test.com", "CLIENT");
        Contract contract = sampleContract();

        when(repo.findByClientId(3L)).thenReturn(List.of(contract));

        List<ContractResponse> result = contractService.list(null, null, null, actor);

        assertEquals(1, result.size());
        assertEquals(3L, result.get(0).clientId());
    }

    @Test
    void updateRejectsActiveContract() {
        Contract contract = sampleContract();
        contract.setStatus(ContractStatus.ACTIVE);

        when(repo.findById(11L)).thenReturn(Optional.of(contract));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> contractService.update(11L, sampleRequest(), new CurrentUser(3L, "client@test.com", "CLIENT"))
        );

        assertEquals("Only pending or draft contracts can be updated", exception.getMessage());
    }

    @Test
    void acceptMarksPendingContractActive() {
        Contract contract = sampleContract();

        when(repo.findById(11L)).thenReturn(Optional.of(contract));
        when(repo.save(any(Contract.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContractResponse response = contractService.accept(11L, new CurrentUser(8L, "freelancer@test.com", "FREELANCER"));

        assertEquals(ContractStatus.ACTIVE, response.status());
        assertNotNull(contract.getRespondedAt());
    }

    @Test
    void rejectMarksPendingContractRejected() {
        Contract contract = sampleContract();

        when(repo.findById(11L)).thenReturn(Optional.of(contract));
        when(repo.save(any(Contract.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContractResponse response = contractService.reject(11L, new CurrentUser(8L, "freelancer@test.com", "FREELANCER"));

        assertEquals(ContractStatus.REJECTED, response.status());
        assertNotNull(contract.getRespondedAt());
    }

    @Test
    void completeRejectsNonActiveContract() {
        Contract contract = sampleContract();
        contract.setStatus(ContractStatus.REJECTED);

        when(repo.findById(11L)).thenReturn(Optional.of(contract));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> contractService.complete(11L, new CurrentUser(3L, "client@test.com", "CLIENT"))
        );

        assertEquals("Only active contracts can be completed", exception.getMessage());
    }

    @Test
    void completeMarksActiveContractCompleted() {
        Contract contract = sampleContract();
        contract.setStatus(ContractStatus.ACTIVE);

        when(repo.findById(11L)).thenReturn(Optional.of(contract));
        when(repo.save(any(Contract.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContractResponse response = contractService.complete(11L, new CurrentUser(3L, "client@test.com", "CLIENT"));

        assertEquals(ContractStatus.COMPLETED, response.status());
    }

    @Test
    void deleteRejectsActiveContractWithMilestones() {
        Contract contract = sampleContract();
        contract.setStatus(ContractStatus.ACTIVE);

        when(repo.findById(11L)).thenReturn(Optional.of(contract));
        when(milestoneRepository.findByContractId(11L)).thenReturn(List.of(Milestone.builder().id(1L).build()));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> contractService.delete(11L, new CurrentUser(3L, "client@test.com", "CLIENT"))
        );

        assertEquals("Active contracts with milestones cannot be deleted", exception.getMessage());
        verify(repo, never()).delete(any(Contract.class));
    }

    @Test
    void deleteRemovesNonActiveContract() {
        Contract contract = sampleContract();
        contract.setStatus(ContractStatus.REJECTED);

        when(repo.findById(11L)).thenReturn(Optional.of(contract));

        contractService.delete(11L, new CurrentUser(3L, "client@test.com", "CLIENT"));

        verify(repo).delete(contract);
    }

    private ContractRequest sampleRequest() {
        return new ContractRequest(
                3L,
                8L,
                "Website",
                "Build website",
                new BigDecimal("1000.00"),
                "Client Owner",
                "Free Lancer",
                LocalDate.now(),
                LocalDate.now().plusDays(30)
        );
    }

    private Contract sampleContract() {
        return Contract.builder()
                .id(11L)
                .clientId(3L)
                .freelancerId(8L)
                .title("Website")
                .scope("Build website")
                .totalBudget(new BigDecimal("1000.00"))
                .clientName("Client Owner")
                .freelancerName("Free Lancer")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(30))
                .status(ContractStatus.PENDING_ACCEPTANCE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private UserSummary sampleUser(Long id, String firstName, String lastName, boolean verified, boolean active) {
        return new UserSummary(
                id,
                firstName.toLowerCase() + "@test.com",
                firstName,
                lastName,
                "USER",
                null,
                null,
                null,
                null,
                null,
                null,
                verified,
                active
        );
    }
}
