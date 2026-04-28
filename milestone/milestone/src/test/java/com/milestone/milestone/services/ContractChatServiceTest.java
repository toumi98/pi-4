package com.milestone.milestone.services;

import com.milestone.milestone.dto.ChatLinkMessageRequest;
import com.milestone.milestone.dto.ChatMessageRequest;
import com.milestone.milestone.dto.ChatMessageResponse;
import com.milestone.milestone.dto.ContractCallRequest;
import com.milestone.milestone.dto.TypingEvent;
import com.milestone.milestone.exception.NotFoundException;
import com.milestone.milestone.models.ChatAttachmentType;
import com.milestone.milestone.models.Contract;
import com.milestone.milestone.models.ContractMessage;
import com.milestone.milestone.models.ContractStatus;
import com.milestone.milestone.models.MessageSenderRole;
import com.milestone.milestone.repositories.ContractMessageRepository;
import com.milestone.milestone.repositories.ContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractChatServiceTest {

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private ContractMessageRepository messageRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ContractChatService contractChatService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(contractChatService, "uploadsRoot", tempDir.toString());
    }

    @Test
    void historyReturnsMessagesForActiveContract() {
        when(contractRepository.findById(10L)).thenReturn(Optional.of(activeContract()));
        when(messageRepository.findByContractIdOrderBySentAtAsc(10L)).thenReturn(List.of(sampleMessage()));

        List<ChatMessageResponse> result = contractChatService.history(10L);

        assertEquals(1, result.size());
        assertEquals("Hello", result.get(0).content());
    }

    @Test
    void sendStoresNormalizedMessage() {
        when(contractRepository.findById(10L)).thenReturn(Optional.of(activeContract()));
        when(messageRepository.save(any(ContractMessage.class))).thenAnswer(invocation -> {
            ContractMessage message = invocation.getArgument(0);
            message.setId(5L);
            message.setSentAt(LocalDateTime.now());
            return message;
        });

        ChatMessageResponse response = contractChatService.send(
                10L,
                new ChatMessageRequest(3L, MessageSenderRole.CLIENT, "Client User", "  Hello  ")
        );

        assertEquals(5L, response.id());
        assertEquals("Hello", response.content());
    }

    @Test
    void sendRejectsBlankContent() {
        when(contractRepository.findById(10L)).thenReturn(Optional.of(activeContract()));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> contractChatService.send(
                        10L,
                        new ChatMessageRequest(3L, MessageSenderRole.CLIENT, "Client User", "   ")
                )
        );

        assertEquals("Message content is required", exception.getMessage());
    }

    @Test
    void sendLinkStoresLinkAttachment() {
        when(contractRepository.findById(10L)).thenReturn(Optional.of(activeContract()));
        when(messageRepository.save(any(ContractMessage.class))).thenAnswer(invocation -> {
            ContractMessage message = invocation.getArgument(0);
            message.setId(6L);
            message.setSentAt(LocalDateTime.now());
            return message;
        });

        ChatMessageResponse response = contractChatService.sendLink(
                10L,
                new ChatLinkMessageRequest(3L, MessageSenderRole.CLIENT, "Client User", "See link", "Reference", "https://example.com")
        );

        assertEquals(ChatAttachmentType.LINK, response.attachmentType());
        assertEquals("https://example.com", response.attachmentUrl());
    }

    @Test
    void sendLinkRejectsInvalidUrl() {
        when(contractRepository.findById(10L)).thenReturn(Optional.of(activeContract()));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> contractChatService.sendLink(
                        10L,
                        new ChatLinkMessageRequest(3L, MessageSenderRole.CLIENT, "Client User", "See link", "Reference", "ftp://example.com")
                )
        );

        assertEquals("Invalid chat link", exception.getMessage());
    }

    @Test
    void sendFileStoresAttachment() {
        MockMultipartFile file = new MockMultipartFile("file", "proof.txt", "text/plain", "hello".getBytes());

        when(contractRepository.findById(10L)).thenReturn(Optional.of(activeContract()));
        when(messageRepository.save(any(ContractMessage.class))).thenAnswer(invocation -> {
            ContractMessage message = invocation.getArgument(0);
            message.setId(7L);
            message.setSentAt(LocalDateTime.now());
            return message;
        });

        ChatMessageResponse response = contractChatService.sendFile(
                10L,
                3L,
                MessageSenderRole.CLIENT,
                "Client User",
                "Proof attached",
                "Attachment",
                file
        );

        assertEquals(ChatAttachmentType.FILE, response.attachmentType());
        assertEquals("proof.txt", response.attachmentFileName());
        assertNotNull(response.attachmentDownloadUrl());
    }

    @Test
    void sendFileRejectsMissingFile() {
        when(contractRepository.findById(10L)).thenReturn(Optional.of(activeContract()));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> contractChatService.sendFile(10L, 3L, MessageSenderRole.CLIENT, "Client User", "x", "Attachment", null)
        );

        assertEquals("A file is required", exception.getMessage());
    }

    @Test
    void typingAcceptsValidParticipant() {
        when(contractRepository.findById(10L)).thenReturn(Optional.of(activeContract()));

        contractChatService.typing(new TypingEvent(10L, 3L, MessageSenderRole.CLIENT, "Client User", true, LocalDateTime.now()));
    }

    @Test
    void announceCallCreatesMeetingLinkMessage() {
        when(contractRepository.findById(10L)).thenReturn(Optional.of(activeContract()));
        when(messageRepository.save(any(ContractMessage.class))).thenAnswer(invocation -> {
            ContractMessage message = invocation.getArgument(0);
            message.setId(8L);
            message.setSentAt(LocalDateTime.now());
            return message;
        });

        contractChatService.announceCall(10L, new ContractCallRequest(3L, MessageSenderRole.CLIENT, "Client User"));
    }

    @Test
    void loadAttachmentReturnsExistingFile() throws Exception {
        Path file = Files.createFile(tempDir.resolve("stored-chat.txt"));
        Files.writeString(file, "content");
        ContractMessage message = sampleMessage();
        message.setAttachmentType(ChatAttachmentType.FILE);
        message.setAttachmentStoragePath(file.toString());

        when(messageRepository.findById(5L)).thenReturn(Optional.of(message));

        Resource resource = contractChatService.loadAttachment(10L, 5L);

        assertEquals(true, resource.exists());
    }

    @Test
    void loadAttachmentRejectsNonFileMessage() {
        ContractMessage message = sampleMessage();
        message.setAttachmentType(ChatAttachmentType.LINK);
        message.setAttachmentStoragePath(null);

        when(messageRepository.findById(5L)).thenReturn(Optional.of(message));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> contractChatService.loadAttachment(10L, 5L)
        );

        assertEquals("Chat message does not contain a file", exception.getMessage());
    }

    @Test
    void loadAttachmentRejectsMissingStoredFile() {
        ContractMessage message = sampleMessage();
        message.setAttachmentType(ChatAttachmentType.FILE);
        message.setAttachmentStoragePath(tempDir.resolve("missing-chat.txt").toString());

        when(messageRepository.findById(5L)).thenReturn(Optional.of(message));

        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> contractChatService.loadAttachment(10L, 5L)
        );

        assertEquals("Chat attachment file not found: 5", exception.getMessage());
    }

    @Test
    void resolveAttachmentMediaTypeFallsBackForBlankMimeType() {
        MediaType type = contractChatService.resolveAttachmentMediaType(
                new ChatMessageResponse(5L, 10L, 3L, MessageSenderRole.CLIENT, "Client User", "Hello", LocalDateTime.now(),
                        ChatAttachmentType.FILE, "Attachment", null, "file.txt", "", 12L, "/download")
        );

        assertEquals(MediaType.APPLICATION_OCTET_STREAM, type);
    }

    @Test
    void getMessageReturnsResponse() {
        when(messageRepository.findById(5L)).thenReturn(Optional.of(sampleMessage()));

        ChatMessageResponse response = contractChatService.getMessage(10L, 5L);

        assertEquals(5L, response.id());
        assertNull(response.attachmentType());
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

    private ContractMessage sampleMessage() {
        return ContractMessage.builder()
                .id(5L)
                .contractId(10L)
                .senderId(3L)
                .senderRole(MessageSenderRole.CLIENT)
                .senderName("Client User")
                .content("Hello")
                .sentAt(LocalDateTime.now())
                .build();
    }
}
