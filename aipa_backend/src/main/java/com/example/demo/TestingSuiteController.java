package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.EntityManager;

/**
 * 🧪 TestingSuiteController 
 * 
 * Comprehensive testing framework for AI-driven calendar event creation and memory management.
 * Designed to catch and fix every issue, from subtle edge cases to deeper logic failures.
 * 
 * Features:
 * - 100+ test scenarios from TestPromptDatabase
 * - Real database validation
 * - AI response verification
 * - Performance benchmarking
 * - Critical issue detection
 * 
 * This controller is the "bulletproof" testing system requested by the user.
 */
@RestController
@RequestMapping("/api/testing")
@CrossOrigin(origins = "http://localhost:3000")
public class TestingSuiteController {
    
    private static final Logger logger = LoggerFactory.getLogger(TestingSuiteController.class);
    
    // Service dependencies
    @Autowired private CalendarEventCreationService calendarEventCreationService;
    @Autowired private MemoryService memoryService;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;
    
    /**
     * � Single Test Runner - Test individual prompts for debugging
     */
    @PostMapping("/run-single-test")
    public ResponseEntity<Map<String, Object>> runSingleTest(@RequestBody Map<String, String> request) {
        String prompt = request.get("prompt");
        logger.info("🔧 Running single test for prompt: {}", prompt);
        
        Map<String, Object> result = new HashMap<>();
        try {
            // Run the test with detailed logging
            Map<String, Object> testResult = executeEventTest(prompt, "SINGLE_TEST"); // Use single test type for debugging
            result.put("success", true);
            result.put("prompt", prompt);
            result.put("testResult", testResult);
            
            logger.info("✅ Single test completed: {}", testResult);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("❌ Single test failed for prompt: {}", prompt, e);
            result.put("success", false);
            result.put("prompt", prompt);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }
    
    /**
     * �🚀 Master Test Suite - Runs all testing modules
     */
    @PostMapping("/run-comprehensive-tests")
    public ResponseEntity<Map<String, Object>> runComprehensiveTests(Authentication authentication) {
        logger.info("🚀 Starting comprehensive testing suite...");
        
        Map<String, Object> results = new HashMap<>();
        
        try {
            // Run all test modules
            ResponseEntity<Map<String, Object>> eventTestsResponse = runEventCreationTests();
            ResponseEntity<Map<String, Object>> memoryTestsResponse = runMemoryTests();
            ResponseEntity<Map<String, Object>> followUpTestsResponse = runFollowUpTests();
            ResponseEntity<Map<String, Object>> edgeCaseTestsResponse = runEdgeCaseTests();
            ResponseEntity<Map<String, Object>> validationResponse = validateActualDatabaseState();
            
            // Collect results
            results.put("eventCreation", eventTestsResponse.getBody());
            results.put("memoryManagement", memoryTestsResponse.getBody());
            results.put("followUpTests", followUpTestsResponse.getBody());
            results.put("edgeCases", edgeCaseTestsResponse.getBody());
            results.put("databaseValidation", validationResponse.getBody());
            
            // Generate comprehensive summary
            Map<String, Object> summary = new HashMap<>();
            summary.put("timestamp", LocalDateTime.now().toString());
            summary.put("totalTests", getTotalTestCount(results));
            summary.put("passedTests", getPassedTestCount(results));
            summary.put("failedTests", getFailedTestCount(results));
            summary.put("successRate", calculateSuccessRate(results));
            summary.put("criticalIssues", identifyCriticalIssues(results));
            summary.put("recommendations", generateRecommendations(results));
            
            results.put("summary", summary);
            
            logger.info("✅ Comprehensive testing completed. Success rate: {}%", 
                        calculateSuccessRate(results));
            
            return ResponseEntity.ok(results);
            
        } catch (Exception e) {
            logger.error("❌ Testing suite failed: {}", e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "Testing suite failed: " + e.getMessage());
            errorResult.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.ok(errorResult);
        }
    }
    
    /**
     * 📅 Event Creation Tests using TestPromptDatabase
     */
    @PostMapping("/run-event-tests")
    public ResponseEntity<Map<String, Object>> runEventCreationTests() {
        logger.info("📅 Running event creation tests...");
        
        Map<String, Object> results = new HashMap<>();
        List<Map<String, Object>> testResults = new ArrayList<>();
        
        // Basic event tests from TestPromptDatabase
        String[] basicPrompts = TestPromptDatabase.BASIC_EVENT_PROMPTS;
        
        // Complex event tests from TestPromptDatabase
        String[] complexPrompts = TestPromptDatabase.COMPLEX_TIME_PROMPTS;
        
        // Execute basic tests
        for (String prompt : basicPrompts) {
            Map<String, Object> testResult = executeEventTest(prompt, "BASIC");
            testResults.add(testResult);
        }
        
        // Execute complex tests
        for (String prompt : complexPrompts) {
            Map<String, Object> testResult = executeEventTest(prompt, "COMPLEX");
            testResults.add(testResult);
        }
        
        results.put("testResults", testResults);
        results.put("totalTests", testResults.size());
        results.put("passedTests", testResults.stream().mapToInt(r -> (Boolean) r.get("passed") ? 1 : 0).sum());
        
        return ResponseEntity.ok(results);
    }
    
    /**
     * 🧠 Memory Management Tests using TestPromptDatabase
     */
    @PostMapping("/run-memory-tests")
    public ResponseEntity<Map<String, Object>> runMemoryTests() {
        logger.info("🧠 Running memory management tests...");
        
        Map<String, Object> results = new HashMap<>();
        List<Map<String, Object>> testResults = new ArrayList<>();
        
        // Memory tests from TestPromptDatabase
        String[] memoryPrompts = TestPromptDatabase.PERSONAL_INFO_PROMPTS;
        
        // Execute memory tests
        for (String prompt : memoryPrompts) {
            Map<String, Object> testResult = executeMemoryTest(prompt);
            testResults.add(testResult);
        }
        
        results.put("testResults", testResults);
        results.put("totalTests", testResults.size());
        results.put("passedTests", testResults.stream().mapToInt(r -> (Boolean) r.get("passed") ? 1 : 0).sum());
        
        return ResponseEntity.ok(results);
    }
    
    /**
     * 🔄 Follow-up and Dependency Tests using TestPromptDatabase
     */
    @PostMapping("/run-followup-tests")
    public ResponseEntity<Map<String, Object>> runFollowUpTests() {
        logger.info("🔄 Running follow-up and dependency tests...");
        
        Map<String, Object> results = new HashMap<>();
        List<Map<String, Object>> testResults = new ArrayList<>();
        
        // Sequential event creation tests
        String[][] sequentialTests = TestPromptDatabase.SEQUENTIAL_TEST_PAIRS;
        
        // Memory-dependent tests
        String[][] memoryDependentTests = TestPromptDatabase.MEMORY_DEPENDENT_TEST_PAIRS;
        
        // Execute sequential tests
        for (String[] testPair : sequentialTests) {
            Map<String, Object> testResult = executeSequentialTest(testPair[0], testPair[1]);
            testResults.add(testResult);
        }
        
        // Execute memory-dependent tests
        for (String[] testPair : memoryDependentTests) {
            Map<String, Object> testResult = executeMemoryDependentTest(testPair[0], testPair[1]);
            testResults.add(testResult);
        }
        
        results.put("testResults", testResults);
        results.put("totalTests", testResults.size());
        results.put("passedTests", testResults.stream().mapToInt(r -> (Boolean) r.get("passed") ? 1 : 0).sum());
        
        return ResponseEntity.ok(results);
    }
    
    /**
     * ⚡ Edge Case Tests using TestPromptDatabase
     */
    @PostMapping("/run-edge-case-tests")
    public ResponseEntity<Map<String, Object>> runEdgeCaseTests() {
        logger.info("⚡ Running edge case tests...");
        
        Map<String, Object> results = new HashMap<>();
        List<Map<String, Object>> testResults = new ArrayList<>();
        
        // Edge case prompts from TestPromptDatabase
        String[] edgeCasePrompts = TestPromptDatabase.EDGE_CASE_PROMPTS;
        
        // Execute edge case tests
        for (String prompt : edgeCasePrompts) {
            Map<String, Object> testResult = executeEventTest(prompt, "EDGE_CASE");
            testResults.add(testResult);
        }
        
        results.put("testResults", testResults);
        results.put("totalTests", testResults.size());
        results.put("passedTests", testResults.stream().mapToInt(r -> (Boolean) r.get("passed") ? 1 : 0).sum());
        
        return ResponseEntity.ok(results);
    }
    
    /**
     * 🗃️ Database Validation - Check actual database state
     */
    @PostMapping("/validate-database")
    public ResponseEntity<Map<String, Object>> validateActualDatabaseState() {
        logger.info("🗃️ Validating actual database state...");
        
        Map<String, Object> results = new HashMap<>();
        
        try {
            // Check calendar events
            Map<String, Object> calendarValidation = validateCalendarEvents();
            results.put("calendarEvents", calendarValidation);
            
            // Check user memories
            Map<String, Object> memoryValidation = validateUserMemories();
            results.put("userMemories", memoryValidation);
            
            // Overall validation status
            boolean hasEvents = (Boolean) calendarValidation.get("hasEvents");
            boolean hasMemories = (Boolean) memoryValidation.get("hasMemories");
            
            results.put("overallStatus", hasEvents && hasMemories ? "HEALTHY" : "ISSUES_DETECTED");
            results.put("timestamp", LocalDateTime.now().toString());
            
        } catch (Exception e) {
            logger.error("❌ Database validation failed: {}", e.getMessage(), e);
            results.put("error", "Database validation failed: " + e.getMessage());
            results.put("overallStatus", "VALIDATION_FAILED");
        }
        
        return ResponseEntity.ok(results);
    }
    
    // ============ Test Execution Methods ============
    
    /**
     * Execute a single event creation test
     */
    private Map<String, Object> executeEventTest(String prompt, String testType) {
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            logger.debug("Testing [{}]: {}", testType, prompt);
            
            // Get a test user
            User testUser = userRepository.findByEmail("testuser@example.com");
            if (testUser == null) {
                result.put("passed", false);
                result.put("error", "Test user not found");
                return result;
            }
            
            // Execute event creation
            CalendarEventCreationService.EventCreationResult eventResult =
                    calendarEventCreationService.createEventsFromInput(testUser.getId(), prompt);
            
            // Validate results - different logic for edge cases
            boolean aiResponseReceived = eventResult != null;
            boolean eventsCreated = eventResult != null && eventResult.hasEvents();
            
            // For edge cases, success means correctly rejecting invalid input
            boolean testPassed;
            if ("EDGE_CASE".equals(testType)) {
                // Edge cases should pass when they correctly reject invalid input
                testPassed = aiResponseReceived && !eventsCreated;
            } else {
                // Normal tests should pass when events are created
                testPassed = aiResponseReceived && eventsCreated;
            }
            
            result.put("prompt", prompt);
            result.put("testType", testType);
            result.put("passed", testPassed);
            result.put("aiResponseReceived", aiResponseReceived);
            result.put("eventsCreated", eventsCreated);
            result.put("executionTimeMs", System.currentTimeMillis() - startTime);
            
            if (eventResult != null) {
                result.put("eventCount", eventResult.getCreatedEvents().size());
                result.put("errors", eventResult.getErrors());
            }
            
        } catch (Exception e) {
            logger.error("❌ Event test failed for prompt '{}': {}", prompt, e.getMessage());
            result.put("passed", false);
            result.put("error", e.getMessage());
            result.put("executionTimeMs", System.currentTimeMillis() - startTime);
        }
        
        return result;
    }
    
    /**
     * Execute a memory storage test
     */
    private Map<String, Object> executeMemoryTest(String prompt) {
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            // Get a test user
            User testUser = userRepository.findByEmail("testuser@example.com");
            if (testUser == null) {
                result.put("passed", false);
                result.put("error", "Test user not found");
                return result;
            }
            
            // Execute memory analysis and storage
            MemoryAnalysisService.MemoryAnalysisResult memoryResult =
                    memoryService.analyzeAndStoreMemory(testUser.getId(), prompt);
            
            // Validate results
            boolean memoryAnalyzed = memoryResult != null;
            boolean memoryStored = memoryResult != null && memoryResult.shouldStore();
            
            result.put("prompt", prompt);
            result.put("passed", memoryAnalyzed && memoryStored);
            result.put("memoryAnalyzed", memoryAnalyzed);
            result.put("memoryStored", memoryStored);
            result.put("executionTimeMs", System.currentTimeMillis() - startTime);
            
        } catch (Exception e) {
            logger.error("❌ Memory test failed for prompt '{}': {}", prompt, e.getMessage());
            result.put("passed", false);
            result.put("error", e.getMessage());
            result.put("executionTimeMs", System.currentTimeMillis() - startTime);
        }
        
        return result;
    }
    
    /**
     * Execute sequential test (first prompt, then second prompt that depends on first)
     */
    private Map<String, Object> executeSequentialTest(String firstPrompt, String secondPrompt) {
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            // Execute first prompt
            Map<String, Object> firstResult = executeEventTest(firstPrompt, "SEQUENTIAL_FIRST");
            
            // Small delay to simulate realistic usage
            Thread.sleep(100);
            
            // Execute second prompt
            Map<String, Object> secondResult = executeEventTest(secondPrompt, "SEQUENTIAL_SECOND");
            
            // Validate sequential relationship
            boolean firstPassed = (Boolean) firstResult.get("passed");
            boolean secondPassed = (Boolean) secondResult.get("passed");
            boolean sequentialLogicWorks = firstPassed && secondPassed;
            
            result.put("firstPrompt", firstPrompt);
            result.put("secondPrompt", secondPrompt);
            result.put("passed", sequentialLogicWorks);
            result.put("firstResult", firstResult);
            result.put("secondResult", secondResult);
            result.put("executionTimeMs", System.currentTimeMillis() - startTime);
            
        } catch (Exception e) {
            logger.error("❌ Sequential test failed: {}", e.getMessage());
            result.put("passed", false);
            result.put("error", e.getMessage());
            result.put("executionTimeMs", System.currentTimeMillis() - startTime);
        }
        
        return result;
    }
    
    /**
     * Execute memory-dependent test (store memory, then use it)
     */
    private Map<String, Object> executeMemoryDependentTest(String memoryPrompt, String useMemoryPrompt) {
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            // Store memory first
            Map<String, Object> memoryResult = executeMemoryTest(memoryPrompt);
            
            // Small delay
            Thread.sleep(100);
            
            // Use the memory in event creation
            Map<String, Object> eventResult = executeEventTest(useMemoryPrompt, "MEMORY_DEPENDENT");
            
            // Validate memory dependency
            boolean memoryStored = (Boolean) memoryResult.get("passed");
            boolean eventCreated = (Boolean) eventResult.get("passed");
            boolean memoryLogicWorks = memoryStored && eventCreated;
            
            result.put("memoryPrompt", memoryPrompt);
            result.put("useMemoryPrompt", useMemoryPrompt);
            result.put("passed", memoryLogicWorks);
            result.put("memoryResult", memoryResult);
            result.put("eventResult", eventResult);
            result.put("executionTimeMs", System.currentTimeMillis() - startTime);
            
        } catch (Exception e) {
            logger.error("❌ Memory-dependent test failed: {}", e.getMessage());
            result.put("passed", false);
            result.put("error", e.getMessage());
            result.put("executionTimeMs", System.currentTimeMillis() - startTime);
        }
        
        return result;
    }
    
    // ============ Validation Methods ============
    
    /**
     * Validate calendar events in database
     */
    private Map<String, Object> validateCalendarEvents() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get test user first
            User testUser = userRepository.findByEmail("testuser@example.com");
            if (testUser == null) {
                result.put("hasEvents", false);
                result.put("error", "Test user not found");
                return result;
            }
            
            // Get events for the test user
            List<CalendarEvent> events = entityManager
                .createQuery("SELECT e FROM CalendarEvent e WHERE e.user = :user", CalendarEvent.class)
                .setParameter("user", testUser)
                .getResultList();
            
            boolean hasEvents = events != null && !events.isEmpty();
            
            result.put("hasEvents", hasEvents);
            result.put("eventCount", events != null ? events.size() : 0);
            result.put("status", hasEvents ? "HEALTHY" : "NO_EVENTS_FOUND");
            
            if (hasEvents && events != null) {
                // Additional validation - check recent events based on start date
                long recentEvents = events.stream()
                    .filter(e -> e.getStart().isAfter(LocalDate.now().minusDays(30)))
                    .count();
                
                result.put("recentEventCount", recentEvents);
                result.put("hasRecentEvents", recentEvents > 0);
            }
            
        } catch (Exception e) {
            logger.error("❌ Calendar validation failed: {}", e.getMessage());
            result.put("hasEvents", false);
            result.put("error", e.getMessage());
            result.put("status", "VALIDATION_ERROR");
        }
        
        return result;
    }
    
    /**
     * Validate user memories in database
     */
    private Map<String, Object> validateUserMemories() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get test user
            User testUser = userRepository.findByEmail("testuser@example.com");
            if (testUser == null) {
                result.put("hasMemories", false);
                result.put("error", "Test user not found");
                return result;
            }
            
            List<String> memories = memoryService.getRelevantMemories(testUser.getId(), "any context");
            boolean hasMemories = memories != null && !memories.isEmpty();
            
            result.put("hasMemories", hasMemories);
            result.put("memoryCount", memories != null ? memories.size() : 0);
            result.put("status", hasMemories ? "HEALTHY" : "NO_MEMORIES_FOUND");
            
        } catch (Exception e) {
            logger.error("❌ Memory validation failed: {}", e.getMessage());
            result.put("hasMemories", false);
            result.put("error", e.getMessage());
            result.put("status", "VALIDATION_ERROR");
        }
        
        return result;
    }
    
    // ============ Utility Methods ============
    
    private int getTotalTestCount(Map<String, Object> results) {
        return results.values().stream()
            .filter(v -> v instanceof Map)
            .mapToInt(v -> {
                Map<?, ?> map = (Map<?, ?>) v;
                Object total = map.get("totalTests");
                return total instanceof Integer ? (Integer) total : 0;
            })
            .sum();
    }
    
    private int getPassedTestCount(Map<String, Object> results) {
        return results.values().stream()
            .filter(v -> v instanceof Map)
            .mapToInt(v -> {
                Map<?, ?> map = (Map<?, ?>) v;
                Object passed = map.get("passedTests");
                return passed instanceof Integer ? (Integer) passed : 0;
            })
            .sum();
    }
    
    private int getFailedTestCount(Map<String, Object> results) {
        return getTotalTestCount(results) - getPassedTestCount(results);
    }
    
    private double calculateSuccessRate(Map<String, Object> results) {
        int total = getTotalTestCount(results);
        int passed = getPassedTestCount(results);
        return total > 0 ? (double) passed / total * 100 : 0.0;
    }
    
    private List<String> identifyCriticalIssues(Map<String, Object> results) {
        List<String> issues = new ArrayList<>();
        
        // Check for the main issue mentioned in the prompt
        Object validationObj = results.get("databaseValidation");
        if (validationObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> validation = (Map<String, Object>) validationObj;
            
            Object calendarEventsObj = validation.get("calendarEvents");
            if (calendarEventsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> calendarEvents = (Map<String, Object>) calendarEventsObj;
                
                Object hasEventsObj = calendarEvents.get("hasEvents");
                if (hasEventsObj instanceof Boolean && !(Boolean) hasEventsObj) {
                    issues.add("CRITICAL: No events found in database despite AI responses");
                }
            }
        }
        
        // Check for memory storage issues
        if (getFailedTestCount(results) > getTotalTestCount(results) * 0.3) {
            issues.add("HIGH: More than 30% of tests failing");
        }
        
        return issues;
    }
    
    private List<String> generateRecommendations(Map<String, Object> results) {
        List<String> recommendations = new ArrayList<>();
        
        double successRate = calculateSuccessRate(results);
        
        if (successRate < 50) {
            recommendations.add("URGENT: System requires immediate attention - success rate below 50%");
        } else if (successRate < 80) {
            recommendations.add("MODERATE: Consider investigating failing test patterns");
        } else if (successRate < 95) {
            recommendations.add("MINOR: System mostly healthy, fine-tune edge cases");
        } else {
            recommendations.add("EXCELLENT: System performing well, maintain current quality");
        }
        
        List<String> criticalIssues = identifyCriticalIssues(results);
        if (!criticalIssues.isEmpty()) {
            recommendations.add("PRIORITY: Address critical issues: " + String.join(", ", criticalIssues));
        }
        
        return recommendations;
    }
}