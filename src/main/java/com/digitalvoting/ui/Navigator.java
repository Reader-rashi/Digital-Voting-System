package com.digitalvoting.ui;

public interface Navigator {
    String LANDING = "landing", LOGIN = "login", BALLOT = "ballot", RECEIPT = "receipt", REGISTER = "register",
            RESULTS = "results", ADMIN_LOGIN = "adminLogin", ADMIN = "admin";
    void go(String screen);
}
