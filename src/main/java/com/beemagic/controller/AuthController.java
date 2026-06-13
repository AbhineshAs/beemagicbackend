package com.beemagic.controller;

import com.beemagic.entity.User;
import com.beemagic.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${admin.email:admin@beemagic.com}")
    private String adminEmail;

    @Value("${admin.password:admin123}")
    private String adminPassword;

    // Stores Phone Number -> OTP code mapping temporarily
    private final Map<String, String> phoneOtpMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void seedAdmin() {
        Optional<User> adminOpt = userRepository.findByEmail(adminEmail);
        if (adminOpt.isEmpty()) {
            User admin = new User();
            admin.setName("Bee Magic Admin");
            admin.setEmail(adminEmail);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRole("ADMIN");
            userRepository.save(admin);
        } else {
            User admin = adminOpt.get();
            boolean updated = false;
            if (!"ADMIN".equals(admin.getRole())) {
                admin.setRole("ADMIN");
                updated = true;
            }
            if (!passwordEncoder.matches(adminPassword, admin.getPassword())) {
                admin.setPassword(passwordEncoder.encode(adminPassword));
                updated = true;
            }
            if (updated) {
                userRepository.save(admin);
            }
        }
    }


    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> request) {
        String phoneNumber = request.get("phoneNumber");
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Phone number is required!"));
        }

        // Generate a 6-digit OTP
        String otp = String.format("%06d", new Random().nextInt(1000000));
        phoneOtpMap.put(phoneNumber, otp);

        // Log clearly to the Spring Boot console output
        System.out.println("\n==============================================");
        System.out.println("BEE MAGIC OTP FOR " + phoneNumber + " IS: " + otp);
        System.out.println("==============================================\n");

        return ResponseEntity.ok(Map.of(
                "message", "OTP code sent to " + phoneNumber + " successfully!",
                "otp", otp // Sent in response to make local verification easy for user/developer testing
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email is already registered!"));
        }

        if (request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isEmpty()) {
            if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Phone number is already registered!"));
            }
        }

        // OTP verification step
        String expectedOtp = phoneOtpMap.get(request.getPhoneNumber());
        if (expectedOtp == null || !expectedOtp.equals(request.getOtp())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid or expired OTP code!"));
        }

        // Clear OTP after successful validation
        phoneOtpMap.remove(request.getPhoneNumber());

        User user = new User();
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setAddress(request.getAddress());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setRole("USER");

        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "address", user.getAddress() != null ? user.getAddress() : "",
                "phoneNumber", user.getPhoneNumber() != null ? user.getPhoneNumber() : "",
                "role", user.getRole()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        Optional<User> optionalUser = userRepository.findByEmail(request.getEmail());
        boolean isSocial = (request.getProvider() != null && !request.getProvider().trim().isEmpty())
                || (request.getEmail() != null && request.getEmail().startsWith("social_")
                        && request.getEmail().endsWith("@example.com"));

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (isSocial || passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                boolean updated = false;
                if (request.getAddress() != null && !request.getAddress().trim().isEmpty()
                        && (user.getAddress() == null || user.getAddress().trim().isEmpty())) {
                    user.setAddress(request.getAddress());
                    updated = true;
                }
                if (request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isEmpty()
                        && (user.getPhoneNumber() == null || user.getPhoneNumber().trim().isEmpty())) {
                    Optional<User> existingUserByPhone = userRepository.findByPhoneNumber(request.getPhoneNumber());
                    if (existingUserByPhone.isPresent() && !existingUserByPhone.get().getId().equals(user.getId())) {
                        return ResponseEntity.badRequest().body(Map.of("message", "Phone number is already registered!"));
                    }
                    user.setPhoneNumber(request.getPhoneNumber());
                    updated = true;
                }
                if (request.getName() != null && !request.getName().trim().isEmpty()
                        && (user.getName() == null || user.getName().equals("Google User")
                                || user.getName().equals("Apple User") || user.getName().equals("Social User"))) {
                    user.setName(request.getName());
                    updated = true;
                }
                if (updated) {
                    userRepository.save(user);
                }

                return ResponseEntity.ok(Map.of(
                        "id", user.getId(),
                        "name", user.getName(),
                        "email", user.getEmail(),
                        "address", user.getAddress() != null ? user.getAddress() : "",
                        "phoneNumber", user.getPhoneNumber() != null ? user.getPhoneNumber() : "",
                        "role", user.getRole()));
            }
        } else if (isSocial) {
            // Auto-register social user
            if (request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isEmpty()) {
                Optional<User> existingUserByPhone = userRepository.findByPhoneNumber(request.getPhoneNumber());
                if (existingUserByPhone.isPresent()) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Phone number is already registered!"));
                }
            }
            User user = new User();
            user.setEmail(request.getEmail());

            String provider = request.getProvider();
            if (provider == null || provider.trim().isEmpty()) {
                provider = "Social";
                try {
                    String rawProvider = request.getEmail().substring(7, request.getEmail().indexOf("@"));
                    provider = rawProvider.substring(0, 1).toUpperCase() + rawProvider.substring(1);
                } catch (Exception e) {
                    // fallback
                }
            }

            String displayName = request.getName();
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = provider + " User";
            }

            user.setName(displayName);
            String pass = request.getPassword();
            if (pass == null || pass.trim().isEmpty()) {
                pass = "password";
            }
            user.setPassword(passwordEncoder.encode(pass));
            user.setRole("USER");
            user.setAddress(request.getAddress() != null ? request.getAddress() : "");
            user.setPhoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber() : "");

            userRepository.save(user);

            return ResponseEntity.ok(Map.of(
                    "id", user.getId(),
                    "name", user.getName(),
                    "email", user.getEmail(),
                    "address", user.getAddress() != null ? user.getAddress() : "",
                    "phoneNumber", user.getPhoneNumber() != null ? user.getPhoneNumber() : "",
                    "role", user.getRole()));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid email or password!"));
    }

    @GetMapping("/users/count")
    public ResponseEntity<?> getUserCount() {
        long count = userRepository.count();
        return ResponseEntity.ok(Map.of("count", count));
    }
}

class RegisterRequest {
    private String name;
    private String email;
    private String password;
    private String address;
    private String phoneNumber;
    private String otp;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }
}

class LoginRequest {
    private String email;
    private String password;
    private String name;
    private String address;
    private String phoneNumber;
    private String provider;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
