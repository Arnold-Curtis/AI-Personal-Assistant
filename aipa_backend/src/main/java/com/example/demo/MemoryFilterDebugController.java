package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/memory-filter-debug")
public class MemoryFilterDebugController {

    private final MemoryFilterService memoryFilterService;
    private final MemoryAnalysisService memoryAnalysisService;
    private final MemoryService memoryService;
    private final UserRepository userRepository;

    @Autowired
    public MemoryFilterDebugController(MemoryFilterService memoryFilterService,
                                     MemoryAnalysisService memoryAnalysisService,
                                     MemoryService memoryService,
                                     UserRepository userRepository) {
        this.memoryFilterService = memoryFilterService;
        this.memoryAnalysisService = memoryAnalysisService;
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

    @PostMapping("/test-filter")
    public ResponseEntity<Map<String, Object>> testMemoryFilter(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        
        String testInput = request.get("input");
        if (testInput == null || testInput.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Input is required"));
        }
        
        try {
            
            MemoryFilterService.MemoryWorthinessResult filterResult = 
                memoryFilterService.analyzeMemoryWorthiness(testInput);
            
            
            UUID userId = getUserIdFromAuthentication(authentication);
            List<String> existingCategories = memoryService.getCategories(userId);
            MemoryAnalysisService.MemoryAnalysisResult analysisResult = 
                memoryAnalysisService.analyzeForMemory(testInput, existingCategories);
            
            Map<String, Object> result = new HashMap<>();
            result.put("input", testInput);
            result.put("filter", Map.of(
                "isWorthy", filterResult.isWorthy(),
                "reason", filterResult.getReason(),
                "score", filterResult.getScore()
            ));
            result.put("analysis", Map.of(
                "shouldStore", analysisResult.shouldStore(),
                "categoryMatch", analysisResult.getCategoryMatch(),
                "newCategory", analysisResult.getNewCategorySuggestion(),
                "memoryToStore", analysisResult.getMemoryToStore(),
                "confidence", analysisResult.getConfidence(),
                "memoryType", analysisResult.getMemoryType()
            ));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/test-batch")
    public ResponseEntity<Map<String, Object>> testBatchInputs(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        
        @SuppressWarnings("unchecked")
        List<String> testInputs = (List<String>) request.get("inputs");
        
        if (testInputs == null || testInputs.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Inputs array is required"));
        }
        
        try {
            UUID userId = getUserIdFromAuthentication(authentication);
            List<String> existingCategories = memoryService.getCategories(userId);
            
            List<Map<String, Object>> results = new ArrayList<>();
            
            for (String testInput : testInputs) {
                MemoryFilterService.MemoryWorthinessResult filterResult = 
                    memoryFilterService.analyzeMemoryWorthiness(testInput);
                
                MemoryAnalysisService.MemoryAnalysisResult analysisResult = 
                    memoryAnalysisService.analyzeForMemory(testInput, existingCategories);
                
                Map<String, Object> testResult = new HashMap<>();
                testResult.put("input", testInput);
                testResult.put("isWorthy", filterResult.isWorthy());
                testResult.put("filterReason", filterResult.getReason());
                testResult.put("filterScore", filterResult.getScore());
                testResult.put("shouldStore", analysisResult.shouldStore());
                testResult.put("confidence", analysisResult.getConfidence());
                testResult.put("memoryToStore", analysisResult.getMemoryToStore());
                
                results.add(testResult);
            }
            
            
            long worthyCount = results.stream().mapToLong(r -> (Boolean) r.get("isWorthy") ? 1 : 0).sum();
            long shouldStoreCount = results.stream().mapToLong(r -> (Boolean) r.get("shouldStore") ? 1 : 0).sum();
            
            Map<String, Object> response = new HashMap<>();
            response.put("results", results);
            response.put("summary", Map.of(
                "totalInputs", testInputs.size(),
                "worthyInputs", worthyCount,
                "filteredInputs", testInputs.size() - worthyCount,
                "wouldStore", shouldStoreCount,
                "filterEffectiveness", String.format("%.1f%%", 
                    (100.0 * (testInputs.size() - shouldStoreCount)) / testInputs.size())
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/test-examples")
    public ResponseEntity<Map<String, Object>> getTestExamples() {
        Map<String, Object> examples = new HashMap<>();
        
        
        List<String> questions = Arrays.asList(
            "when is my wedding again?",
            "what's my dad's name?",
            "where did I put my keys?",
            "can you tell me about my birthday?",
            "do you remember my favorite color?",
            "what time is my appointment?",
            "when is my next meeting?",
            "what's the weather like?",
            "how old am I?",
            "where do I live?"
        );
        
        
        List<String> chatFragments = Arrays.asList(
            "ok",
            "thanks",
            "yes",
            "no",
            "got it",
            "i see",
            "alright",
            "hmm",
            "yeah sure",
            "whatever",
            "123",
            "error",
            "continue"
        );
        
        
        List<String> valuableInfo = Arrays.asList(
            "my birthday is March 15th",
            "my dad's name is Paul",
            "I love playing guitar",
            "I'm learning Spanish",
            "I live in New York",
            "my dog Max is a golden retriever",
            "I work at Google as a software engineer",
            "I graduated from MIT in 2020",
            "my anniversary is June 10th",
            "I have a meeting next Tuesday at 2 PM",
            "I'm allergic to peanuts",
            "my favorite color is blue"
        );
        
        examples.put("questions_to_filter", questions);
        examples.put("chat_fragments_to_filter", chatFragments);
        examples.put("valuable_info_to_store", valuableInfo);
        examples.put("description", "Test cases for memory filtering system");
        
        return ResponseEntity.ok(examples);
    }
}

