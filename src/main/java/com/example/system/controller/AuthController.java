package com.example.system.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.system.dto.AuthRequest;
import com.example.system.dto.AuthUserResponse;
import com.example.system.dto.ChangePasswordRequest;
import com.example.system.dto.PreferenceRequest;
import com.example.system.dto.UpdateProfileRequest;
import com.example.system.service.AuthService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public AuthUserResponse me(HttpSession session) {
        return authService.currentUser(session);
    }

    @PostMapping("/login")
    public AuthUserResponse login(@RequestBody AuthRequest request, HttpSession session) {
        return authService.login(request == null ? null : request.username(),
                request == null ? null : request.password(),
                session);
    }

    @PostMapping("/register")
    public AuthUserResponse register(@RequestBody AuthRequest request, HttpSession session) {
        return authService.register(request == null ? null : request.username(),
                request == null ? null : request.password(),
                session);
    }

    @PutMapping("/profile")
    public AuthUserResponse updateProfile(@RequestBody UpdateProfileRequest request, HttpSession session) {
        return authService.updateProfile(request, session);
    }

    @PutMapping("/preferences")
    public AuthUserResponse updatePreferences(@RequestBody PreferenceRequest request, HttpSession session) {
        return authService.updatePreferences(request, session);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestBody ChangePasswordRequest request, HttpSession session) {
        authService.changePassword(request, session);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpSession session) {
        authService.logout(session);
    }
}
