package com.digitalvoting.ui;

import com.digitalvoting.model.Models.Voter;
import com.digitalvoting.service.AuthService;
import com.digitalvoting.service.OtpService;
import com.digitalvoting.service.OtpService.Channel;
import com.digitalvoting.util.ServiceException;
import com.digitalvoting.util.Validators;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/** Voter login: 1) password  2) email/SMS OTP  3) face verification. */
public class LoginPanel extends JPanel {
    private final Navigator nav;
    private final AuthService auth = new AuthService();
    private final OtpService otp = new OtpService();
    private final CardLayout phases = new CardLayout();
    private final JPanel body = new JPanel(phases);
    private Voter voter;

    public LoginPanel(Navigator nav) {
        this.nav = nav;
        setLayout(new BorderLayout(0, 12));
        setBackground(UI.BG);
        setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        add(UI.h1("Voter login"), BorderLayout.NORTH);
        body.setOpaque(false);
        body.add(credentialsPhase(), "creds");
        add(body, BorderLayout.CENTER);
    }

    private JPanel credentialsPhase() {
        UI.Form f = new UI.Form();
        JTextField id = f.row("Email or mobile", UI.text(24));
        JPasswordField pw = f.row("Password", UI.password(24));
        f.addWide(UI.button("Continue", true, () -> {
            Async.run(() -> auth.voterLogin(id.getText(), new String(pw.getPassword())), v -> {
                voter = v;
                body.add(otpPhase(), "otp");
                phases.show(body, "otp");
            }, t -> UI.error(this, t));
        }));
        return wrap(f);
    }

    private JPanel otpPhase() {
        UI.Form f = new UI.Form();
        f.addWide(UI.muted("Step 2 of 3 - choose where to receive your one-time password"));
        JComboBox<String> channel = f.row("Send OTP to",
                new JComboBox<>(new String[]{"Email " + Validators.maskEmail(voter.email()), "Mobile " + Validators.maskMobile(voter.mobile())}));
        JTextField code = f.row("OTP", UI.text(12));
        JButton send = UI.button("Send OTP", false, () -> {
            boolean email = channel.getSelectedIndex() == 0;
            Async.run(() -> { otp.send(email ? Channel.EMAIL : Channel.SMS, email ? voter.email() : voter.mobile(), AuthService.PURPOSE_LOGIN); return true; },
                    ok -> UI.info(this, "OTP sent."), t -> UI.error(this, t));
        });
        JButton verify = UI.button("Verify OTP", true, () -> {
            boolean email = channel.getSelectedIndex() == 0;
            Async.run(() -> { otp.verify(email ? voter.email() : voter.mobile(), AuthService.PURPOSE_LOGIN, code.getText()); return true; },
                    ok -> { body.add(facePhase(), "face"); phases.show(body, "face"); }, t -> UI.error(this, t));
        });
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false); row.add(send); row.add(verify);
        f.addWide(row);
        return wrap(f);
    }

    private JPanel facePhase() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);
        CameraPanel cam = new CameraPanel();
        JLabel msg = UI.muted("Step 3 of 3 - look at the camera, one face only, good lighting.");
        JButton go = UI.button("Verify face and continue", true, () -> { });
        go.addActionListener(e -> {
            BufferedImage img = cam.snapshot();
            if (img == null) { UI.error(this, new ServiceException("Camera is not ready yet.")); return; }
            go.setEnabled(false);
            Async.run(() -> {
                byte[] face = Session.FACE.extractFace(img);
                if (face == null) throw new ServiceException("Could not find exactly one face. Try again.");
                auth.verifyFace(voter, face);
                return true;
            }, ok -> { Session.voter = voter; nav.go(Navigator.BALLOT); },
               t -> { go.setEnabled(true); UI.error(this, t); });
        });
        p.add(msg, BorderLayout.NORTH);
        p.add(cam, BorderLayout.CENTER);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT)); south.setOpaque(false); south.add(go);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private static JPanel wrap(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setOpaque(false);
        p.add(c);
        return p;
    }
}
