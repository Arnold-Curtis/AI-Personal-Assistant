package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.ArrayList;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/memories")
public class MemoryController {
    private static final Logger logger = Logger.getLogger(MemoryController.class.getName());
    
    private final MemoryService memoryService;
    private final UserRepository userRepository;

    @Autowired
    public MemoryController(MemoryService memoryService, UserRepository userRepository) {
        this.memoryService = memoryService;
        this.userRepository = userRepository;
    }
    
    private UUID getUserIdFromAuthentication(Authentication authentication) {
        String email = authentication.getName();
        logger.info("Getting user ID for email: " + email);
        User user = userRepository.findByEmail(email);
        if (user == null) {
            logger.severe("User not found with email: " + email);
            throw new RuntimeException("User not found with email: " + email);
        }
        logger.info("Found user with ID: " + user.getId());
        return user.getId();
    }

    @PostMapping
    public ResponseEntity<Memory> storeMemory(
            Authentication authentication,
            @RequestBody Map<String, String> request) {
        try {
            UUID userId = getUserIdFromAuthentication(authentication);
            String category = request.get("category");
            String content = request.get("content");
            
            logger.info("Storing memory for user ID: " + userId + ", category: " + category);
            
            if (category == null || category.trim().isEmpty()) {
                logger.warning("Category is missing or empty");
                return ResponseEntity.badRequest().build();
            }
            
            if (content == null || content.trim().isEmpty()) {
                logger.warning("Content is missing or empty");
                return ResponseEntity.badRequest().build();
            }
            
            Memory memory = memoryService.storeMemory(userId, category.trim(), content.trim());
            logger.info("Memory stored successfully with ID: " + memory.getId());
            return ResponseEntity.ok(memory);
        } catch (Exception e) {
            logger.severe("Error storing memory: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories(Authentication authentication) {
        UUID userId = getUserIdFromAuthentication(authentication);
        List<String> categories = memoryService.getCategories(userId);
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/categories/details")
    public ResponseEntity<List<Map<String, Object>>> getCategoriesWithCounts(Authentication authentication) {
        UUID userId = getUserIdFromAuthentication(authentication);
        List<Map<String, Object>> categoriesWithCounts = memoryService.getCategoriesWithCounts(userId);
        return ResponseEntity.ok(categoriesWithCounts);
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<String>> getMemoriesByCategory(
            Authentication authentication,
            @PathVariable String category) {
        UUID userId = getUserIdFromAuthentication(authentication);
        List<String> memories = memoryService.getMemoriesByCategory(userId, category);
        return ResponseEntity.ok(memories);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllMemories(Authentication authentication) {
        try {
            UUID userId = getUserIdFromAuthentication(authentication);
            logger.info("Getting all memories for user ID: " + userId);
            
            List<Map<String, Object>> memories = memoryService.getAllMemoriesWithDetails(userId);
            logger.info("Found " + memories.size() + " memories for user");
            
            return ResponseEntity.ok(memories);
        } catch (Exception e) {
            logger.severe("Error getting all memories: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(new ArrayList<>());
        }
    }

    @DeleteMapping("/{memoryId}")
    public ResponseEntity<Void> deactivateMemory(
            Authentication authentication,
            @PathVariable UUID memoryId) {
        memoryService.deactivateMemory(memoryId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/latest/{category}")
    public ResponseEntity<List<String>> getLatestMemoriesByCategory(
            Authentication authentication,
            @PathVariable String category) {
        UUID userId = getUserIdFromAuthentication(authentication);
        List<String> memories = memoryService.getLatestMemoriesByCategory(userId, category);
        return ResponseEntity.ok(memories);
    }

    @PutMapping("/{memoryId}")
    public ResponseEntity<Map<String, Object>> updateMemory(
            Authentication authentication,
            @PathVariable UUID memoryId,
            @RequestBody Map<String, String> request) {
        String content = request.get("content");
        
        boolean updated = memoryService.updateMemory(memoryId, content);
        if (updated) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Memory updated successfully"));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/detail/{memoryId}")
    public ResponseEntity<Map<String, Object>> getMemoryDetail(
            Authentication authentication,
            @PathVariable UUID memoryId) {
        UUID userId = getUserIdFromAuthentication(authentication);
        
        Map<String, Object> memoryDetail = memoryService.getMemoryDetail(userId, memoryId);
        if (memoryDetail != null) {
            return ResponseEntity.ok(memoryDetail);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchMemories(
            Authentication authentication,
            @RequestParam String query,
            @RequestParam(required = false) String category) {
        UUID userId = getUserIdFromAuthentication(authentication);
        List<Map<String, Object>> searchResults = memoryService.searchMemories(userId, query, category);
        return ResponseEntity.ok(searchResults);
    }

    @GetMapping("/debug")
    public ResponseEntity<Map<String, Object>> debugMemories(Authentication authentication) {
        UUID userId = getUserIdFromAuthentication(authentication);
        
        Map<String, Object> debugInfo = new HashMap<>();
        debugInfo.put("userId", userId.toString());
        debugInfo.put("allMemoriesCount", memoryService.getAllMemories(userId).size());
        debugInfo.put("allMemoriesWithDetails", memoryService.getAllMemoriesWithDetails(userId));
        debugInfo.put("categories", memoryService.getCategories(userId));
        debugInfo.put("memoryStats", memoryService.getMemoryStats(userId));
        
        return ResponseEntity.ok(debugInfo);
    }
} 
