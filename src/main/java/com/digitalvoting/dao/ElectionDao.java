package com.digitalvoting.dao;

import com.digitalvoting.db.Database;
import com.digitalvoting.model.Models.*;
import com.digitalvoting.util.Security;
import com.digitalvoting.util.ServiceException;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class ElectionDao {
    private Election map(ResultSet r) throws SQLException {
        return new Election(r.getInt("election_id"), r.getString("title"), r.getTimestamp("start_time").toLocalDateTime(),
                r.getTimestamp("end_time").toLocalDateTime(), r.getBoolean("results_declared"));
    }

    // ---------- elections ----------
    public int createElection(String title, LocalDateTime start, LocalDateTime end) throws SQLException {
        try (Connection c = Database.get();
             PreparedStatement p = c.prepareStatement("INSERT INTO elections (title,start_time,end_time) VALUES (?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, title); p.setTimestamp(2, Timestamp.valueOf(start)); p.setTimestamp(3, Timestamp.valueOf(end));
            p.executeUpdate();
            try (ResultSet k = p.getGeneratedKeys()) { k.next(); return k.getInt(1); }
        }
    }

    public List<Election> listElections() throws SQLException {
        try (Connection c = Database.get(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM elections ORDER BY start_time DESC")) {
            List<Election> out = new ArrayList<>();
            while (r.next()) out.add(map(r));
            return out;
        }
    }

    public Optional<Election> activeElection() throws SQLException {
        try (Connection c = Database.get();
             PreparedStatement p = c.prepareStatement("SELECT * FROM elections WHERE start_time <= ? AND end_time > ? ORDER BY start_time DESC LIMIT 1")) {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            p.setTimestamp(1, now); p.setTimestamp(2, now);
            try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(map(r)) : Optional.empty(); }
        }
    }

    public Optional<Election> latestElection() throws SQLException {
        List<Election> all = listElections();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    public void setResultsDeclared(int electionId, boolean declared) throws SQLException {
        try (Connection c = Database.get(); PreparedStatement p = c.prepareStatement("UPDATE elections SET results_declared=? WHERE election_id=?")) {
            p.setBoolean(1, declared); p.setInt(2, electionId); p.executeUpdate();
        }
    }

    // ---------- parties ----------
    public List<Party> listParties() throws SQLException {
        try (Connection c = Database.get(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM parties ORDER BY name")) {
            List<Party> out = new ArrayList<>();
            while (r.next()) out.add(new Party(r.getInt("party_id"), r.getString("name"), r.getString("symbol")));
            return out;
        }
    }

    public void addParty(String name, String symbol) throws SQLException {
        try (Connection c = Database.get(); PreparedStatement p = c.prepareStatement("INSERT INTO parties (name,symbol) VALUES (?,?)")) {
            p.setString(1, name); p.setString(2, symbol); p.executeUpdate();
        }
    }

    public void deleteParty(int id) throws SQLException {
        try (Connection c = Database.get(); PreparedStatement p = c.prepareStatement("DELETE FROM parties WHERE party_id=?")) {
            p.setInt(1, id); p.executeUpdate();
        }
    }

    // ---------- candidates ----------
    public List<Candidate> listCandidates(int electionId) throws SQLException {
        try (Connection c = Database.get();
             PreparedStatement p = c.prepareStatement("SELECT c.candidate_id,c.election_id,c.name,c.party_id,p.name AS party,p.symbol FROM candidates c LEFT JOIN parties p ON p.party_id=c.party_id WHERE c.election_id=? ORDER BY c.candidate_id")) {
            p.setInt(1, electionId);
            try (ResultSet r = p.executeQuery()) {
                List<Candidate> out = new ArrayList<>();
                while (r.next()) {
                    int pid = r.getInt("party_id");
                    out.add(new Candidate(r.getInt("candidate_id"), r.getInt("election_id"), r.getString("name"),
                            r.wasNull() ? null : pid, r.getString("party"), r.getString("symbol")));
                }
                return out;
            }
        }
    }

    public void addCandidate(int electionId, String name, Integer partyId) throws SQLException {
        try (Connection c = Database.get(); PreparedStatement p = c.prepareStatement("INSERT INTO candidates (election_id,party_id,name) VALUES (?,?,?)")) {
            p.setInt(1, electionId);
            if (partyId == null) p.setNull(2, Types.INTEGER); else p.setInt(2, partyId);
            p.setString(3, name);
            p.executeUpdate();
        }
    }

    public void deleteCandidate(int id) throws SQLException {
        try (Connection c = Database.get(); PreparedStatement p = c.prepareStatement("DELETE FROM candidates WHERE candidate_id=?")) {
            p.setInt(1, id); p.executeUpdate();
        }
    }

    // ---------- voting ----------
    public boolean hasVoted(int electionId, int voterId) throws SQLException {
        try (Connection c = Database.get(); PreparedStatement p = c.prepareStatement("SELECT 1 FROM voter_participation WHERE election_id=? AND voter_id=?")) {
            p.setInt(1, electionId); p.setInt(2, voterId);
            try (ResultSet r = p.executeQuery()) { return r.next(); }
        }
    }

    /**
     * Atomically records "voter X voted" and an anonymous ballot. The PK on voter_participation guarantees one
     * vote per voter per election even if two requests race.
     */
    public String castVote(int electionId, int voterId, int candidateId) throws SQLException, ServiceException {
        String receipt = Security.randomHex(8);
        try (Connection c = Database.get()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM candidates WHERE candidate_id=? AND election_id=?")) {
                    p.setInt(1, candidateId); p.setInt(2, electionId);
                    try (ResultSet r = p.executeQuery()) {
                        if (!r.next()) throw new ServiceException("Invalid candidate for this election.");
                    }
                }
                try (PreparedStatement p = c.prepareStatement("INSERT INTO voter_participation (election_id,voter_id) VALUES (?,?)")) {
                    p.setInt(1, electionId); p.setInt(2, voterId); p.executeUpdate();
                } catch (SQLIntegrityConstraintViolationException dup) {
                    throw new ServiceException("You have already voted in this election.");
                }
                try (PreparedStatement p = c.prepareStatement("INSERT INTO votes (election_id,candidate_id,receipt_code) VALUES (?,?,?)")) {
                    p.setInt(1, electionId); p.setInt(2, candidateId); p.setString(3, receipt); p.executeUpdate();
                }
                c.commit();
                return receipt;
            } catch (SQLException | ServiceException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public List<ResultRow> results(int electionId) throws SQLException {
        try (Connection c = Database.get();
             PreparedStatement p = c.prepareStatement("SELECT c.name, COALESCE(p.name,'Independent') AS party, COUNT(v.vote_id) AS votes FROM candidates c LEFT JOIN parties p ON p.party_id=c.party_id LEFT JOIN votes v ON v.candidate_id=c.candidate_id WHERE c.election_id=? GROUP BY c.candidate_id, c.name, p.name ORDER BY votes DESC, c.name")) {
            p.setInt(1, electionId);
            try (ResultSet r = p.executeQuery()) {
                List<ResultRow> out = new ArrayList<>();
                while (r.next()) out.add(new ResultRow(r.getString(1), r.getString(2), r.getInt(3)));
                return out;
            }
        }
    }

    public Turnout turnout(int electionId) throws SQLException {
        try (Connection c = Database.get(); Statement s = c.createStatement()) {
            int eligible, voted;
            try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM voters WHERE status='APPROVED'")) { r.next(); eligible = r.getInt(1); }
            try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM voter_participation WHERE election_id=" + electionId)) { r.next(); voted = r.getInt(1); }
            return new Turnout(eligible, voted);
        }
    }
}
