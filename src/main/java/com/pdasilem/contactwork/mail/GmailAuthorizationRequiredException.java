package com.pdasilem.contactwork.mail;

public class GmailAuthorizationRequiredException extends RuntimeException {

    private final String authorizationUrl;

    public GmailAuthorizationRequiredException(String authorizationUrl) {
        super("Gmail authorization is required");
        this.authorizationUrl = authorizationUrl;
    }

    public String getAuthorizationUrl() {
        return authorizationUrl;
    }
}
