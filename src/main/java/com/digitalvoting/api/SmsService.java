package com.digitalvoting.api;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import com.digitalvoting.config.AppConfig;
import com.digitalvoting.util.ServiceException;

/**
 * Mobile OTP delivery. Choose with sms.mode in config.properties:
 *   console   - print OTP in the terminal (development)
 *   twilio    - Twilio REST API (twilio.account_sid, twilio.auth_token, twilio.from)
 *   fast2sms  - Fast2SMS REST API (fast2sms.api_key)
 *   2factor   - 2Factor.in OTP API (twofactor.api_key)
 *   http      - generic JSON POST to sms.url with Bearer sms.api_key
 */
public class SmsService {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public void sendOtp(String mobile, String otp) throws ServiceException {
        String message = "Your Digital Voting OTP is " + otp + ". Valid for "
                + AppConfig.getInt("otp.expiry_minutes", 5) + " minutes. Do not share it.";
        String mode = AppConfig.get("sms.mode", "console").toLowerCase();
        switch (mode) {
            case "twilio" -> sendTwilio(mobile, message);
            case "fast2sms" -> sendFast2Sms(mobile, message);
            case "2factor" -> send2Factor(mobile, otp);
            case "http" -> sendGeneric(mobile, message);
            default -> System.out.println("[DEV] SMS OTP for " + mobile + " = " + otp);
        }
    }

    private void sendTwilio(String mobile, String message) throws ServiceException {
        String sid = AppConfig.get("twilio.account_sid"), token = AppConfig.get("twilio.auth_token"), from = AppConfig.get("twilio.from");
        if (sid.isEmpty() || token.isEmpty() || from.isEmpty())
            throw new ServiceException("Twilio is not configured (twilio.account_sid / auth_token / from).");
        String form = "To=" + enc("+91" + mobile) + "&From=" + enc(from) + "&Body=" + enc(message);
        String auth = Base64.getEncoder().encodeToString((sid + ":" + token).getBytes(StandardCharsets.UTF_8));
        post(HttpRequest.newBuilder(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + sid + "/Messages.json"))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)), "Twilio");
    }

    private void sendFast2Sms(String mobile, String message) throws ServiceException {
        String key = AppConfig.get("fast2sms.api_key");
        if (key.isEmpty()) throw new ServiceException("Fast2SMS is not configured (fast2sms.api_key).");
        String json = "{\"route\":\"q\",\"message\":\"" + esc(message) + "\",\"language\":\"english\",\"flash\":0,\"numbers\":\"" + mobile + "\"}";
        post(HttpRequest.newBuilder(URI.create("https://www.fast2sms.com/dev/bulkV2"))
                .header("authorization", key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)), "Fast2SMS");
    }

    /** 2Factor.in: GET /API/V1/{key}/SMS/{mobile}/{otp} sends our own OTP value using their default template. */
    private void send2Factor(String mobile, String otp) throws ServiceException {
        String key = AppConfig.get("twofactor.api_key");
        if (key.isEmpty()) throw new ServiceException("2Factor is not configured (twofactor.api_key).");
        post(HttpRequest.newBuilder(URI.create("https://2factor.in/API/V1/" + enc(key) + "/SMS/" + mobile + "/" + otp)).GET(), "2Factor");
    }

    private void sendGeneric(String mobile, String message) throws ServiceException {
        String json = "{\"to\":\"91" + mobile + "\",\"sender\":\"" + esc(AppConfig.get("sms.sender_id")) + "\",\"message\":\"" + esc(message) + "\"}";
        post(HttpRequest.newBuilder(URI.create(AppConfig.get("sms.url")))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + AppConfig.get("sms.api_key"))
                .POST(HttpRequest.BodyPublishers.ofString(json)), "SMS gateway");
    }

    private void post(HttpRequest.Builder b, String name) throws ServiceException {
        try {
            HttpResponse<String> res = http.send(b.timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2)
                throw new ServiceException(name + " rejected the SMS (HTTP " + res.statusCode() + "): " + res.body());
            if ("Fast2SMS".equals(name) && res.body().contains("\"return\":false"))
                throw new ServiceException("Fast2SMS error: " + res.body());
            if ("2Factor".equals(name) && !res.body().replace(" ", "").contains("\"Status\":\"Success\""))
                throw new ServiceException("2Factor error: " + res.body());
        } catch (IOException | IllegalArgumentException e) {
            throw new ServiceException("Could not reach " + name + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("SMS sending interrupted");
        }
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
}