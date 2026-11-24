package com.digitalvoting.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;

import com.digitalvoting.model.Models.*;
import com.digitalvoting.model.Models.Candidate;
import com.digitalvoting.model.Models.Election;
import com.digitalvoting.service.VotingService;

public class BallotPanel extends JPanel {
    private final VotingService voting = new VotingService();
    private final ButtonGroup group = new ButtonGroup();

    public BallotPanel(Navigator nav) {
        setLayout(new BorderLayout(0, 12));
        setBackground(UI.BG);
        setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        add(UI.h1("Cast your vote"), BorderLayout.NORTH);
        if (Session.voter == null) { add(UI.muted("Please log in first."), BorderLayout.CENTER); return; }
        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        add(new JScrollPane(list), BorderLayout.CENTER);

        Async.run(() -> {
            Election e = voting.requireActiveElection();
            if (voting.hasVoted(e, Session.voter)) throw new com.digitalvoting.util.ServiceException("You have already voted in this election.");
            return new Object[]{e, voting.ballot(e)};
        }, res -> {
            Election e = (Election) res[0];
            @SuppressWarnings("unchecked") List<Candidate> cands = (List<Candidate>) res[1];
            list.add(UI.muted(e.title() + "  -  closes " + e.end().toString().replace('T', ' ')));
            list.add(Box.createVerticalStrut(10));
            JRadioButton[] picks = new JRadioButton[cands.size()];
            for (int i = 0; i < cands.size(); i++) {
                Candidate c = cands.get(i);
                JRadioButton r = new JRadioButton(c.name() + "   (" + (c.partyName() == null ? "Independent" : c.partyName())
                        + (c.symbol() == null ? "" : ", " + c.symbol()) + ")");
                r.setFont(UI.BASE.deriveFont(16f));
                r.setOpaque(false);
                r.setActionCommand(String.valueOf(c.id()));
                group.add(r); picks[i] = r;
                list.add(r); list.add(Box.createVerticalStrut(8));
            }
            JButton cast = UI.button("Cast vote", true, () -> {
                if (group.getSelection() == null) { UI.info(this, "Select a candidate first."); return; }
                int cid = Integer.parseInt(group.getSelection().getActionCommand());
                if (JOptionPane.showConfirmDialog(this, "Your vote is final and cannot be changed. Continue?", "Confirm vote", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
                Async.run(() -> voting.cast(Session.voter, cid), receipt -> { Session.receipt = receipt; nav.go(Navigator.RECEIPT); }, t -> UI.error(this, t));
            });
            JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT)); south.setOpaque(false); south.add(cast);
            add(south, BorderLayout.SOUTH);
            revalidate(); repaint();
        }, t -> { list.add(UI.muted(t.getMessage())); revalidate(); repaint(); });
    }
}