package org.skilltransfer.icnsubway.controller;

import org.skilltransfer.icnsubway.service.AuthService;
import org.skilltransfer.icnsubway.dto.Requests.LoginRequest;
import org.skilltransfer.icnsubway.dto.Requests.SignupRequest;
import org.skilltransfer.icnsubway.dto.Responses.LoginResponse;
import org.skilltransfer.icnsubway.dto.Responses.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class    AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/api/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse signup(@RequestBody SignupRequest body) {
        return auth.signup(body);
    }

    @PostMapping("/api/auth/login")
    public LoginResponse login(@RequestBody LoginRequest body) {
        return auth.login(body);
    }
}
