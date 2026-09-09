package com.example.backend.auth.service;

import com.example.backend.auth.entity.User;

public interface AuthService {

    User authenticate(String username, String password);
}

