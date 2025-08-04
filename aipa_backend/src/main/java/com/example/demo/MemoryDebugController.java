package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

@RestController
@RequestMapping("/api/memory-debug")
public class MemoryDebugController {

    private final MemoryService memoryService;
    private final UserRepository userRepository;

    @Autowired
    public MemoryDebugController(MemoryService memoryService, UserRepository userRepository) {
        this.memoryService = memoryService;
        this.userRepository = userRepository;
    }
    
    private UUID getUserIdFromAuthentication(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("User not found with email: " + email);
        }
        return user.getId();
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testMemorySystem(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        
        UUID userId = getUserIdFromAuthentication(authentication);
        String testInput = request.get("input");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Test memory analysis
            MemoryAnalysisService.MemoryAnalysisResult analysis = 
                memoryService.analyzeAndStoreMemory(userId, testInput);
            
            result.put("analysis", Map.of(
                "categoryMatch", analysis.getCategoryMatch(),
                "newCategory", analysis.getNewCategorySuggestion(),
                "memoryToStore", analysis.getMemoryToStore(),
                "confidence", analysis.getConfidence(),
                "memoryType", analysis.getMemoryType(),
                "shouldStore", analysis.shouldStore()
            ));
            
            // Get memory stats
            String stats = memoryService.getMemoryStats(userId);
            result.put("memoryStats", stats);
            
            // Get relevant memories
            List<String> relevantMemories = memoryService.getRelevantMemories(userId, testInput);
            result.put("relevantMemories", relevantMemories);
            
            // Get all categories
            List<String> categories = memoryService.getCategories(userId);
            result.put("allCategories", categories);
            
            // Get all memories
            List<String> allMemories = memoryService.getAllMemories(userId);
            result.put("allMemories", allMemories);
            
            result.put("success", true);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/store-test-memory")
    public ResponseEntity<Map<String, Object>> storeTestMemory(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        
        UUID userId = getUserIdFromAuthentication(authentication);
        String category = request.get("category");
        String content = request.get("content");
        
        try {
            Memory memory = memoryService.storeMemory(userId, category, content);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "memoryId", memory.getId().toString(),
                "category", category,
                "content", content
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/clear-all")
    public ResponseEntity<Map<String, Object>> clearAllMemories(Authentication authentication) {
        UUID userId = getUserIdFromAuthentication(authentication);
        
        try {
            List<String> allMemories = memoryService.getAllMemories(userId);
            // Note: We'd need to add a clearAllMemories method to MemoryService for this to work
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Would clear " + allMemories.size() + " memories (not implemented yet)"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}
