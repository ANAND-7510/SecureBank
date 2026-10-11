
package com.securebank.controller;

import com.securebank.model.User;
import com.securebank.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class LoginController {

    private final UserRepository userRepository;

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    public LoginController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record LoginRequest(String email, String password) {}

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        if (request.email() == null || request.password() == null
                || request.email().isBlank()
                || request.email().length() > 150
                || request.password().length() > 72) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Please enter a valid email and password."));
        }

        User user = userRepository
                .findByEmailIgnoreCase(request.email().trim())
                .orElse(null);

        if (user == null ||
                !passwordEncoder.matches(
                        request.password(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password."));
        }

        // Invalidate any existing session before creating a new one.
        HttpSession oldSession = httpRequest.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }

        // Create a server-side session after successful authentication.
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute("userId", user.getId());
        session.setAttribute("userName", user.getFullName());
        session.setMaxInactiveInterval(15 * 60);

        return ResponseEntity.ok(Map.of(
                "message", "Login successful.",
                "name", user.getFullName()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        return ResponseEntity.ok(
                Map.of("message", "Logged out successfully.")
        );
    }
}
