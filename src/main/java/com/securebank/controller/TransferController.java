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
@RequestMapping("/api/transfer")
public class TransferController {

    private static final BigDecimal MAX_TRANSFER =
            new BigDecimal("100000.00");

    private static final BigDecimal MAX_BALANCE =
            new BigDecimal("9999999999.99");

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public TransferController(
            UserRepository userRepository,
            TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    public record TransferRequest(
            String recipientEmail,
            BigDecimal amount
    ) {}

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, String>> transfer(
            @RequestBody(required = false) TransferRequest request,
            HttpServletRequest httpRequest) {

        HttpSession session = httpRequest.getSession(false);

        if (session == null || session.getAttribute("userId") == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Please log in first."));
        }

        if (request == null
                || request.recipientEmail() == null
                || request.amount() == null
                || request.recipientEmail().isBlank()
                || request.recipientEmail().length() > 150
                || request.amount().signum() <= 0
                || request.amount().scale() > 2
                || request.amount().compareTo(MAX_TRANSFER) > 0) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error",
                            "Enter a valid recipient email and an amount up to ₹100,000."
                    ));
        }

        Long senderId = (Long) session.getAttribute("userId");
        String recipientEmail = request.recipientEmail().trim();

        User recipientLookup = userRepository
                .findByEmailIgnoreCase(recipientEmail)
                .orElse(null);

        if (recipientLookup == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Recipient account was not found."));
        }

        Long recipientId = recipientLookup.getId();

        if (senderId.equals(recipientId)) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error",
                            "You cannot transfer money to your own account."
                    ));
        }

        // Lock both accounts in ID order.
        Long firstId = senderId < recipientId ? senderId : recipientId;
        Long secondId = senderId < recipientId ? recipientId : senderId;

        User firstUser = userRepository.findByIdForUpdate(firstId)
                .orElse(null);

        User secondUser = userRepository.findByIdForUpdate(secondId)
                .orElse(null);

        if (firstUser == null || secondUser == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error",
                            "Sender or recipient account was not found."
                    ));
        }

        User sender = firstId.equals(senderId) ? firstUser : secondUser;
        User recipient = firstId.equals(recipientId) ? firstUser : secondUser;

        BigDecimal amount = request.amount();
        BigDecimal senderBalance = sender.getBalance();
        BigDecimal recipientBalance = recipient.getBalance();

        if (senderBalance == null || recipientBalance == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Account balance is unavailable."));
        }

        if (senderBalance.compareTo(amount) < 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Insufficient demo balance."));
        }

        BigDecimal newRecipientBalance = recipientBalance.add(amount);

        if (newRecipientBalance.compareTo(MAX_BALANCE) > 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Recipient balance limit reached."));
        }

        BigDecimal newSenderBalance = senderBalance.subtract(amount);

        // Update both balances.
        sender.setBalance(newSenderBalance);
        recipient.setBalance(newRecipientBalance);

        userRepository.save(sender);
        userRepository.save(recipient);

        // Record the outgoing transfer for the sender.
        transactionRepository.save(new Transaction(
                senderId,
                "TRANSFER_OUT",
                amount,
                "Demo transfer to " + recipientEmail
        ));

        // Record the incoming transfer for the recipient.
        transactionRepository.save(new Transaction(
                recipientId,
                "TRANSFER_IN",
                amount,
                "Demo transfer from " + sender.getEmail()
        ));

        return ResponseEntity.ok(Map.of(
                "message", "Demo transfer successful.",
                "balance", newSenderBalance.toPlainString()
        ));
    }
}
