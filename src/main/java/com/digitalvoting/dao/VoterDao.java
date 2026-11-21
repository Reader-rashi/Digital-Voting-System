
package com.digitalvoting.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.digitalvoting.db.Database;
import com.digitalvoting.model.FaceSample;
import com.digitalvoting.model.Models.*;
import com.digitalvoting.model.Models.NewVoter;
import com.digitalvoting.model.Models.Voter;
import com.digitalvoting.model.Models.VoterAuth;
import com.digitalvoting.util.Security;

public class VoterDao {

    private static final String COLS =
            "voter_id, full_name, dob, gender, email, mobile, " +
            "aadhaar_last4, constituency, status, face_enrolled, created_at";

    private Voter map(ResultSet r) throws SQLException {
        return new Voter(
                r.getInt("voter_id"),
                r.getString("full_name"),
                r.getDate("dob").toLocalDate(),
                r.getString("gender"),
                r.getString("email"),
                r.getString("mobile"),
                r.getString("aadhaar_last4"),
                r.getString("constituency"),
                r.getString("status"),
                r.getBoolean("face_enrolled"),
                r.getTimestamp("created_at").toLocalDateTime()
        );
    }

    public boolean exists(
            String aadhaarHash,
            String email,
            String mobile
    ) throws SQLException {

        try (Connection c = Database.get();
             PreparedStatement p = c.prepareStatement(
                     "SELECT 1 FROM voters " +
                     "WHERE aadhaar_hash=? OR email=? OR mobile=? LIMIT 1"
             )) {

            p.setString(1, aadhaarHash);
            p.setString(2, email);
            p.setString(3, mobile);

            try (ResultSet r = p.executeQuery()) {
                return r.next();
            }
        }
    }

    /** Inserts a voter and their face templates in one transaction. */
    public int insert(
            NewVoter v,
            String aadhaarHash,
            String passwordHash,
            String status
    ) throws SQLException {

        try (Connection c = Database.get()) {

            c.setAutoCommit(false);

            try {
                int id;

                String sql =
                        "INSERT INTO voters " +
                        "(full_name,dob,gender,aadhaar_hash,aadhaar_last4," +
                        "email,mobile,constituency,password_hash," +
                        "face_enrolled,status) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?)";

                try (PreparedStatement p = c.prepareStatement(
                        sql,
                        Statement.RETURN_GENERATED_KEYS
                )) {

                    p.setString(1, v.fullName());
                    p.setDate(2, java.sql.Date.valueOf(v.dob()));
                    p.setString(3, v.gender());
                    p.setString(4, aadhaarHash);
                    p.setString(5, v.aadhaar().substring(8));
                    p.setString(6, v.email());
                    p.setString(7, v.mobile());
                    p.setString(8, v.constituency());
                    p.setString(9, passwordHash);
                    p.setBoolean(10, !v.faceImages().isEmpty());
                    p.setString(11, status);

                    p.executeUpdate();

                    try (ResultSet k = p.getGeneratedKeys()) {
                        if (!k.next()) {
                            throw new SQLException(
                                    "Could not obtain generated voter ID."
                            );
                        }

                        id = k.getInt(1);
                    }
                }

                String faceSql =
                        "INSERT INTO face_templates (voter_id,image) " +
                        "VALUES (?,?)";

                try (PreparedStatement p = c.prepareStatement(faceSql)) {

                    for (byte[] img : v.faceImages()) {
                        p.setInt(1, id);
                        p.setBytes(2, img);
                        p.addBatch();
                    }

                    p.executeBatch();
                }

                c.commit();
                return id;

            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public Optional<VoterAuth> findAuth(
            String emailOrMobile
    ) throws SQLException {

        String sql =
                "SELECT " + COLS +
                ", password_hash FROM voters WHERE email=? OR mobile=?";

        try (Connection c = Database.get();
             PreparedStatement p = c.prepareStatement(sql)) {

            p.setString(1, emailOrMobile);
            p.setString(2, emailOrMobile);

            try (ResultSet r = p.executeQuery()) {

                return r.next()
                        ? Optional.of(
                                new VoterAuth(
                                        map(r),
                                        r.getString("password_hash")
                                )
                        )
                        : Optional.empty();
            }
        }
    }

    public List<Voter> list(String statusOrNull) throws SQLException {

        String sql =
                "SELECT " + COLS + " FROM voters" +
                (statusOrNull == null ? "" : " WHERE status=?") +
                " ORDER BY voter_id DESC";

        try (Connection c = Database.get();
             PreparedStatement p = c.prepareStatement(sql)) {

            if (statusOrNull != null) {
                p.setString(1, statusOrNull);
            }

            try (ResultSet r = p.executeQuery()) {

                List<Voter> out = new ArrayList<>();

                while (r.next()) {
                    out.add(map(r));
                }

                return out;
            }
        }
    }

    public void updateStatus(
            int voterId,
            String status
    ) throws SQLException {

        try (Connection c = Database.get();
             PreparedStatement p = c.prepareStatement(
                     "UPDATE voters SET status=? WHERE voter_id=?"
             )) {

            p.setString(1, status);
            p.setInt(2, voterId);
            p.executeUpdate();
        }
    }

    /** Retrieves face templates for one voter. */
    public List<byte[]> faceTemplates(
            int voterId
    ) throws SQLException {

        try (Connection c = Database.get();
             PreparedStatement p = c.prepareStatement(
                     "SELECT image FROM face_templates WHERE voter_id=?"
             )) {

            p.setInt(1, voterId);

            try (ResultSet r = p.executeQuery()) {

                List<byte[]> out = new ArrayList<>();

                while (r.next()) {
                    out.add(r.getBytes(1));
                }

                return out;
            }
        }
    }

    /**
     * Retrieves all face templates belonging to approved,
     * face-enrolled voters, along with their voter IDs.
     */
    public List<FaceSample> allApprovedFaceTemplates()
            throws SQLException {

        String sql = """
                SELECT ft.voter_id, ft.image
                FROM face_templates ft
                INNER JOIN voters v
                    ON v.voter_id = ft.voter_id
                WHERE v.status = 'APPROVED'
                  AND v.face_enrolled = TRUE
                ORDER BY ft.voter_id
                """;

        try (Connection c = Database.get();
             PreparedStatement p = c.prepareStatement(sql);
             ResultSet r = p.executeQuery()) {

            List<FaceSample> samples = new ArrayList<>();

            while (r.next()) {
                samples.add(
                        new FaceSample(
                                r.getInt("voter_id"),
                                r.getBytes("image")
                        )
                );
            }

            return samples;
        }
    }

    public Map<String, Integer> countByStatus()
            throws SQLException {

        try (Connection c = Database.get();
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery(
                     "SELECT status, COUNT(*) " +
                     "FROM voters GROUP BY status"
             )) {

            Map<String, Integer> m = new LinkedHashMap<>();

            while (r.next()) {
                m.put(r.getString(1), r.getInt(2));
            }

            return m;
        }
    }

    public static String hashAadhaar(
            String aadhaar,
            String pepper
    ) {
        return Security.sha256(pepper + ":" + aadhaar);
    }
}
