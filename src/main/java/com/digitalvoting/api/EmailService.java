package com.digitalvoting.api;

import java.util.Properties;

import com.digitalvoting.config.AppConfig;
import com.digitalvoting.util.ServiceException;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/** Email OTP delivery through JavaMail (Jakarta Mail) over SMTP + STARTTLS. */
public class EmailService {

    public void sendOtp(String to, String otp) throws ServiceException {
        String host = AppConfig.get("smtp.host");
        if (host.isEmpty()) {                                   // dev mode
            System.out.println("[DEV] Email OTP for " + to + " = " + otp);
            return;
        }
        final String user = AppConfig.get("smtp.username");
        final String pass = AppConfig.get("smtp.password");
        String from = AppConfig.get("smtp.from", user);

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", AppConfig.get("smtp.port", "587"));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");        // avoids TLS 1.3 problems with some filters/antivirus
        if ("465".equals(AppConfig.get("smtp.port", "587"))) {
            props.put("mail.smtp.ssl.enable", "true");          // implicit SSL (port 465)
        } else {
            props.put("mail.smtp.starttls.enable", "true");     // STARTTLS (port 587)
        }
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, pass);
            }
        });
        try {
            Message m = new MimeMessage(session);
            m.setFrom(new InternetAddress(from));
            m.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            m.setSubject("Your Digital Voting OTP");
            m.setText("Your one-time password is " + otp + ". It expires in "
                    + AppConfig.getInt("otp.expiry_minutes", 5) + " minutes. Do not share it with anyone.");
            Transport.send(m);
        } catch (MessagingException e) {
            throw new ServiceException("Could not send email OTP: " + e.getMessage(), e);
        }
    }
}
