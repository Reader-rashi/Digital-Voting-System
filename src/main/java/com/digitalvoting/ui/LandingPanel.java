package com.digitalvoting.ui;

import com.digitalvoting.db.Database;

import javax.swing.*;
import java.awt.*;

public class LandingPanel extends JPanel {
    public LandingPanel(Navigator nav) {
        setLayout(new BorderLayout());
        setBackground(UI.BG);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));

        JLabel h = UI.h1("Vote securely. From anywhere.");
        h.setAlignmentX(CENTER_ALIGNMENT);
        JLabel sub = UI.muted("Aadhaar-verified registration, OTP and face authentication.");
        sub.setAlignmentX(CENTER_ALIGNMENT);
        box.add(h); box.add(Box.createVerticalStrut(6)); box.add(sub); box.add(Box.createVerticalStrut(28));

        for (Object[] b : new Object[][]{
                {"Register as a voter", true, Navigator.REGISTER}, {"Voter login", true, Navigator.LOGIN},
                {"View election results", false, Navigator.RESULTS}, {"Admin login", false, Navigator.ADMIN_LOGIN}}) {
            JButton btn = UI.button((String) b[0], (Boolean) b[1], () -> nav.go((String) b[2]));
            btn.setAlignmentX(CENTER_ALIGNMENT);
            btn.setMaximumSize(new Dimension(320, 44));
            box.add(btn); box.add(Box.createVerticalStrut(12));
        }
        center.add(box);
        add(center, BorderLayout.CENTER);

        JLabel status = UI.muted("Checking database connection...");
        status.setBorder(BorderFactory.createEmptyBorder(8, 24, 8, 24));
        add(status, BorderLayout.SOUTH);
        Async.run(Database::ping, err -> {
            status.setText(err == null ? "Database: connected" : "Database: NOT connected - " + err);
            status.setForeground(err == null ? UI.GREEN : Color.RED);
        }, t -> status.setText("Database check failed: " + t.getMessage()));
    }
}
