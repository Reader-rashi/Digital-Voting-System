package com.digitalvoting.ui;

public interface Navigator {

    String LANDING = "LANDING";
    String LOGIN = "LOGIN";
    String REGISTER = "REGISTER";
    String BALLOT = "BALLOT";
    String RECEIPT = "RECEIPT";
    String RESULTS = "RESULTS";
    String ADMIN_LOGIN = "ADMIN_LOGIN";
    String ADMIN = "ADMIN";

    void go(String screen);
}