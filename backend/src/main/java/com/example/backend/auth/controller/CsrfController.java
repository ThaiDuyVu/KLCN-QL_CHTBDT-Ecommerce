package com.example.backend.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class CsrfController {

    @GetMapping("/csrf")
    public ResponseEntity<Void> getCsrfToken(
            @RequestAttribute("_csrf") CsrfToken csrfToken
    ) {
        csrfToken.getToken();

        return ResponseEntity.noContent().build();
    }
}
