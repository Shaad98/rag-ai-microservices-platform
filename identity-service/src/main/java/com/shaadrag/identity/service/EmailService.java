package com.shaadrag.identity.service;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class EmailService {

    private final JavaMailSender javaMailSender;

    @Value("${user.email}")
    private String from;

    public void sendEmail(
            String to,
            String subject,
            String body) {

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setTo(to);
        message.setFrom(from);
        message.setSubject(subject);
        message.setText(body);

        javaMailSender.send(message);

        log.info("Email sent successfully to {}", to);
    }

    public void sendMailToAll(
            String[] to,
            String subject,
            String body) {

        SimpleMailMessage simpleMailMessage =
                new SimpleMailMessage();

        simpleMailMessage.setTo(to);
        simpleMailMessage.setFrom(from);
        simpleMailMessage.setText(body);
        simpleMailMessage.setSubject(subject);

        javaMailSender.send(simpleMailMessage);

        log.info("Email sent successfully to multiple recipients");
    }

    @Async("emailTaskExecutor")
    public void sendHTMLInEmail(
            String to,
            String subject,
            String body) {

        try {

            MimeMessage mailMessage =
                    javaMailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(
                            mailMessage,
                            true);

            helper.setText(body, true);
            helper.setTo(to);
            helper.setFrom(from);
            helper.setSubject(subject);

            javaMailSender.send(mailMessage);

            log.info(
                    "HTML email sent successfully to {}",
                    to);

        } catch (MessagingException | RuntimeException ex) {

            log.error(
                    "Failed to send HTML email to {}",
                    to,
                    ex);
        }
    }

    public void sendFileViaMail(
            String to,
            String subject,
            String body,
            MultipartFile file)
            throws MessagingException, IOException {

        MimeMessage message =
                javaMailSender.createMimeMessage();

        MimeMessageHelper helper =
                new MimeMessageHelper(
                        message,
                        true);

        helper.setText(body);
        helper.setTo(to);
        helper.setFrom(from);
        helper.setSubject(subject);

        helper.addAttachment(
                file.getOriginalFilename(),
                new ByteArrayResource(file.getBytes()));

        javaMailSender.send(message);

        log.info(
                "File email sent successfully to {}",
                to);
    }
}