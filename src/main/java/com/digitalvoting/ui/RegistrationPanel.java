package com.digitalvoting.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import com.digitalvoting.config.AppConfig;
import com.digitalvoting.model.Models.NewVoter;
import com.digitalvoting.service.OtpService;
import com.digitalvoting.service.OtpService.Channel;
import com.digitalvoting.service.RegistrationService;
import com.digitalvoting.util.ServiceException;
import com.digitalvoting.util.Validators;

/** 4-step registration: details+Aadhaar, email/mobile OTP, face enrollment, password+submit. */
public class RegistrationPanel extends JPanel {
    private final Navigator nav;
    private final RegistrationService reg = new RegistrationService();
    private final OtpService otp = new OtpService();
    private final CardLayout steps = new CardLayout();
    private final JPanel body = new JPanel(steps);
    private final JLabel stepLabel = UI.muted("");
    private final List<byte[]> faces = new ArrayList<>();
    private boolean emailOk, mobileOk;

    // step 1 fields
    private final JTextField name = UI.text(24), dob = UI.text(24), aadhaar = UI.text(24), constituency = UI.text(24),
            email = UI.text(24), mobile = UI.text(24);
    private final JComboBox<String> gender = new JComboBox<>(new String[]{"Male", "Female", "Other"});
    private final JPasswordField pw = UI.password(24), pw2 = UI.password(24);

    public RegistrationPanel(Navigator nav) {
        this.nav = nav;
        setLayout(new BorderLayout(0, 12));
        setBackground(UI.BG);
        setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        JPanel top = new JPanel(new GridLayout(2, 1));
        top.setOpaque(false);
        top.add(UI.h1("Voter registration")); top.add(stepLabel);
        add(top, BorderLayout.NORTH);
        body.setOpaque(false);
        body.add(step1(), "1"); body.add(step2(), "2"); body.add(step3(), "3"); body.add(step4(), "4");
        add(body, BorderLayout.CENTER);
        show(1);
    }

    private void show(int n) { stepLabel.setText("Step " + n + " of 4"); steps.show(body, String.valueOf(n)); }

    private NewVoter draft(String password) {
        LocalDate d;
        try { d = LocalDate.parse(dob.getText().trim()); }
        catch (java.time.format.DateTimeParseException e) { throw new IllegalArgumentException("Date of birth must be yyyy-MM-dd, e.g. 2001-04-23"); }
        return new NewVoter(name.getText().trim(), d, (String) gender.getSelectedItem(), aadhaar.getText().replaceAll("\\s", ""),
                email.getText().trim().toLowerCase(), mobile.getText().trim(), constituency.getText().trim(), password, new ArrayList<>(faces));
    }

    private JPanel step1() {
        UI.Form f = new UI.Form();
        f.row("Full name (as on Aadhaar)", name);
        f.row("Date of birth (yyyy-MM-dd)", dob);
        f.row("Gender", gender);
        f.row("Aadhaar number", aadhaar);
        f.row("Constituency", constituency);
        f.row("Email", email);
        f.row("Mobile (10 digits)", mobile);
        f.addWide(UI.button("Verify Aadhaar and continue", true, () -> {
            final NewVoter v;
            try { v = draft(""); } catch (RuntimeException e) { UI.error(this, e); return; }
            Async.run(() -> { reg.validateStep1(v); return true; }, ok -> show(2), t -> UI.error(this, t));
        }));
        return left(f);
    }

    /** One row: [Send OTP] [code] [Verify]. */
    private JPanel otpRow(Channel ch, Supplier<String> target, String purpose, Runnable onVerified) {
        JTextField code = UI.text(8);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row.setOpaque(false);
        row.add(UI.button("Send OTP", false, () -> Async.run(() -> { otp.send(ch, target.get(), purpose); return true; },
                ok -> UI.info(this, "OTP sent to " + (ch == Channel.EMAIL ? Validators.maskEmail(target.get()) : Validators.maskMobile(target.get()))),
                t -> UI.error(this, t))));
        row.add(code);
        row.add(UI.button("Verify", true, () -> Async.run(() -> { otp.verify(target.get(), purpose, code.getText()); return true; },
                ok -> { onVerified.run(); UI.info(this, "Verified."); }, t -> UI.error(this, t))));
        return row;
    }

    private JPanel step2() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JButton next = UI.button("Continue", true, () -> show(3));
        next.setEnabled(false);
        Runnable check = () -> next.setEnabled(emailOk && mobileOk);
        p.add(new JLabel("Verify your email"));
        p.add(otpRow(Channel.EMAIL, () -> email.getText().trim().toLowerCase(), RegistrationService.PURPOSE_EMAIL, () -> { emailOk = true; check.run(); }));
        p.add(Box.createVerticalStrut(16));
        p.add(new JLabel("Verify your mobile number"));
        p.add(otpRow(Channel.SMS, () -> mobile.getText().trim(), RegistrationService.PURPOSE_MOBILE, () -> { mobileOk = true; check.run(); }));
        p.add(Box.createVerticalStrut(16));
        p.add(next);
        return left(p);
    }

    private JPanel step3() {
    int min = AppConfig.getInt("face.min_samples", 6);

    JPanel p = new JPanel(new BorderLayout(0, 12));
    p.setOpaque(false);

    CameraPanel cam = new CameraPanel();

    JLabel count = UI.muted(
        "Samples captured: 0 / " + min +
        " (slightly turn your head between shots)"
    );

    JButton next = UI.button(
        "Continue",
        true,
        () -> show(4)
    );

    next.setEnabled(false);

    JButton capture = UI.button(
        "Capture sample",
        false,
        () -> {}
    );

    capture.addActionListener(e -> {

        if (faces.size() >= min) {
            UI.info(this, "Required face samples already captured.");
            return;
        }

        BufferedImage img = cam.snapshot();

        if (img == null) {
            UI.error(this,
                new ServiceException("Camera is not ready yet."));
            return;
        }

        capture.setEnabled(false);

        Async.run(() -> {

            byte[] face = Session.FACE.extractFace(img);

            if (face == null) {
                throw new ServiceException(
                    "Could not detect exactly one face. Try again."
                );
            }

            return face;

        }, face -> {

            faces.add(face);

            count.setText(
                "Samples captured: " + faces.size() +
                " / " + min
            );

            next.setEnabled(faces.size() >= min);

            capture.setEnabled(faces.size() < min);

        }, t -> {

            capture.setEnabled(true);
            UI.error(this, t);

        });
    });

    JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
    south.setOpaque(false);

    south.add(capture);
    south.add(next);
    south.add(count);

    p.add(cam, BorderLayout.CENTER);
    p.add(south, BorderLayout.SOUTH);

    return p;
}

    private JPanel step4() {
        UI.Form f = new UI.Form();
        f.addWide(UI.muted("Choose a password (8+ characters, letters and digits)"));
        f.row("Password", pw);
        f.row("Confirm password", pw2);
        f.addWide(UI.button("Submit registration", true, () -> {
            String a = new String(pw.getPassword());
            if (!a.equals(new String(pw2.getPassword()))) { UI.error(this, new ServiceException("Passwords do not match.")); return; }
            final NewVoter v;
            try { v = draft(a); } catch (RuntimeException e) { UI.error(this, e); return; }
            Async.run(() -> reg.register(v), id -> {
                UI.info(this, "Registration submitted. Your voter ID is " + id + ".\nYou can log in once an administrator approves your registration.");
                nav.go(Navigator.LANDING);
            }, t -> UI.error(this, t));
        }));
        return left(f);
    }

    private static JPanel left(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setOpaque(false);
        p.add(c);
        return p;
    }
}
