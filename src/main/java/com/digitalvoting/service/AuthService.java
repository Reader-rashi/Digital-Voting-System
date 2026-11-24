package com.digitalvoting.service;

import java.sql.SQLException;

import com.digitalvoting.api.FaceService;
import com.digitalvoting.dao.AdminDao;
import com.digitalvoting.dao.AuditDao;
import com.digitalvoting.dao.VoterDao;
import com.digitalvoting.model.Models.*;
import com.digitalvoting.model.Models.Admin;
import com.digitalvoting.model.Models.AdminAuth;
import com.digitalvoting.model.Models.Voter;
import com.digitalvoting.model.Models.VoterAuth;
import com.digitalvoting.util.Security;
import com.digitalvoting.util.ServiceException;

public class AuthService {
    public static final String PURPOSE_LOGIN = "LOGIN";

    private final VoterDao voters = new VoterDao();
    private final AdminDao admins = new AdminDao();
    private final AuditDao audit = new AuditDao();
    private final FaceService face = new FaceService();

    /** Step 1 of voter login: credentials. */
    public Voter voterLogin(String emailOrMobile, String password) throws ServiceException {
        try {
            VoterAuth a = voters.findAuth(emailOrMobile.trim()).orElse(null);
            if (a == null || !Security.checkPassword(password, a.passwordHash())) {
                audit.log("voter:" + emailOrMobile, "LOGIN_FAILED", null);
                throw new ServiceException("Invalid credentials.");
            }
            switch (a.voter().status()) {
                case "PENDING" -> throw new ServiceException("Your registration is awaiting admin approval.");
                case "REJECTED" -> throw new ServiceException("Your registration was rejected.");
                case "BLOCKED" -> throw new ServiceException("Your account is blocked. Contact the election office.");
                default -> { }
            }
            return a.voter();
        } catch (SQLException e) { throw new ServiceException("Database error: " + e.getMessage(), e); }
    }

    /** Step 3 of voter login: face match against enrolled samples. */
    
public void verifyFace(Voter voter, byte[] probePng)
        throws ServiceException {

    try {
        var samples = voters.allApprovedFaceTemplates();

        boolean matched = face.verify(
            voter.id(),
            probePng,
            samples
        );

        if (!matched) {
            audit.log(
                "voter:" + voter.id(),
                "FACE_MISMATCH",
                null
            );

            throw new ServiceException(
                "Face did not match. Try again in good lighting."
            );
        }

        audit.log(
            "voter:" + voter.id(),
            "LOGIN_OK",
            null
        );

    } catch (SQLException e) {
        throw new ServiceException(
            "Database error: " + e.getMessage(),
            e
        );
    }
}

    public Admin adminLogin(String username, String password) throws ServiceException {
        try {
            AdminAuth a = admins.findAuth(username.trim()).orElse(null);
            if (a == null || !Security.checkPassword(password, a.passwordHash())) {
                audit.log("admin:" + username, "ADMIN_LOGIN_FAILED", null);
                throw new ServiceException("Invalid admin credentials.");
            }
            audit.log("admin:" + username, "ADMIN_LOGIN_OK", null);
            return a.admin();
        } catch (SQLException e) { throw new ServiceException("Database error: " + e.getMessage(), e); }
    }
}