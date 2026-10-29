package com.dms.api.service;

import com.dms.api.dtos.FormSubmission;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EnquiryMailService {
    private final JavaMailSender sender;
    private final String from;
    private final String to;

    public EnquiryMailService(JavaMailSender sender,
                              @Value("${spring.mail.username}") String from,
                              @Value("${app.mail.to}") String to) {
        this.sender = sender;
        this.from = from;
        this.to = to;
    }


    public void send(FormSubmission submission) throws MailException {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setFrom(from);
        msg.setReplyTo(submission.email());

        String body = "From: " + submission.name() +
                "\nEmail: " + submission.email() +
                "\n" + submission.message();

        msg.setText(body);
        sender.send(msg);
    }
}
