package com.digitalvoting.dao;

import com.digitalvoting.db.Database;

import java.sql.*;
import java.time.LocalDateTime;

public class OtpDao {
    public record Otp(long id, String hash, int attempts) {}

    public void save(String target, String purpose, String hash, LocalDateTime expires) throws SQLException {
        try (Connection c = Database.get();
             PreparedStatement p = c.prepareStatement("INSERT INTO otp_requests (target,purpose,otp_hash,expires_at) VALUES (?,?,?,?)")) {
            p.setString(1, target); p.setString(2, purpose); p.setString(3, hash); p.setTimestamp(4, Timestamp.valueOf(expires));
            p.executeUpdate();
        }
    }

    /** Latest unused, unexpired OTP or null. */
    public Otp latestActive(String target, String purpose) throws SQLException {
        try (Connection c = Database.get();
             PreparedStatement p = c.prepareStatement("SELECT id,otp_hash,attempts FROM otp_requests WHERE target=? AND purpose=? AND used=0 AND expires_at>? ORDER BY id DESC LIMIT 1")) {
            p.setString(1, target); p.setString(2, purpose); p.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            try (ResultSet r = p.executeQuery()) { return r.next() ? new Otp(r.getLong(1), r.getString(2), r.getInt(3)) : null; }
        }
    }

    public void addAttempt(long id) throws SQLException {
        try (Connection c = Database.get(); PreparedStatement p = c.prepareStatement("UPDATE otp_requests SET attempts=attempts+1 WHERE id=?")) {
            p.setLong(1, id); p.executeUpdate();
        }
    }

    public void markUsed(long id) throws SQLException {
        try (Connection c = Database.get(); PreparedStatement p = c.prepareStatement("UPDATE otp_requests SET used=1, verified_at=? WHERE id=?")) {
            p.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now())); p.setLong(2, id); p.executeUpdate();
        }
    }

    public boolean verifiedSince(String target, String purpose, LocalDateTime since) throws SQLException {
        try (Connection c = Database.get();
             PreparedStatement p = c.prepareStatement("SELECT 1 FROM otp_requests WHERE target=? AND purpose=? AND used=1 AND verified_at>? LIMIT 1")) {
            p.setString(1, target); p.setString(2, purpose); p.setTimestamp(3, Timestamp.valueOf(since));
            try (ResultSet r = p.executeQuery()) { return r.next(); }
        }
    }
}
