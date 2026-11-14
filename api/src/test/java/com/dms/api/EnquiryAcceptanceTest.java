package com.dms.api;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class EnquiryAcceptanceTest {

    private static final String PATH = "/api/v1/enquiries";

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired
    RestTestClient client;

    private RestTestClient.ResponseSpec post(String body) {
        return client.post().uri(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    @Test
    void validEnquiryReturns201AndEmailsTheCompany() throws Exception {
        post("""
                {"name":"Jon Snow","email":"jonsnow@nightwatch.com",
                 "message":"We need a quote for a new dragon.","consent":true}
                """)
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.status").exists();

        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage mail = greenMail.getReceivedMessages()[0];

        assertThat(mail.getAllRecipients()[0].toString()).isEqualTo("owner@dms.test");
        assertThat(mail.getFrom()[0].toString()).isEqualTo("noreply@dms.test");
        assertThat(mail.getReplyTo()[0].toString()).isEqualTo("jonsnow@nightwatch.com");
        assertThat(mail.getSubject()).isEqualTo("New message from: Jon Snow");
        assertThat(GreenMailUtil.getBody(mail))
                .contains("Jon Snow", "a new dragon");
    }

    @Test
    void invalidEnquiryReturns400WithFieldErrorsAndSendsNothing() {
        post("""
                {"name":"","email":"not-an-email","message":"","consent":false}
                """)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.fieldErrors.name").isEqualTo("Name is required")
                .jsonPath("$.fieldErrors.consent").isEqualTo("Consent is required");

        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void malformedJsonReturns400WithTheStandardBody() {
        post("{ \"name\": ")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.timestamp").exists();

        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void unknownPathReturns404WithTheStandardBody() {
        client.get().uri("/api/v1/nope").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.status").isEqualTo(404);
    }
}