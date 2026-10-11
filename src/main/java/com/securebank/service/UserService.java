package com.securebank.service;

import com.securebank.model.User;
import com.securebank.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User registerUser(String fullName, String email, String password) {

        if (fullName == null || fullName.trim().length() < 2) {
            throw new IllegalArgumentException(
                    "Name must contain at least 2 characters.");
        }

        if (email == null ||
                !email.trim().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException(
                    "Please enter a valid email address.");
        }

        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException(
                    "Password must contain at least 8 characters.");
        }

        String cleanName = fullName.trim();
        String cleanEmail = email.trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(cleanEmail)) {
            throw new IllegalArgumentException(
                    "An account with this email already exists.");
        }

        User user = new User();
        user.setFullName(cleanName);
        user.setEmail(cleanEmail);

        // Store the BCrypt hash, never the original password.
        user.setPassword(passwordEncoder.encode(password));

        return userRepository.save(user);
    }
}
