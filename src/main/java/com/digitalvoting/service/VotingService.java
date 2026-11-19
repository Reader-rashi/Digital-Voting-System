package com.digitalvoting.service;

import com.digitalvoting.dao.*;
import com.digitalvoting.model.Models.*;
import com.digitalvoting.util.ServiceException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public class VotingService {
    private final ElectionDao elections = new ElectionDao();
    private final AuditDao audit = new AuditDao();

    public Election requireActiveElection() throws ServiceException {
        try {
            return elections.activeElection().orElseThrow(() -> new ServiceException("There is no election open for voting right now."));
        } catch (SQLException e) { throw new ServiceException("Database error: " + e.getMessage(), e); }
    }

    public List<Candidate> ballot(Election e) throws ServiceException {
        try { return elections.listCandidates(e.id()); }
        catch (SQLException ex) { throw new ServiceException("Database error: " + ex.getMessage(), ex); }
    }

    public boolean hasVoted(Election e, Voter v) throws ServiceException {
        try { return elections.hasVoted(e.id(), v.id()); }
        catch (SQLException ex) { throw new ServiceException("Database error: " + ex.getMessage(), ex); }
    }

    public VoteReceipt cast(Voter voter, int candidateId) throws ServiceException {
        Election e = requireActiveElection();
        try {
            String code = elections.castVote(e.id(), voter.id(), candidateId);
            audit.log("voter:" + voter.id(), "VOTE_CAST", "election=" + e.id());   // never logs the candidate
            return new VoteReceipt(code, e.title(), LocalDateTime.now());
        } catch (SQLException ex) { throw new ServiceException("Could not record your vote: " + ex.getMessage(), ex); }
    }
}
