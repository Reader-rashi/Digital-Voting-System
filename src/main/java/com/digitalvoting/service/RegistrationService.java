package com.digitalvoting.service;

import com.digitalvoting.api.AadhaarService;
import com.digitalvoting.config.AppConfig;
import com.digitalvoting.dao.AuditDao;
import com.digitalvoting.dao.VoterDao;
import com.digitalvoting.model.Models.NewVoter;
import com.digitalvoting.util.*;

import java.sql.SQLException;

public class RegistrationService {
    public static final String PURPOSE_EMAIL = "REG_EMAIL", PURPOSE_MOBILE = "REG_MOBILE";

    private final VoterDao voters = new VoterDao();
    private final AuditDao audit = new AuditDao();
    private final OtpService otp = new OtpService();
    private final AadhaarService aadhaar = new AadhaarService();

    /** Step 1 check: field validation + Aadhaar verification + duplicate check. */
    public void validateStep1(NewVoter v) throws ServiceException {
        if (v.fullName().isBlank() || v.constituency().isBlank()) throw new ServiceException("Name and constituency are required.");
        if (!Validators.adult(v.dob())) throw new ServiceException("You must be at least 18 years old.");
        if (!Validators.email(v.email())) throw new ServiceException("Enter a valid email address.");
        if (!Validators.mobile(v.mobile())) throw new ServiceException("Enter a valid 10-digit mobile number.");
        aadhaar.verify(v.aadhaar(), v.fullName());
        try {
            if (voters.exists(hash(v.aadhaar()), v.email(), v.mobile()))
                throw new ServiceException("A voter with this Aadhaar, email or mobile is already registered.");
        } catch (SQLException e) { throw new ServiceException("Database error: " + e.getMessage(), e); }
    }

    /** Final step: re-checks everything server-side, then stores the voter. */
    public int register(NewVoter v) throws ServiceException {
        validateStep1(v);
        if (!Validators.strongPassword(v.password())) throw new ServiceException("Password must be 8+ characters with letters and digits.");
        if (v.faceImages().size() < AppConfig.getInt("face.min_samples", 3)) throw new ServiceException("Capture at least " + AppConfig.getInt("face.min_samples", 3) + " face samples.");
        if (!otp.wasVerifiedRecently(v.email(), PURPOSE_EMAIL, 30)) throw new ServiceException("Email is not verified.");
        if (!otp.wasVerifiedRecently(v.mobile(), PURPOSE_MOBILE, 30)) throw new ServiceException("Mobile number is not verified.");
        try {
            String status = AppConfig.getBool("registration.auto_approve", false) ? "APPROVED" : "PENDING";
            int id = voters.insert(v, hash(v.aadhaar()), Security.hashPassword(v.password()), status);
            audit.log("voter:" + id, "REGISTER", "status=" + status);
            return id;
        } catch (SQLException e) { throw new ServiceException("Could not save registration: " + e.getMessage(), e); }
    }

    private static String hash(String aadhaarNo) { return VoterDao.hashAadhaar(aadhaarNo, AppConfig.get("security.pepper", "")); }
}
