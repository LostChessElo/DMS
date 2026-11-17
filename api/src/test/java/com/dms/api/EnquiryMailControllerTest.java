package com.dms.api;

import com.dms.api.controllers.EnquiryMailController;
import com.dms.api.dtos.FormSubmission;
import com.dms.api.service.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EnquiryMailController.class)
public class EnquiryMailControllerTest {
    private static final String PATH = "/api/v1/enquiries";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    IdempotencyService idempotencyService;

    private static String json(String name, String email, String message, boolean consent) {
        return """
                {"name":"%s","email":"%s","message":"%s","consent":%s}
                """.formatted(name, email, message, consent);
    }

    private ResultActions mockPost(String uuid, String path, String jsonBody) throws Exception {
        return mockMvc.perform(post(path)
                .header("Idempotency-Key", uuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody));
    }

    @Test
    void validRequestReturns201() throws Exception {
        String uuid = UUID.randomUUID().toString();

        mockPost(uuid, PATH,
                json("Test", "test@test.com",
                        "Need a quote please", true))
                .andExpect(status().isCreated());

        verify(idempotencyService).process(eq(uuid), any(FormSubmission.class));
    }

    @Test
    void missingRequestFieldsReturns400() throws Exception {
        String uuid = UUID.randomUUID().toString();

        String req = """
                {"name": "%s", "email": "%s"}
                """.formatted("Dumb user", "dummy123@dumb.com");

        mockPost(uuid, PATH, req).andExpect(status().isBadRequest());

        verify(idempotencyService, never()).process(eq(uuid), any(FormSubmission.class));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        String uuid = UUID.randomUUID().toString();

        String malformedJson = """
                {"name": "%s", "email":}
                """.formatted("Dumb user");

        mockMvc.perform(post(PATH)
                        .header("Idempotency-Key", uuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest());

        verify(idempotencyService, never()).process(eq(uuid), any(FormSubmission.class));
    }

    @Test
    void emptyBodyReturns400() throws Exception {
        String uuid = UUID.randomUUID().toString();

        mockPost(uuid, PATH, "").andExpect(status().isBadRequest());

        verify(idempotencyService, never()).process(eq(uuid), any(FormSubmission.class));
    }

    @Test
    void invalidNameReturns400() throws Exception {
        String uuid = UUID.randomUUID().toString();

        mockPost(uuid, PATH,
                json("", "test@test.com",
                        "Need a quote", true))
                .andExpect(status().isBadRequest());

        verify(idempotencyService, never()).process(eq(uuid), any(FormSubmission.class));
    }

    @Test
    void nameTooLongReturns400() throws Exception {
        String uuid = UUID.randomUUID().toString();
        String nameTooLong = "A".repeat(101);

        mockPost(uuid, PATH,
                json(nameTooLong, "test@test.com",
                        "Need a quote", true))
                .andExpect(status().isBadRequest());

        verify(idempotencyService, never()).process(eq(uuid), any(FormSubmission.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "test",
            "test@",
            "test@test",
            "test@test."
    })
    void invalidEmailReturns400(String email) throws Exception {
        String uuid = UUID.randomUUID().toString();

        mockPost(uuid, PATH,
                json("Test", email,
                        "Need a quote please", true))
                .andExpect(status().isBadRequest());

        verify(idempotencyService, never()).process(eq(uuid), any(FormSubmission.class));
    }

    @Test
    void blankMessageReturns400() throws Exception {
        String uuid = UUID.randomUUID().toString();

        mockPost(uuid, PATH,
                json("test", "test@test.com", "", true))
                .andExpect(status().isBadRequest());

        verify(idempotencyService, never()).process(eq(uuid), any(FormSubmission.class));
    }

    @Test
    void messageTooLongReturns400() throws Exception {
        String uuid = UUID.randomUUID().toString();
        String message = "A".repeat(2001);

        mockPost(uuid, PATH,
                json("Test", "test@test.com",
                        message, true))
                .andExpect(status().isBadRequest());

        verify(idempotencyService, never()).process(eq(uuid), any(FormSubmission.class));
    }

    @Test
    void noConsentReturns400() throws Exception {
        String uuid = UUID.randomUUID().toString();

        mockPost(uuid, PATH,
                json("Test", "test@test.com",
                        "Need a quote please", false))
                .andExpect(status().isBadRequest());

        verify(idempotencyService, never()).process(eq(uuid), any(FormSubmission.class));
    }

    @Test
    void wrongContentTypeReturns415() throws Exception {
        String uuid = UUID.randomUUID().toString();

        String req = """
                <?xml version="1.0" encoding="utf-8"?>
                <entry xmlns="http://www.w3.org/2005/Atom">
                  <title>Website enquiry</title>
                  <id>urn:uuid:60a76c80-d399-11d9-b93C-0003939e0af6</id>
                  <updated>2026-11-07T10:00:00Z</updated>
                  <author><name>Test Bot</name></author>
                  <content>Need a quote please</content>
                </entry>
                """;

        mockMvc.perform(post(PATH)
                        .header("Idempotency-Key", uuid)
                        .contentType(MediaType.APPLICATION_ATOM_XML)
                        .content(req))
                .andExpect(status().isUnsupportedMediaType());

        verify(idempotencyService, never()).process(eq(uuid), any(FormSubmission.class));
    }

    @Test
    void getOnEndpointReturns405() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(idempotencyService);
    }

    @Test
    void deleteOnEndpointReturns405() throws Exception {
        mockMvc.perform(delete(PATH))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(idempotencyService);
    }

    @Test
    void putOnEndpointReturns405() throws Exception {
        mockMvc.perform(put(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("test", "test@test.com", "testing", true)))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(idempotencyService);
    }

    @Test
    void patchOnEndpointReturns405() throws Exception {
        mockMvc.perform(patch(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("test", "test@test.com", "testing", true)))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(idempotencyService);
    }

    @Test
    void unknownEndpointReturns404() throws Exception {
        String uuid = UUID.randomUUID().toString();

        mockPost(uuid, PATH + "/unknown",
                json("Test", "test@test.com",
                        "Need a quote please", true))
                .andExpect(status().isNotFound());

        verify(idempotencyService, never()).process(eq(uuid), any(FormSubmission.class));
    }
}