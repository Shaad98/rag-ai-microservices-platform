package com.shaadrag.identity.service;


import java.io.IOException;

// import org.apache.tomcat.util.http.MimeHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
// import org.springframework.mail.javamail.MimeMailMessage;
// import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class EmailService {

    private final JavaMailSender javaMailSender;

    @Value("${user.email}")
    private  String from;

    public void sendEmail(String to,String subject,String body)
    {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setFrom(from);
        message.setSubject(subject);
        message.setText(body);
        javaMailSender.send(message);
        System.out.println("Mail send successfully!");
    }
    
    public void sendMailToAll(String[] to,String subject,String body)
    {
        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setTo(to);
        simpleMailMessage.setFrom(from);
        simpleMailMessage.setText(body);
        simpleMailMessage.setSubject(subject);

        javaMailSender.send(simpleMailMessage);
    }

    public void sendHTMLInEmail(String to,String subject,String body) throws MessagingException
    {
        MimeMessage mailMessage = javaMailSender.createMimeMessage();

        MimeMessageHelper helper = new MimeMessageHelper(mailMessage,true);

        helper.setText(body, true);
        helper.setTo(to);
        helper.setFrom(from);
        helper.setSubject(subject);

        javaMailSender.send(mailMessage);
        // mailMessage.setText(body);
        // mailMessage.setC
    }

    public void sendFileViaMail(String to,String subject,String body,MultipartFile file) throws MessagingException, IOException
    {
        MimeMessage message =  javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message,true); // enable multipart

        helper.setText(body);
        helper.setTo(to);
        helper.setFrom(from);
        helper.setSubject(subject);
        helper.addAttachment(file.getOriginalFilename(), new ByteArrayResource(file.getBytes()));
        
        javaMailSender.send(message);
    }
}
