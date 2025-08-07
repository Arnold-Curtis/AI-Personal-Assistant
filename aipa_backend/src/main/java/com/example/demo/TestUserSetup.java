package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;
import java.util.logging.Logger;

/**
 * 🧪 TEST USER SETUP CONTROLLER
 * 
 * Creates and manages test users for the testing framework
 */
@RestController
@RequestMapping("/api/testing/setup")
@CrossOrigin(origins = "http://localhost:3000")
public class TestUserSetup {
    
    private static final Logger logger = Logger.getLogger(TestUserSetup.class.getName());
    
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    
    /**
     * Create test user for testing framework
     */
    @PostMapping("/create-test-user")
    public ResponseEntity<Map<String, Object>> createTestUser() {
        logger.info("🧪 Creating test user for testing framework...");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Check if test user already exists
            User existingUser = userRepository.findByEmail("testuser@example.com");
            if (existingUser != null) {
                result.put("success", true);
                result.put("message", "Test user already exists");
                result.put("userId", existingUser.getId().toString());
                return ResponseEntity.ok(result);
            }
            
            // Create new test user
            User testUser = new User();
            testUser.setEmail("testuser@example.com");
            testUser.setPassword(passwordEncoder.encode("testpassword123"));
            testUser.setFullName("Test User");
            testUser.setEmailVerified(true);
            
            User savedUser = userRepository.save(testUser);
            
            result.put("success", true);
            result.put("message", "Test user created successfully");
            result.put("userId", savedUser.getId().toString());
            result.put("email", savedUser.getEmail());
            
            logger.info("✅ Test user created with ID: " + savedUser.getId());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.severe("❌ Error creating test user: " + e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
    
    /**
     * Get test user info
     */
    @GetMapping("/test-user-info")
    public ResponseEntity<Map<String, Object>> getTestUserInfo() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            User testUser = userRepository.findByEmail("testuser@example.com");
            if (testUser == null) {
                result.put("exists", false);
                result.put("message", "Test user does not exist");
            } else {
                result.put("exists", true);
                result.put("userId", testUser.getId().toString());
                result.put("email", testUser.getEmail());
                result.put("fullName", testUser.getFullName());
                result.put("emailVerified", testUser.getEmailVerified());
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            result.put("error", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
    
    /**
     * Clean up test data
     */
    @PostMapping("/cleanup-test-data")
    public ResponseEntity<Map<String, Object>> cleanupTestData() {
        logger.info("🧹 Cleaning up test data...");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            User testUser = userRepository.findByEmail("testuser@example.com");
            if (testUser == null) {
                result.put("success", true);
                result.put("message", "No test user to clean up");
                return ResponseEntity.ok(result);
            }
            
            // Note: We're not actually deleting the user here to avoid breaking the tests
            // Just reporting what would be cleaned up
            result.put("success", true);
            result.put("message", "Test user exists but not deleted (kept for testing)");
            result.put("userId", testUser.getId().toString());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.severe("Error in cleanup: " + e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
}
