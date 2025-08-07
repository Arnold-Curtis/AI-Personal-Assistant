package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.*;
import java.util.logging.Logger;

/**
 * 🔧 MEMORY SYSTEM DIAGNOSTIC AND FIX CONTROLLER
 * 
 * This controller helps diagnose and fix memory storage issues found during testing.
 * Based on the test results showing only 35% memory storage success rate.
 * 
 * Key Issues Found:
 * 1. Memory analysis works but storage condition fails
 * 2. InputRoutingService filtering too aggressively
 * 3. MemoryFilterService value scoring too low
 * 4. LLM analysis not returning "high" or "medium" confidence consistently
 */
@RestController
@RequestMapping("/api/testing/memory-fix")
@CrossOrigin(origins = "http://localhost:3000")
public class TestMemoryFix {
    
    private static final Logger logger = Logger.getLogger(TestMemoryFix.class.getName());
    
    @Autowired private MemoryService memoryService;
    @Autowired private MemoryAnalysisService memoryAnalysisService;
    @Autowired private MemoryFilterService memoryFilterService;
    @Autowired private InputRoutingService inputRoutingService;
    @Autowired private UserRepository userRepository;
    
    /**
     * Diagnostic endpoint to trace memory analysis step by step
     */
    @PostMapping("/diagnose")
    public ResponseEntity<Map<String, Object>> diagnoseMemoryIssue(@RequestBody Map<String, String> request) {
        String testInput = request.get("input");
        logger.info("🔍 Diagnosing memory issue for: " + testInput);
        
        Map<String, Object> diagnosis = new HashMap<>();
        
        try {
            // Get test user
            User testUser = userRepository.findByEmail("testuser@example.com");
            if (testUser == null) {
                diagnosis.put("error", "Test user not found - need to create testuser@example.com");
                return ResponseEntity.ok(diagnosis);
            }
            
            UUID userId = testUser.getId();
            List<String> existingCategories = memoryService.getCategories(userId);
            
            // Step 1: Input Routing Analysis
            InputRoutingService.RoutingDecision routingDecision = inputRoutingService.routeInput(testInput);
            diagnosis.put("step1_routing", Map.of(
                "shouldProcessMemory", routingDecision.shouldProcessMemory(),
                "destination", routingDecision.getDestination().toString(),
                "reasoning", routingDecision.getReasoning()
            ));
            
            // Step 2: Memory Filter Analysis
            MemoryFilterService.MemoryWorthinessResult worthinessResult = 
                memoryFilterService.analyzeMemoryWorthiness(testInput);
            diagnosis.put("step2_filtering", Map.of(
                "isWorthy", worthinessResult.isWorthy(),
                "reason", worthinessResult.getReason(),
                "score", worthinessResult.getScore()
            ));
            
            // Step 3: Full Memory Analysis
            MemoryAnalysisService.MemoryAnalysisResult analysis = 
                memoryAnalysisService.analyzeForMemory(testInput, existingCategories);
            diagnosis.put("step3_analysis", Map.of(
                "categoryMatch", analysis.getCategoryMatch(),
                "newCategory", analysis.getNewCategorySuggestion(),
                "memoryToStore", analysis.getMemoryToStore(),
                "confidence", analysis.getConfidence(),
                "memoryType", analysis.getMemoryType(),
                "shouldStore", analysis.shouldStore()
            ));
            
            // Step 4: Final Storage Test
            MemoryAnalysisService.MemoryAnalysisResult storageTest = 
                memoryService.analyzeAndStoreMemory(userId, testInput);
            diagnosis.put("step4_storage", Map.of(
                "finalShouldStore", storageTest.shouldStore(),
                "finalConfidence", storageTest.getConfidence(),
                "finalMemoryToStore", storageTest.getMemoryToStore()
            ));
            
            // Overall Assessment
            diagnosis.put("summary", Map.of(
                "passedRouting", routingDecision.shouldProcessMemory(),
                "passedFiltering", worthinessResult.isWorthy(),
                "passedAnalysis", analysis.shouldStore(),
                "finalResult", storageTest.shouldStore(),
                "blockingStep", identifyBlockingStep(routingDecision, worthinessResult, analysis)
            ));
            
            return ResponseEntity.ok(diagnosis);
            
        } catch (Exception e) {
            logger.severe("Error in memory diagnosis: " + e.getMessage());
            diagnosis.put("error", e.getMessage());
            return ResponseEntity.ok(diagnosis);
        }
    }
    
    /**
     * Force memory storage with enhanced prompting
     */
    @PostMapping("/force-store")
    public ResponseEntity<Map<String, Object>> forceMemoryStorage(@RequestBody Map<String, String> request) {
        String testInput = request.get("input");
        logger.info("🔧 Force storing memory for: " + testInput);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            User testUser = userRepository.findByEmail("testuser@example.com");
            if (testUser == null) {
                result.put("error", "Test user not found");
                return ResponseEntity.ok(result);
            }
            
            // Enhanced memory analysis with better prompting
            String enhancedInput = enhanceInputForMemoryStorage(testInput);
            
            MemoryAnalysisService.MemoryAnalysisResult analysis = 
                memoryService.analyzeAndStoreMemory(testUser.getId(), enhancedInput);
            
            result.put("success", analysis.shouldStore());
            result.put("originalInput", testInput);
            result.put("enhancedInput", enhancedInput);
            result.put("analysis", Map.of(
                "categoryMatch", analysis.getCategoryMatch(),
                "newCategory", analysis.getNewCategorySuggestion(),
                "memoryToStore", analysis.getMemoryToStore(),
                "confidence", analysis.getConfidence(),
                "shouldStore", analysis.shouldStore()
            ));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.severe("Error in force memory storage: " + e.getMessage());
            result.put("error", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
    
    /**
     * Test batch of failing prompts from the test suite
     */
    @PostMapping("/test-failing-prompts")
    public ResponseEntity<Map<String, Object>> testFailingPrompts() {
        logger.info("🧪 Testing known failing memory prompts...");
        
        String[] failingPrompts = {
            "My birthday is March 15th",
            "I live in Nairobi", 
            "I studied at Strathmore University",
            "I work as a software engineer",
            "My phone number is 555-0123",
            "I drive a Toyota Camry",
            "I live on Oak Street",
            "I graduated in 2018",
            "I was born in Chicago"
        };
        
        Map<String, Object> results = new HashMap<>();
        List<Map<String, Object>> testResults = new ArrayList<>();
        
        try {
            User testUser = userRepository.findByEmail("testuser@example.com");
            if (testUser == null) {
                results.put("error", "Test user not found");
                return ResponseEntity.ok(results);
            }
            
            for (String prompt : failingPrompts) {
                Map<String, Object> testResult = new HashMap<>();
                testResult.put("prompt", prompt);
                
                try {
                    MemoryAnalysisService.MemoryAnalysisResult analysis = 
                        memoryService.analyzeAndStoreMemory(testUser.getId(), prompt);
                    
                    testResult.put("success", analysis.shouldStore());
                    testResult.put("confidence", analysis.getConfidence());
                    testResult.put("memoryToStore", analysis.getMemoryToStore());
                    testResult.put("category", analysis.getCategoryMatch().equals("None") ? 
                        analysis.getNewCategorySuggestion() : analysis.getCategoryMatch());
                    
                } catch (Exception e) {
                    testResult.put("success", false);
                    testResult.put("error", e.getMessage());
                }
                
                testResults.add(testResult);
            }
            
            long successCount = testResults.stream().mapToLong(r -> 
                (Boolean) r.getOrDefault("success", false) ? 1 : 0).sum();
            
            results.put("testResults", testResults);
            results.put("totalTests", failingPrompts.length);
            results.put("successCount", successCount);
            results.put("successRate", (double) successCount / failingPrompts.length * 100);
            
            return ResponseEntity.ok(results);
            
        } catch (Exception e) {
            logger.severe("Error testing failing prompts: " + e.getMessage());
            results.put("error", e.getMessage());
            return ResponseEntity.ok(results);
        }
    }
    
    private String identifyBlockingStep(InputRoutingService.RoutingDecision routing,
                                       MemoryFilterService.MemoryWorthinessResult filtering,
                                       MemoryAnalysisService.MemoryAnalysisResult analysis) {
        if (!routing.shouldProcessMemory()) {
            return "INPUT_ROUTING - Memory processing disabled for this input type";
        }
        if (!filtering.isWorthy()) {
            return "MEMORY_FILTERING - Input deemed not worthy: " + filtering.getReason();
        }
        if (!analysis.shouldStore()) {
            return "ANALYSIS_CONFIDENCE - Memory content is 'None' or filtered (confidence: " + analysis.getConfidence() + ")";
        }
        return "NONE - All steps passed successfully";
    }
    
    private String enhanceInputForMemoryStorage(String originalInput) {
        // Add context that helps the AI understand this is personal information
        if (originalInput.toLowerCase().contains("birthday")) {
            return "This is important personal information to remember: " + originalInput;
        }
        if (originalInput.toLowerCase().contains("live") || originalInput.toLowerCase().contains("work")) {
            return "Personal information about my location/work: " + originalInput;
        }
        if (originalInput.toLowerCase().contains("my") && originalInput.toLowerCase().contains("name")) {
            return "Personal identity information: " + originalInput;
        }
        
        // Generic enhancement
        return "Personal fact to remember: " + originalInput;
    }
}
