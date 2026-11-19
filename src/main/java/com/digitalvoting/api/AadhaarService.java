package com.digitalvoting.api;

import com.digitalvoting.config.AppConfig;
import com.digitalvoting.util.ServiceException;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/**
 * Aadhaar identity verification.
 * mock: format + Verhoeff checksum only.  live: calls your UIDAI-authorised (AUA/KUA) gateway.
 * Never log or persist the raw Aadhaar number.
 */
public class AadhaarService {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private static final int[][] D = {
        {0,1,2,3,4,5,6,7,8,9},{1,2,3,4,0,6,7,8,9,5},{2,3,4,0,1,7,8,9,5,6},{3,4,0,1,2,8,9,5,6,7},{4,0,1,2,3,9,5,6,7,8},
        {5,9,8,7,6,0,4,3,2,1},{6,5,9,8,7,1,0,4,3,2},{7,6,5,9,8,2,1,0,4,3},{8,7,6,5,9,3,2,1,0,4},{9,8,7,6,5,4,3,2,1,0}};
    private static final int[][] P = {
        {0,1,2,3,4,5,6,7,8,9},{1,5,7,6,2,8,3,0,9,4},{5,8,0,3,7,9,6,1,4,2},{8,9,1,6,0,4,3,5,2,7},
        {9,4,5,3,1,2,6,8,7,0},{4,2,8,6,5,7,3,9,0,1},{2,7,9,3,8,0,6,4,1,5},{7,0,4,6,9,1,3,2,5,8}};

    /** 12 digits, first digit 2-9, valid Verhoeff checksum. */
    public static boolean isValidFormat(String a) {
        if (a == null || !a.matches("[2-9][0-9]{11}")) return false;
        int c = 0;
        for (int i = 0; i < a.length(); i++) {
            int digit = a.charAt(a.length() - 1 - i) - '0';
            c = D[c][P[i % 8][digit]];
        }
        return c == 0;
    }

    public void verify(String aadhaar, String fullName) throws ServiceException {
        if (!isValidFormat(aadhaar)) throw new ServiceException("Invalid Aadhaar number.");
        if (!"live".equalsIgnoreCase(AppConfig.get("aadhaar.mode", "mock"))) return;   // mock mode

        String json = "{\"aadhaar\":\"" + aadhaar + "\",\"name\":\"" + fullName.replace("\"", "") + "\"}";
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(AppConfig.get("aadhaar.url")))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("X-License-Key", AppConfig.get("aadhaar.license_key"))
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            // Adapt to your gateway's response schema. Here: HTTP 200 and body contains "status":"success".
            boolean ok = res.statusCode() == 200 && res.body().replace(" ", "").contains("\"status\":\"success\"");
            if (!ok) throw new ServiceException("Aadhaar verification failed.");
        } catch (IOException | IllegalArgumentException e) {
            throw new ServiceException("Could not reach Aadhaar verification service: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("Aadhaar verification interrupted");
        }
    }
}
