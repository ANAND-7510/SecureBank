package com.securebank.controller;

import com.securebank.model.User;
import com.securebank.model.Transaction;
import com.securebank.repository.UserRepository;
import com.securebank.repository.TransactionRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
public class DepositController {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public DepositController(
            UserRepository userRepository,
            TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    public record DepositRequest(BigDecimal amount) {}

    @PostMapping("/deposit")
    @Transactional
    public ResponseEntity<Map<String, String>> deposit(
            @RequestBody(required = false) DepositRequest request,
            HttpServletRequest httpRequest) {

        HttpSession session = httpRequest.getSession(false);

        if (session == null || session.getAttribute("userId") == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Please log in first."));
        }

        if (request == null || request.amount() == null
                || request.amount().compareTo(BigDecimal.ZERO) <= 0
                || request.amount().scale() > 2
                || request.amount().compareTo(new BigDecimal("100000")) > 0) {

            return ResponseEntity.badRequest()
                    .body(Map.of("error",
                            "Enter an amount above ₹0 and no more than ₹100,000, with up to 2 decimal places."));
        }

        Long userId = (Long) session.getAttribute("userId");

        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Account not found."));
        }

        BigDecimal currentBalance = user.getBalance();

        if (currentBalance == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Account balance is unavailable."));
        }

        BigDecimal newBalance = currentBalance.add(request.amount());

        if (newBalance.compareTo(new BigDecimal("9999999999.99")) > 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Balance limit reached."));
        }

        user.setBalance(newBalance);
        userRepository.save(user);

        // Record the deposit in transaction history.
        Transaction transaction = new Transaction(
                userId,
                "DEPOSIT",
                request.amount(),
                "Demo deposit"
        );

        transactionRepository.save(transaction);

        return ResponseEntity.ok(Map.of(
                "message", "Demo deposit successful.",
                "balance", newBalance.toPlainString()
        ));
    }
}
