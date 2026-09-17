package com.qrmenu.account;

import com.qrmenu.account.SignupDtos.SignupRequest;
import com.qrmenu.account.SignupDtos.SignupResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inscription libre-service, publique — pas de Basic Auth (voir {@code SecurityConfig} :
 * seul {@code /api/admin/**} exige un compte, tout le reste, dont {@code /api/public/**}
 * comme {@code OrderPublicController}, reste ouvert).
 */
@RestController
public class SignupPublicController {

    private final SignupService signupService;

    public SignupPublicController(SignupService signupService) {
        this.signupService = signupService;
    }

    @PostMapping("/api/public/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(signupService.signup(request));
    }
}
