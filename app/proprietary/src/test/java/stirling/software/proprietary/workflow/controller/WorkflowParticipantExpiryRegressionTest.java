package stirling.software.proprietary.workflow.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import stirling.software.proprietary.security.model.User;
import stirling.software.proprietary.storage.model.ShareAccessRole;
import stirling.software.proprietary.workflow.model.WorkflowParticipant;
import stirling.software.proprietary.workflow.repository.WorkflowParticipantRepository;
import stirling.software.proprietary.workflow.service.CertificateSubmissionValidator;
import stirling.software.proprietary.workflow.service.MetadataEncryptionService;
import stirling.software.proprietary.workflow.service.WorkflowSessionService;

import tools.jackson.databind.ObjectMapper;

class WorkflowParticipantExpiryRegressionTest {

    @Test
    void expiredShareTokenStillReturnsParticipantDetails() throws Exception {
        WorkflowSessionService sessionService = mock(WorkflowSessionService.class);
        WorkflowParticipantRepository repository = mock(WorkflowParticipantRepository.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        MetadataEncryptionService encryptionService = mock(MetadataEncryptionService.class);
        CertificateSubmissionValidator certificateValidator =
                mock(CertificateSubmissionValidator.class);

        WorkflowParticipant participant = new WorkflowParticipant();
        participant.setId(41L);
        participant.setShareToken("expired-test-token");
        participant.setEmail("participant@example.invalid");
        participant.setName("Expired participant");
        participant.setAccessRole(ShareAccessRole.EDITOR);
        participant.setExpiresAt(LocalDateTime.now().minusMinutes(10));
        when(repository.findByShareToken("expired-test-token"))
                .thenReturn(Optional.of(participant));

        WorkflowParticipantController controller =
                new WorkflowParticipantController(
                        sessionService,
                        repository,
                        objectMapper,
                        encryptionService,
                        certificateValidator,
                        mock(org.springframework.context.ApplicationEventPublisher.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        // This is an intentional reproduction assertion: current code returns HTTP 200 and marks
        // the participant expired, instead of rejecting the expired bearer token with HTTP 403.
        mvc.perform(get("/api/v1/workflow/participant/details")
                        .param("token", "expired-test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expired").value(true))
                .andExpect(jsonPath("$.shareToken").doesNotExist());
    }
}
