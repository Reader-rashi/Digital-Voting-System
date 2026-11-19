package com.digitalvoting.service;

import com.digitalvoting.api.EmailService;
import com.digitalvoting.api.SmsService;
import com.digitalvoting.config.AppConfig;
import com.digitalvoting.dao.OtpDao;
import com.digitalvoting.util.Security;
import com.digitalvoting.util.ServiceException;

import java.sql.SQLException;
import java.time.LocalDateTime;

public class OtpService {
    public enum Channel { EMAIL, SMS }

    private final OtpDao dao = new OtpDao();
    private final EmailService email = new EmailService();
    private final SmsService sms = new SmsService();

    /** Generates, stores (hashed) and delivers an OTP. */
    public void send(Channel channel, String target, String purpose) throws ServiceException {
        String otp = Security.randomOtp();
        try {
            dao.save(target, purpose, Security.sha256(otp + ":" + target), LocalDateTime.now().plusMinutes(AppConfig.getInt("otp.expiry_minutes", 5)));
        } catch (SQLException e) {
            throw new ServiceException("Database error while creating OTP.", e);
        }
        if (channel == Channel.EMAIL) email.sendOtp(target, otp); else sms.sendOtp(target, otp);
    }

    /** Returns normally if the code is correct; throws ServiceException otherwise. */
    public void verify(String target, String purpose, String code) throws ServiceException {
        try {
            OtpDao.Otp otp = dao.latestActive(target, purpose);
            if (otp == null) throw new ServiceException("OTP expired or not requested. Please request a new one.");
            if (otp.attempts() >= AppConfig.getInt("otp.max_attempts", 5)) throw new ServiceException("Too many wrong attempts. Please request a new OTP.");
            dao.addAttempt(otp.id());
            boolean ok = java.security.MessageDigest.isEqual(otp.hash().getBytes(), Security.sha256(code.trim() + ":" + target).getBytes());
            if (!ok) throw new ServiceException("Incorrect OTP.");
            dao.markUsed(otp.id());
        } catch (SQLException e) {
            throw new ServiceException("Database error while verifying OTP.", e);
        }
    }

    public boolean wasVerifiedRecently(String target, String purpose, int minutes) throws ServiceException {
        try { return dao.verifiedSince(target, purpose, LocalDateTime.now().minusMinutes(minutes)); }
        catch (SQLException e) { throw new ServiceException("Database error.", e); }
    }
}
