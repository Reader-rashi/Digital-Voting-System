package com.digitalvoting.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Plain data holders shared by DAOs, services and UI. */
public final class Models {
    private Models() {}

    public record Voter(int id, String fullName, LocalDate dob, String gender, String email, String mobile,
                        String aadhaarLast4, String constituency, String status, boolean faceEnrolled,
                        LocalDateTime createdAt) {}

    public record VoterAuth(Voter voter, String passwordHash) {}

    public record NewVoter(String fullName, LocalDate dob, String gender, String aadhaar, String email,
                           String mobile, String constituency, String password, List<byte[]> faceImages) {}

    public record Admin(int id, String username, String fullName, Role role) {}

    public record AdminAuth(Admin admin, String passwordHash) {}

    public record Election(int id, String title, LocalDateTime start, LocalDateTime end, boolean resultsDeclared) {
        public boolean isActive(LocalDateTime now) { return !now.isBefore(start) && now.isBefore(end); }
        public boolean isFinished(LocalDateTime now) { return !now.isBefore(end); }
        @Override public String toString() { return title; }
    }

    public record Party(int id, String name, String symbol) {
        @Override public String toString() { return name; }
    }

    public record Candidate(int id, int electionId, String name, Integer partyId, String partyName, String symbol) {}

    public record ResultRow(String candidate, String party, int votes) {}

    public record VoteReceipt(String code, String electionTitle, LocalDateTime castAt) {}

    public record Turnout(int eligible, int voted) {}
}
