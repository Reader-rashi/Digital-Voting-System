package com.digitalvoting.ui;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Window with a header bar and one screen at a time. Screens are rebuilt on every visit so state resets. */
public class MainFrame extends JFrame implements Navigator {
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final Map<String, Supplier<JPanel>> screens = new HashMap<>();
    private JPanel current;

    public MainFrame() {
        super("Digital Voting System");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 740);
        setLocationRelativeTo(null);

        screens.put(LANDING, () -> new LandingPanel(this));
        screens.put(LOGIN, () -> new LoginPanel(this));
        screens.put(REGISTER, () -> new RegistrationPanel(this));
        screens.put(BALLOT, () -> new BallotPanel(this));
        screens.put(RECEIPT, () -> new ReceiptPanel(this));
        screens.put(RESULTS, () -> new ResultsPanel(this));
        screens.put(ADMIN_LOGIN, () -> new AdminLoginPanel(this));
        screens.put(ADMIN, () -> new AdminDashboardPanel(this));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UI.NAVY);
        header.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));
        JLabel title = new JLabel("Digital Voting System");
        title.setForeground(Color.WHITE);
        title.setFont(UI.BASE.deriveFont(Font.BOLD, 18f));
        JButton home = new JButton("Home");
        home.addActionListener(e -> { Session.clear(); go(LANDING); });
        header.add(title, BorderLayout.WEST);
        header.add(home, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(header, BorderLayout.NORTH);
        add(root, BorderLayout.CENTER);
        go(LANDING);
    }

    @Override public void go(String screen) {
        if (current != null) root.remove(current);          // removeNotify() releases the camera
        current = screens.get(screen).get();
        root.add(current, screen);
        cards.show(root, screen);
        root.revalidate();
        root.repaint();
    }
}
