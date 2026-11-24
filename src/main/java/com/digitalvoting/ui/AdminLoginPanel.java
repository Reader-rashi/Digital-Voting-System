
package com.digitalvoting.ui;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import com.digitalvoting.model.Models.Admin;
import com.digitalvoting.service.AuthService;
import com.digitalvoting.util.ServiceException;

public class AdminLoginPanel extends JPanel {

    private final Navigator nav;
    private final AuthService authService = new AuthService();

    private final JTextField usernameField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);

    public AdminLoginPanel(Navigator nav) {

        this.nav = nav;

        setLayout(new GridBagLayout());
        setBackground(UI.BG);

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);

        card.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(
                                new Color(220, 220, 220)
                        ),
                        BorderFactory.createEmptyBorder(
                                30, 35, 30, 35
                        )
                )
        );

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(10, 8, 10, 8);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;

        JLabel title = UI.h1("Administrator Login");
        title.setHorizontalAlignment(SwingConstants.CENTER);

        g.gridx = 0;
        g.gridy = 0;
        g.gridwidth = 2;

        card.add(title, g);

        g.gridwidth = 1;
        g.gridy++;

        card.add(new JLabel("Username:"), g);

        g.gridx = 1;
        usernameField.setFont(UI.BASE);
        card.add(usernameField, g);

        g.gridx = 0;
        g.gridy++;

        card.add(new JLabel("Password:"), g);

        g.gridx = 1;
        passwordField.setFont(UI.BASE);
        card.add(passwordField, g);

        JButton loginButton = UI.button(
                "Login",
                true,
                this::login
        );

        g.gridx = 0;
        g.gridy++;
        g.gridwidth = 2;

        card.add(loginButton, g);

        add(card);
    }

    private void login() {

        String username = usernameField.getText().trim();

        String password = new String(
                passwordField.getPassword()
        );

        if (username.isBlank() || password.isBlank()) {
            UI.info(this, "Enter username and password.");
            return;
        }

        try {

            Admin admin = authService.adminLogin(
                    username,
                    password
            );

            if (admin == null) {
                UI.info(this, "Invalid administrator credentials.");
                return;
            }

            Session.admin = admin;

            UI.info(
                    this,
                    "Welcome, " + admin.username()
            );

            nav.go(Navigator.ADMIN);

        } catch (ServiceException e) {

            UI.error(this, e);

        } catch (Exception e) {

            UI.info(
                    this,
                    "Administrator login failed: " + e.getMessage()
            );
        }
    }
}
