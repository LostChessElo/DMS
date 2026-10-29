package com.dms.api;

import com.dms.api.dtos.FormSubmission;
import com.dms.api.service.EnquiryMailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EnquiryMailServiceTest {

    @Mock
    JavaMailSender mailSender;

    @Test
    void sendsToCompanyInboxWithReplyToCustomer() {
        EnquiryMailService service = new EnquiryMailService(mailSender, "noreply@dms.co.za", "info@dms.co.za");
        FormSubmission form = new FormSubmission("Thandi", "thandi@example.com", "Need a quote please", true);

        service.send(form);

        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        var sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("info@dms.co.za");
        assertThat(sent.getReplyTo()).isEqualTo("thandi@example.com");
        assertThat(sent.getText()).contains("Thandi", "Need a quote please");
    }
}