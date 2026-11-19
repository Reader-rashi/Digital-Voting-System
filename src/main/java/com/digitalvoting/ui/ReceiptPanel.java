package com.digitalvoting.ui;

import com.digitalvoting.model.Models.VoteReceipt;

import javax.swing.*;
import java.awt.*;
import java.time.format.DateTimeFormatter;

public class ReceiptPanel extends JPanel {
    public ReceiptPanel(Navigator nav) {
        setLayout(new GridBagLayout());
        setBackground(UI.BG);
        VoteReceipt r = Session.receipt;
        JPanel card = new JPanel();
        card.setBackground(UI.CARD);
        card.setBorder(BorderFactory.createEmptyBorder(28, 40, 28, 40));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        if (r == null) {
            card.add(UI.h1("No receipt available"));
        } else {
            JLabel ok = UI.h1("Vote recorded");
            ok.setForeground(UI.GREEN);
            JLabel code = new JLabel(r.code());
            code.setFont(new Font(Font.MONOSPACED, Font.BOLD, 28));
            card.add(ok);
            card.add(Box.createVerticalStrut(10));
            card.add(UI.muted(r.electionTitle()));
            card.add(UI.muted(r.castAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))));
            card.add(Box.createVerticalStrut(16));
            card.add(UI.muted("Your receipt code (it does not reveal your choice):"));
            card.add(code);
        }
        card.add(Box.createVerticalStrut(20));
        card.add(UI.button("Done", true, () -> { Session.clear(); nav.go(Navigator.LANDING); }));
        add(card);
    }
}
