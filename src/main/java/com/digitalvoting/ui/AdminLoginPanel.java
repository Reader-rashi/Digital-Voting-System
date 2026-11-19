package com.digitalvoting.ui;

import com.digitalvoting.service.AuthService;

import javax.swing.*;
import java.awt.*;

public class AdminLoginPanel extends JPanel {
    public AdminLoginPanel(Navigator nav) {
        setLayout(new BorderLayout(0, 12));
        setBackground(UI.BG);
        setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        add(UI.h1("Admin login"), BorderLayout.NORTH);
        UI.Form f = new UI.Form();
        JTextField user = f.row("Username", UI.text(22));
        JPasswordField pw = f.row("Password", UI.password(22));
        AuthService auth = new AuthService();
        f.addWide(UI.button("Sign in", true, () -> Async.run(() -> auth.adminLogin(user.getText(), new String(pw.getPassword())),
                a -> { Session.admin = a; nav.go(Navigator.ADMIN); }, t -> UI.error(this, t))));
        JPanel w = new JPanel(new FlowLayout(FlowLayout.LEFT)); w.setOpaque(false); w.add(f);
        add(w, BorderLayout.CENTER);
    }
}
