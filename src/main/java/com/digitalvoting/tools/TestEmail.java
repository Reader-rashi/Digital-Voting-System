package com.digitalvoting.tools;

import com.digitalvoting.api.EmailService;
import com.digitalvoting.config.AppConfig;

import java.io.File;

/** Usage: TestEmail <recipient email>. Shows what config is loaded and the real error if sending fails. */
public class TestEmail {
    public static void main(String[] a) {
        if (a.length != 1) { System.err.println("Usage: TestEmail <recipient email>"); System.exit(1); }
        File f = new File("config.properties");
        System.out.println("Working folder : " + new File(".").getAbsoluteFile().getParent());
        System.out.println("config.properties found here: " + f.exists());
        System.out.println("smtp.host      : '" + AppConfig.get("smtp.host") + "'");
        System.out.println("smtp.port      : '" + AppConfig.get("smtp.port") + "'");
        System.out.println("smtp.username  : '" + AppConfig.get("smtp.username") + "'");
        System.out.println("smtp.password  : " + AppConfig.get("smtp.password").length() + " characters (Gmail App Password must be 16)");
        System.out.println("smtp.from      : '" + AppConfig.get("smtp.from") + "'");
        if (AppConfig.get("smtp.host").isEmpty()) {
            System.out.println("\nsmtp.host is EMPTY -> the app is in dev mode and only prints OTPs. Fix config.properties.");
        }
        try {
            new EmailService().sendOtp(a[0], "123456");
            System.out.println("\nDONE. If smtp.host was set, check the inbox/spam of " + a[0]);
        } catch (Exception e) {
            System.out.println("\nFAILED: " + e.getMessage());
            Throwable c = e.getCause();
            while (c != null) { System.out.println("  caused by: " + c); c = c.getCause(); }
        }
    }
}
