
package com.hotelpro.controller;

import com.hotelpro.model.AppUser;
import com.hotelpro.repository.UserRepository;
import com.hotelpro.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public record RegisterRequest(
            @NotBlank
            @Size(max = 100)
            String name,

            @NotBlank
            @Email
            @Size(max = 150)
            String email,

            @NotBlank
            @Pattern(regexp = "^[0-9]{10}$",
                    message = "Phone must contain exactly 10 digits")
            String phone,

            @NotBlank
            @Size(min = 8, max = 72)
            String password
    ) {}

    public record LoginRequest(
            @NotBlank
            @Email
            String email,

            @NotBlank
            String password
    ) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest request) {

        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Email is already registered"
                    ));
        }

        AppUser user = new AppUser(
                request.name().trim(),
                email,
                passwordEncoder.encode(request.password()),
                "GUEST"
        );

        user.setPhone(request.phone().trim());

        userRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "message",
                        "Registration successful"
                ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request) {

        String email = request.email().trim().toLowerCase();

        try {
            Authentication authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    email,
                                    request.password()
                            )
                    );

            UserDetails userDetails =
                    (UserDetails) authentication.getPrincipal();

            AppUser user = userRepository.findByEmail(email)
                    .orElseThrow();

            String token = jwtService.generateToken(userDetails);

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "user", Map.of(
                            "id", user.id,
                            "name", user.name,
                            "email", user.email,
                            "phone", user.getPhone() == null
                                    ? "" : user.getPhone(),
                            "role", user.role
                    )
            ));

        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message",
                            "Invalid email or password"
                    ));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {

        if (authentication == null ||
                !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not logged in"));
        }

        return userRepository.findByEmail(authentication.getName())
                .<ResponseEntity<?>>map(user ->
                        ResponseEntity.ok(Map.of(
                                "id", user.id,
                                "name", user.name,
                                "email", user.email,
                                "phone", user.getPhone() == null
                                        ? "" : user.getPhone(),
                                "role", user.role
                        ))
                )
                .orElseGet(() ->
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(Map.of(
                                        "message",
                                        "User not found"
                                ))
                );
    }
}
