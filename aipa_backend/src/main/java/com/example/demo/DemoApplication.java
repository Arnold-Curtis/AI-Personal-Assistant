package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.lang.NonNull;

@SpringBootApplication
@RestController
public class DemoApplication {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public DemoApplication(UserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NonNull CorsRegistry registry) {
                registry.addMapping("/**")
                    .allowedOrigins("http://localhost:3000")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
            }

            @Override
            public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
                registry.addResourceHandler("/uploads/**")
                        .addResourceLocations("file:uploads/");
            }
        };
    }

    @PostMapping("/api/upload")
    public ResponseEntity<?> handleFileUpload(@RequestParam("file") MultipartFile file) {
        try {
            String uploadDir = "uploads";
            Path uploadPath = Paths.get(uploadDir);
            
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Filename cannot be null"));
            }
            
            String fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String uniqueFilename = UUID.randomUUID() + fileExtension;
            Path filePath = uploadPath.resolve(uniqueFilename);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            return ResponseEntity.ok(Map.of(
                "filename", uniqueFilename,
                "url", "/uploads/" + uniqueFilename
            ));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to upload file"));
        }
    }

    @GetMapping("/api/health")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "timestamp", LocalDateTime.now()
        ));
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String email = credentials.get("email");
        String password = credentials.get("password");
        
        if (email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Email and password are required"
            ));
        }
        
        User user = userRepository.findByEmail(email);
        if (user == null || !passwordEncoder().matches(password, user.getPassword())) {
            return ResponseEntity.status(401).body(Map.of(
                "error", "Invalid email or password"
            ));
        }
        
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
        
        return ResponseEntity.ok(createAuthResponse(user));
    }

    @PostMapping("/api/auth/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, Object> credentials) {
        String email = (String) credentials.get("email");
        String password = (String) credentials.get("password");
        String fullName = (String) credentials.get("fullName");
        String profileImage = (String) credentials.get("profileImage");
        
        if (email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Email and password are required"
            ));
        }
        
        if (userRepository.findByEmail(email) != null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Email already exists"
            ));
        }
        
        User newUser = new User();
        newUser.setEmail(email);
        newUser.setPassword(passwordEncoder().encode(password));
        
        if (fullName != null && !fullName.isBlank()) {
            newUser.setFullName(fullName);
        }
        
        if (profileImage != null && !profileImage.isBlank()) {
            newUser.setProfileImage(profileImage);
        }
        
        userRepository.save(newUser);
        
        return ResponseEntity.ok(createAuthResponse(newUser));
    }

    @GetMapping("/api/auth/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
        }
        
        String email = jwtUtil.extractUsername(token);
        User user = userRepository.findByEmail(email);
        
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }
        
        return ResponseEntity.ok(createUserResponse(user));
    }

    @GetMapping("/api/info")
    public String appInfo() {
        return "{\"name\":\"Calendar API\",\"version\":\"1.0\",\"status\":\"running\"}";
    }

    private Map<String, Object> createAuthResponse(User user) {
        UserDetails userDetails = org.springframework.security.core.userdetails.User
            .withUsername(user.getEmail())
            .password(user.getPassword())
            .authorities("USER")
            .build();
        
        Map<String, Object> response = new HashMap<>();
        response.put("token", jwtUtil.generateToken(userDetails));
        response.put("user", createUserResponse(user));
        return response;
    }

    private Map<String, Object> createUserResponse(User user) {
        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put("email", user.getEmail());
        userResponse.put("fullName", user.getFullName() != null ? user.getFullName() : "User");
        
        String profileImageUrl = user.getProfileImage() != null ? 
            "/uploads/" + user.getProfileImage() : 
            "https://ui-avatars.com/api/?name=" + 
            (user.getFullName() != null ? 
                user.getFullName().substring(0, 1) : 
                user.getEmail().substring(0, 1)) + 
            "&background=3b82f6&color=fff";
        
        userResponse.put("profileImageUrl", profileImageUrl);
        return userResponse;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}