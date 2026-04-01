package com.rightpath.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rightpath.security.v2")
public class AuthProperties {

    private final Jwt jwt = new Jwt();
    private final Refresh refresh = new Refresh();

    public Jwt getJwt() {
        return jwt;
    }

    public Refresh getRefresh() {
        return refresh;
    }

    public static class Jwt {
        /** Optional. If blank, fall back to legacy jwt.token */
        private String secret;
        private String issuer;
        private String audience;
        private long accessTtlMinutes = 10;
        private long clockSkewSeconds = 60;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public long getAccessTtlMinutes() {
            return accessTtlMinutes;
        }

        public void setAccessTtlMinutes(long accessTtlMinutes) {
            this.accessTtlMinutes = accessTtlMinutes;
        }

        public long getClockSkewSeconds() {
            return clockSkewSeconds;
        }

        public void setClockSkewSeconds(long clockSkewSeconds) {
            this.clockSkewSeconds = clockSkewSeconds;
        }
    }

    public static class Refresh {
        private long ttlDays = 14;
        private String cookieName = "refresh_token";
        private boolean cookieSecure = true;
        private String cookieSamesite = "Lax";
        private String cookiePath = "/api/auth/refresh";

        public long getTtlDays() {
            return ttlDays;
        }

        public void setTtlDays(long ttlDays) {
            this.ttlDays = ttlDays;
        }

        public String getCookieName() {
            return cookieName;
        }

        public void setCookieName(String cookieName) {
            this.cookieName = cookieName;
        }

        public boolean isCookieSecure() {
            return cookieSecure;
        }

        public void setCookieSecure(boolean cookieSecure) {
            this.cookieSecure = cookieSecure;
        }

        public String getCookieSamesite() {
            return cookieSamesite;
        }

        public void setCookieSamesite(String cookieSamesite) {
            this.cookieSamesite = cookieSamesite;
        }

        public String getCookiePath() {
            return cookiePath;
        }

        public void setCookiePath(String cookiePath) {
            this.cookiePath = cookiePath;
        }
    }
}
