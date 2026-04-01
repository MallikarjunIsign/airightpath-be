package com.rightpath.service.impl;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import com.rightpath.dto.AccessTokenResponse;

@Service
public class TokenFacade {

    private final UserDetailsService userDetailsService;
    private final AccessTokenService accessTokenService;

    public TokenFacade(UserDetailsService userDetailsService, AccessTokenService accessTokenService) {
        this.userDetailsService = userDetailsService;
        this.accessTokenService = accessTokenService;
    }

    public AccessTokenResponse issueAccessTokenForSubject(String subject) {
        UserDetails user = userDetailsService.loadUserByUsername(subject);
        var issued = accessTokenService.issueAccessToken(user);
        return new AccessTokenResponse(issued.token(), "Bearer", issued.expiresInSeconds());
    }
}
