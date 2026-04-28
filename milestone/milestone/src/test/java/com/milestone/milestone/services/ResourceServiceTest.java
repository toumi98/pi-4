package com.milestone.milestone.services;

import com.milestone.milestone.dto.ResourceLinkRequest;
import com.milestone.milestone.dto.ResourceResponse;
import com.milestone.milestone.exception.NotFoundException;
import com.milestone.milestone.models.Contract;
import com.milestone.milestone.models.ContractStatus;
import com.milestone.milestone.models.Milestone;
import com.milestone.milestone.models.ResourceCategory;
import com.milestone.milestone.models.ResourceEntityType;
import com.milestone.milestone.models.ResourceItem;
import com.milestone.milestone.models.ResourceType;
import com.milestone.milestone.repositories.ContractRepository;
import com.milestone.milestone.repositories.MilestoneRepository;
import com.milestone.milestone.repositories.ResourceItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
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
class ResourceServiceTest {

    @Mock
    private ResourceItemRepository repo;

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private MilestoneRepository milestoneRepository;

    @InjectMocks
    private ResourceService resourceService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(resourceService, "uploadsRoot", tempDir.toString());
    }

    @Test
    void createLinkSavesValidLink() {
        ResourceLinkRequest request = new ResourceLinkRequest(
                ResourceEntityType.CONTRACT,
                10L,
                ResourceCategory.REFERENCE,
                "Project brief",
                "https://example.com/brief"
        );

        when(contractRepository.findById(10L)).thenReturn(Optional.of(activeContract()));
        when(repo.save(any(ResourceItem.class))).thenAnswer(invocation -> {
            ResourceItem item = invocation.getArgument(0);
            item.setId(5L);
            item.setCreatedAt(LocalDateTime.now());
            return item;
        });

        ResourceResponse response = resourceService.createLink(request);

        assertEquals(5L, response.id());
        assertEquals(ResourceType.LINK, response.resourceType());
        assertEquals("https://example.com/brief", response.url());
    }

    @Test
    void createLinkRejectsInvalidUrl() {
        ResourceLinkRequest request = new ResourceLinkRequest(
                ResourceEntityType.CONTRACT,
                10L,
                ResourceCategory.REFERENCE,
                "Project brief",
                "ftp://example.com/file"
        );

        when(contractRepository.findById(10L)).thenReturn(Optional.of(activeContract()));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> resourceService.createLink(request)
        );

        assertEquals("Invalid resource URL", exception.getMessage());
        verify(repo, never()).save(any(ResourceItem.class));
    }

    @Test
    void uploadFileStoresFileAndMetadata() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "proof.txt",
                "text/plain",
                "hello".getBytes()
        );

        when(milestoneRepository.findById(20L)).thenReturn(Optional.of(Milestone.builder().id(20L).build()));
        when(repo.save(any(ResourceItem.class))).thenAnswer(invocation -> {
            ResourceItem item = invocation.getArgument(0);
            item.setId(9L);
            item.setCreatedAt(LocalDateTime.now());
            return item;
        });

        ResourceResponse response = resourceService.uploadFile(ResourceEntityType.MILESTONE, 20L, "Proof", "DELIVERABLE", file);

        assertEquals(ResourceType.FILE, response.resourceType());
        assertEquals("proof.txt", response.fileName());
        assertNotNull(response.downloadUrl());
    }

    @Test
    void uploadFileRejectsMissingFile() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> resourceService.uploadFile(ResourceEntityType.MILESTONE, 20L, "Proof", "DELIVERABLE", null)
        );

        assertEquals("A file is required", exception.getMessage());
    }

    @Test
    void listReturnsResourcesForEntity() {
        when(contractRepository.findById(10L)).thenReturn(Optional.of(activeContract()));
        when(repo.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(ResourceEntityType.CONTRACT, 10L))
                .thenReturn(List.of(sampleResourceItem()));

        List<ResourceResponse> result = resourceService.list(ResourceEntityType.CONTRACT, 10L);

        assertEquals(1, result.size());
        assertEquals("Reference doc", result.get(0).label());
    }

    @Test
    void loadFileRejectsNonFileResource() {
        ResourceItem item = sampleResourceItem();
        item.setResourceType(ResourceType.LINK);
        item.setStoragePath(null);

        when(repo.findById(5L)).thenReturn(Optional.of(item));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> resourceService.loadFile(5L)
        );

        assertEquals("Resource is not a file", exception.getMessage());
    }

    @Test
    void loadFileReturnsExistingStoredFile() throws Exception {
        Path file = Files.createFile(tempDir.resolve("stored.txt"));
        Files.writeString(file, "content");
        ResourceItem item = sampleResourceItem();
        item.setResourceType(ResourceType.FILE);
        item.setStoragePath(file.toString());

        when(repo.findById(5L)).thenReturn(Optional.of(item));

        Resource resource = resourceService.loadFile(5L);

        assertNotNull(resource);
        assertEquals(true, resource.exists());
    }

    @Test
    void loadFileRejectsMissingStoredFile() {
        ResourceItem item = sampleResourceItem();
        item.setResourceType(ResourceType.FILE);
        item.setStoragePath(tempDir.resolve("missing.txt").toString());

        when(repo.findById(5L)).thenReturn(Optional.of(item));

        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> resourceService.loadFile(5L)
        );

        assertEquals("Stored file not found for resource: 5", exception.getMessage());
    }

    @Test
    void deleteRemovesMetadataAndStoredFile() throws Exception {
        Path file = Files.createFile(tempDir.resolve("stored-delete.txt"));
        ResourceItem item = sampleResourceItem();
        item.setStoragePath(file.toString());
        item.setResourceType(ResourceType.FILE);

        when(repo.findById(5L)).thenReturn(Optional.of(item));

        resourceService.delete(5L);

        verify(repo).delete(item);
        assertEquals(false, Files.exists(file));
    }

    @Test
    void resolveMediaTypeFallsBackForBlankMimeType() {
        MediaType type = resourceService.resolveMediaType(
                new ResourceResponse(1L, ResourceEntityType.CONTRACT, 10L, ResourceType.FILE, ResourceCategory.BRIEF,
                        "Doc", null, "doc.txt", "", 10L, LocalDateTime.now(), "/download")
        );

        assertEquals(MediaType.APPLICATION_OCTET_STREAM, type);
    }

    @Test
    void resolveMediaTypeParsesValidMimeType() {
        MediaType type = resourceService.resolveMediaType(
                new ResourceResponse(1L, ResourceEntityType.CONTRACT, 10L, ResourceType.FILE, ResourceCategory.BRIEF,
                        "Doc", null, "doc.txt", "text/plain", 10L, LocalDateTime.now(), "/download")
        );

        assertEquals(MediaType.TEXT_PLAIN, type);
    }

    @Test
    void createLinkRejectsCancelledContract() {
        Contract contract = activeContract();
        contract.setStatus(ContractStatus.CANCELLED);
        ResourceLinkRequest request = new ResourceLinkRequest(
                ResourceEntityType.CONTRACT,
                10L,
                ResourceCategory.REFERENCE,
                "Project brief",
                "https://example.com/brief"
        );

        when(contractRepository.findById(10L)).thenReturn(Optional.of(contract));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> resourceService.createLink(request)
        );

        assertEquals("Cannot manage resources on cancelled contracts", exception.getMessage());
    }

    private Contract activeContract() {
        return Contract.builder()
                .id(10L)
                .clientId(3L)
                .freelancerId(8L)
                .title("Website")
                .scope("Build website")
                .totalBudget(new BigDecimal("1000.00"))
                .clientName("Client")
                .freelancerName("Freelancer")
                .status(ContractStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private ResourceItem sampleResourceItem() {
        return ResourceItem.builder()
                .id(5L)
                .entityType(ResourceEntityType.CONTRACT)
                .entityId(10L)
                .resourceType(ResourceType.LINK)
                .category(ResourceCategory.REFERENCE)
                .label("Reference doc")
                .url("https://example.com/doc")
                .createdAt(LocalDateTime.now())
                .build();
    }
}
