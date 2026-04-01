package com.rightpath.util;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.rightpath.config.AuthProperties;

@Component
public class RefreshCookieFactory {

    private final AuthProperties props;

    public RefreshCookieFactory(AuthProperties props) {
        this.props = props;
    }

    public ResponseCookie build(String rawRefreshToken) {
        long maxAgeSeconds = Duration.ofDays(props.getRefresh().getTtlDays()).toSeconds();
        return ResponseCookie.from(props.getRefresh().getCookieName(), rawRefreshToken)
                .httpOnly(true)
                .secure(props.getRefresh().isCookieSecure())
                .path(props.getRefresh().getCookiePath())
                .maxAge(maxAgeSeconds)
                .sameSite(props.getRefresh().getCookieSamesite())
                .build();
    }

    public ResponseCookie clear() {
        return ResponseCookie.from(props.getRefresh().getCookieName(), "")
                .httpOnly(true)
                .secure(props.getRefresh().isCookieSecure())
                .path(props.getRefresh().getCookiePath())
                .maxAge(0)
                .sameSite(props.getRefresh().getCookieSamesite())
                .build();
    }
}
