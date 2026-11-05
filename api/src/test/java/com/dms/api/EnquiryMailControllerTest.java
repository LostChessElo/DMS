package com.dms.api;

import com.dms.api.controllers.EnquiryMailController;
import com.dms.api.dtos.FormSubmission;
import com.dms.api.service.EnquiryMailService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.htmlunit.MockMvcWebClientBuilder;

import java.lang.reflect.ParameterizedType;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(EnquiryMailController.class)
@Disabled
public class EnquiryMailControllerTest {
    private static final String PATH = "/api/v1/enquire";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    EnquiryMailService mailService;
    @Autowired
    private MockMvcWebClientBuilder mockMvcWebClientBuilder;

    private static String json(String name, String email, String message, boolean consent) {
        return """
                {"name":"%s","email":"%s","message":"%s","consent":%s}
                """.formatted(name, email, message, consent);
    }

    @Test
    void validRequestReturns201() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Test", "test@test.com", "Need a quote please", true)))
                .andExpect(status().isCreated());

        verify(mailService).send(any(FormSubmission.class));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        String malformedJson = """
                {"name": "%s", "email": "%s"}
                """.formatted("Dumb user", "dummy@dumb.com");

        mockMvc.perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(malformedJson)).andExpect(status().isBadRequest());

        verify(mailService, never()).send(any(FormSubmission.class));
    }

    @Test
    void invalidNameReturns400() throws Exception {
        mockMvc.perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("", "test@test.com", "Need a quote", true)))
                .andExpect(status().isBadRequest());

        verify(mailService, never()).send(any(FormSubmission.class));
    }

    @Test
    void nameTooLongReturns400() throws Exception{
        String nameTooLong = "A".repeat(101);

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(nameTooLong, "test@test.com", "Need a quote", true)))
                .andExpect(status().isBadRequest());

        verify(mailService, never()).send(any(FormSubmission.class));
    }

    @Test
    void invalidEmailReturns400() throws Exception{
        String[] invalidEmails = {
                "test",
                "test@",
                "test@test",
                "test@test."
        };

        for (String email: invalidEmails) {
            mockMvc.perform(post(PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("Tester", email, "Need a quote",true )))
                    .andExpect(status().isBadRequest());

            verify(mailService, never()).send(any(FormSubmission.class));
        }
    }

    @Test
    void noConsentReturns400() throws Exception{
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Mai", "test@test.com", "Need a quote", false)))
                .andExpect(status().isBadRequest());

        verify(mailService, never()).send(any(FormSubmission.class));
    }
}
