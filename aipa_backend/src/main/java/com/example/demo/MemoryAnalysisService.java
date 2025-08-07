package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.logging.Logger;

@Service
public class MemoryAnalysisService {
    
    private static final Logger logger = Logger.getLogger(MemoryAnalysisService.class.getName());
    
    private final MemoryFilterService memoryFilterService;
    private final InputRoutingService inputRoutingService;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${gemini.api.key}")
    private String geminiApiKey;
    
    @Autowired
    public MemoryAnalysisService(MemoryFilterService memoryFilterService, InputRoutingService inputRoutingService, WebClient.Builder webClientBuilder) {
        this.memoryFilterService = memoryFilterService;
        this.inputRoutingService = inputRoutingService;
        this.webClient = webClientBuilder
            .baseUrl("https://generativelanguage.googleapis.com")
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
    
    
    public static final String ENHANCED_MEMORY_PROMPT = 
        "Extract personal information from user input. BE EXTREMELY GENEROUS - extract ANYTHING personal about the user!\n\n" +
        
        "ALWAYS extract if user mentions:\n" +
        "• Personal identity: names, age, birthday, location, job, education, family\n" +
        "• Education: university, college, school, degree, studied, graduated, courses\n" +
        "• Professional: job title, company, workplace, career, profession, skills\n" +
        "• Relationships: parents, siblings, spouse, friends, colleagues, pets\n" +
        "• Preferences: favorite foods/movies/colors, likes/dislikes, hobbies\n" +
        "• Personal facts: where they live/work, what they own, goals, health info\n" +
        "• Contact info: phone numbers, emails, addresses\n\n" +
        
        "Use HIGH confidence for clear personal facts, MEDIUM for preferences.\n" +
        "Only use 'None' for pure questions or completely irrelevant content.\n\n" +
        
        "Categories: %s\n\n" +
        
        "Return JSON:\n" +
        "{\"categoryMatch\":\"match_or_None\",\"newCategorySuggestion\":\"suggestion_or_None\",\"memoryToStore\":\"fact_or_None\",\"confidence\":\"high_medium_or_low\",\"memoryType\":\"personal_fact_preference_or_goal\"}\n\n" +
        
        "Examples:\n" +
        "\"I live in Paris\" → {\"categoryMatch\":\"None\",\"newCategorySuggestion\":\"Personal\",\"memoryToStore\":\"Lives in Paris\",\"confidence\":\"high\",\"memoryType\":\"personal_fact\"}\n" +
        "\"I'm a teacher\" → {\"categoryMatch\":\"None\",\"newCategorySuggestion\":\"Work\",\"memoryToStore\":\"Works as a teacher\",\"confidence\":\"high\",\"memoryType\":\"personal_fact\"}\n" +
        "\"My name is Alice\" → {\"categoryMatch\":\"None\",\"newCategorySuggestion\":\"Personal\",\"memoryToStore\":\"Name is Alice\",\"confidence\":\"high\",\"memoryType\":\"personal_fact\"}\n" +
        "\"I love pizza\" → {\"categoryMatch\":\"None\",\"newCategorySuggestion\":\"Preferences\",\"memoryToStore\":\"Loves pizza\",\"confidence\":\"medium\",\"memoryType\":\"preference\"}\n" +
        "\"My phone is 555-1234\" → {\"categoryMatch\":\"None\",\"newCategorySuggestion\":\"Contact\",\"memoryToStore\":\"Phone number is 555-1234\",\"confidence\":\"high\",\"memoryType\":\"personal_fact\"}\n" +
        "\"I studied at Harvard\" → {\"categoryMatch\":\"None\",\"newCategorySuggestion\":\"Education\",\"memoryToStore\":\"Studied at Harvard\",\"confidence\":\"high\",\"memoryType\":\"personal_fact\"}\n\n" +
        
        "Input: \"%s\"\n" +
        "JSON:";
    
    public MemoryAnalysisResult analyzeForMemory(String userInput, List<String> existingCategories) {
        try {
            InputRoutingService.RoutingDecision routingDecision = inputRoutingService.routeInput(userInput);
            
            if (!routingDecision.shouldProcessMemory()) {
                String reason = routingDecision.getDestination() == InputRoutingService.RoutingDestination.CALENDAR_ONLY 
                    ? "routed_to_calendar" : "not_memory_worthy";
                return new MemoryAnalysisResult("None", "None", "None", reason, "None");
            }
            
            MemoryFilterService.MemoryWorthinessResult worthinessResult = 
                memoryFilterService.analyzeMemoryWorthiness(userInput);
            
            if (!worthinessResult.isWorthy()) {
                return new MemoryAnalysisResult("None", "None", "None", "filtered", "None");
            }
            
            return extractMemoryUsingLLM(userInput, existingCategories);
            
        } catch (Exception e) {
            logger.severe("Error in memory analysis: " + e.getMessage());
            return new MemoryAnalysisResult("None", "None", "None", "low", "None");
        }
    }
    
    private MemoryAnalysisResult extractMemoryUsingLLM(String userInput, List<String> existingCategories) {
        try {
            String categoriesStr = existingCategories.isEmpty() ? "None" : String.join(", ", existingCategories);
            String prompt = String.format(ENHANCED_MEMORY_PROMPT, categoriesStr, userInput);
            
            Map<String, Object> request = Map.of(
                "contents", List.of(
                    Map.of("parts", List.of(
                        Map.of("text", prompt)
                    ))
                ),
                "generationConfig", Map.of(
                    "temperature", 0.4,  // Increased from 0.3 for even more creative extraction
                    "topP", 0.95,        // Increased from 0.9 for more diverse responses
                    "maxOutputTokens", 300 // Increased from 256 for more detailed responses
                )
            );
            
            JsonNode response = webClient.post()
                .uri(uriBuilder -> uriBuilder
                    .path("/v1beta/models/gemini-2.0-flash:generateContent")  // Use advanced model
                    .queryParam("key", geminiApiKey)
                    .build())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            
            String responseText = response
                .path("candidates").get(0)
                .path("content")
                .path("parts").get(0)
                .path("text")
                .asText();
            
            return parseMemoryAnalysisResponse(responseText);
            
        } catch (Exception e) {
            logger.severe("Error extracting memory using LLM: " + e.getMessage());
            return extractMemoryUsingPatterns(userInput, existingCategories);
        }
    }
    
    private MemoryAnalysisResult parseMemoryAnalysisResponse(String responseText) {
        try {
            String cleanedResponse = responseText.trim();
            if (cleanedResponse.startsWith("```json")) {
                cleanedResponse = cleanedResponse.substring(7);
            }
            if (cleanedResponse.endsWith("```")) {
                cleanedResponse = cleanedResponse.substring(0, cleanedResponse.length() - 3);
            }
            cleanedResponse = cleanedResponse.trim();
            
            JsonNode jsonResponse = objectMapper.readTree(cleanedResponse);
            
            String categoryMatch = jsonResponse.path("categoryMatch").asText("None");
            String newCategorySuggestion = jsonResponse.path("newCategorySuggestion").asText("None");
            String memoryToStore = jsonResponse.path("memoryToStore").asText("None");
            String confidence = jsonResponse.path("confidence").asText("low");
            String memoryType = jsonResponse.path("memoryType").asText("None");
            
            return new MemoryAnalysisResult(categoryMatch, newCategorySuggestion, memoryToStore, confidence, memoryType);
            
        } catch (Exception e) {
            logger.warning("Error parsing LLM memory analysis response: " + e.getMessage() + ", Response: " + responseText);
            return new MemoryAnalysisResult("None", "None", "None", "low", "None");
        }
    }
    
    private boolean containsPersonalInfo(String input) {
        String lowerInput = input.toLowerCase();
        
        
        if (lowerInput.matches(".*\\b(birthday|born|birth)\\b.*") ||
            lowerInput.matches(".*\\b(january|february|march|april|may|june|july|august|september|october|november|december)\\s+\\d{1,2}.*") ||
            lowerInput.matches(".*\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}.*")) {
            return true;
        }
        
        
        if (lowerInput.matches(".*\\b(my name is|i'm|i am|call me)\\s+[A-Za-z]+.*") ||
            lowerInput.matches(".*\\b(my \\w+ is named|my \\w+'s name is)\\s+[A-Za-z]+.*")) {
            return true;
        }
        
        
        if (lowerInput.matches(".*\\b(i love|i like|i enjoy|i prefer|my favorite)\\b.*") ||
            lowerInput.matches(".*\\b(i hate|i dislike|i don't like)\\b.*")) {
            return true;
        }
        
        
        if (lowerInput.matches(".*\\b(i want to|i'm learning|i'm studying|my goal is)\\b.*") ||
            lowerInput.matches(".*\\b(i'm trying to|i hope to|i plan to)\\b.*")) {
            return true;
        }
        
        
        if (lowerInput.matches(".*\\b(i live in|i work at|i'm from|my address)\\b.*") ||
            lowerInput.matches(".*\\b(my job|my work|my career)\\b.*")) {
            return true;
        }
        
        return false;
    }
    
    private MemoryAnalysisResult createHighConfidenceResult(String input, List<String> existingCategories) {
        String lowerInput = input.toLowerCase();
        
        
        if (lowerInput.contains("birthday") || lowerInput.contains("born") || 
            lowerInput.matches(".*\\b(january|february|march|april|may|june|july|august|september|october|november|december)\\s+\\d{1,2}.*")) {
            String categoryMatch = findBestCategoryMatch(existingCategories, Arrays.asList("personal", "info", "birthday", "date"));
            return new MemoryAnalysisResult(
                categoryMatch != null ? categoryMatch : "None",
                categoryMatch == null ? "Personal_Information" : "None",
                "Birthday mentioned: " + input.trim(),
                "high",
                "personal_info"
            );
        }
        
        
        if (lowerInput.contains("learning") || lowerInput.contains("want to learn") || 
            lowerInput.contains("studying") || lowerInput.contains("goal")) {
            String categoryMatch = findBestCategoryMatch(existingCategories, Arrays.asList("goals", "learning", "education", "aspirations"));
            return new MemoryAnalysisResult(
                categoryMatch != null ? categoryMatch : "None",
                categoryMatch == null ? "Goals_And_Learning" : "None",
                "Learning goal mentioned: " + input.trim(),
                "high",
                "goal"
            );
        }
        
        
        if (lowerInput.contains("love") || lowerInput.contains("like") || 
            lowerInput.contains("favorite") || lowerInput.contains("prefer")) {
            String categoryMatch = findBestCategoryMatch(existingCategories, Arrays.asList("preferences", "likes", "favorites"));
            return new MemoryAnalysisResult(
                categoryMatch != null ? categoryMatch : "None",
                categoryMatch == null ? "Preferences" : "None",
                "Preference mentioned: " + input.trim(),
                "high",
                "preference"
            );
        }
        
        
        String categoryMatch = findBestCategoryMatch(existingCategories, Arrays.asList("personal", "info"));
        return new MemoryAnalysisResult(
            categoryMatch != null ? categoryMatch : "None",
            categoryMatch == null ? "Personal_Information" : "None",
            input.trim(),
            "medium",
            "fact"
        );
    }
    
    private MemoryAnalysisResult extractMemoryUsingPatterns(String input, List<String> existingCategories) {
        String lowerInput = input.toLowerCase().trim();
        
        if (lowerInput.length() < 5) {
            return new MemoryAnalysisResult("None", "None", "None", "low", "None");
        }
        
        // Enhanced pattern matching with more aggressive extraction
        
        // Location patterns
        if (lowerInput.matches(".*\\b(i live in|i'm from|i am from|my home|my city|my country)\\b.*") ||
            lowerInput.matches(".*\\b(live in|from|based in)\\s+[A-Za-z]+.*")) {
            return new MemoryAnalysisResult("None", "Location", input.trim(), "medium", "personal_fact");
        }
        
        // Job/profession patterns  
        if (lowerInput.matches(".*\\b(i work as|i am a|i'm a|my job|my work|my career|i do|profession)\\b.*") ||
            lowerInput.matches(".*\\b(work at|employed at|job at)\\b.*")) {
            return new MemoryAnalysisResult("None", "Work", input.trim(), "medium", "personal_fact");
        }
        
        // Name patterns
        if (lowerInput.matches(".*\\b(my name is|i am|i'm|call me)\\s+[A-Za-z]+.*") ||
            lowerInput.matches(".*\\b(name is|called)\\s+[A-Za-z]+.*")) {
            return new MemoryAnalysisResult("None", "Personal", input.trim(), "high", "personal_fact");
        }
        
        // Family/relationship patterns
        if (lowerInput.matches(".*\\b(my (dad|father|mom|mother|brother|sister|wife|husband|son|daughter|friend|boyfriend|girlfriend))\\b.*") ||
            lowerInput.matches(".*\\b(family|relative|spouse|partner)\\b.*")) {
            return new MemoryAnalysisResult("None", "Family", input.trim(), "medium", "personal_fact");
        }
        
        // Preferences patterns
        if (lowerInput.matches(".*\\b(i love|i like|i enjoy|i prefer|my favorite|i hate|i dislike)\\b.*")) {
            return new MemoryAnalysisResult("None", "Preferences", input.trim(), "medium", "preference");
        }
        
        // Learning/goals patterns
        if (lowerInput.matches(".*\\b(i want to|i'm learning|i'm studying|my goal|i hope to|i plan to|i'm trying to)\\b.*")) {
            return new MemoryAnalysisResult("None", "Goals", input.trim(), "medium", "goal");
        }
        
        // Age/birthday patterns
        if (lowerInput.matches(".*\\b(i am \\d+|age \\d+|years old|birthday|born in|born on)\\b.*")) {
            return new MemoryAnalysisResult("None", "Personal", input.trim(), "high", "personal_fact");
        }
        
        // Contact information patterns
        if (lowerInput.matches(".*\\b(phone|email|address|contact)\\b.*") && 
            lowerInput.matches(".*\\b(my|is|number)\\b.*")) {
            return new MemoryAnalysisResult("None", "Contact", input.trim(), "high", "personal_fact");
        }
        
        // Education patterns
        if (lowerInput.matches(".*\\b(studied|graduated|degree|university|college|school)\\b.*")) {
            return new MemoryAnalysisResult("None", "Education", input.trim(), "medium", "personal_fact");
        }
        
        // Possession patterns
        if (lowerInput.matches(".*\\b(my (car|house|apartment|bike|motorcycle))\\b.*") ||
            lowerInput.matches(".*\\b(i (own|have|drive))\\b.*")) {
            return new MemoryAnalysisResult("None", "Personal", input.trim(), "medium", "personal_fact");
        }
        
        // General personal information (if contains "I" and personal indicators)
        if (lowerInput.matches(".*\\b(i|my|me)\\b.*") && 
            (lowerInput.contains("is") || lowerInput.contains("have") || lowerInput.contains("am") || 
             lowerInput.contains("was") || lowerInput.contains("will") || lowerInput.contains("like") ||
             lowerInput.contains("work") || lowerInput.contains("live"))) {
            return new MemoryAnalysisResult("None", "Personal", input.trim(), "medium", "personal_fact");
        }
        
        return new MemoryAnalysisResult("None", "None", "None", "low", "None");
    }
    
    private String findBestCategoryMatch(List<String> existingCategories, List<String> keywords) {
        for (String category : existingCategories) {
            String lowerCategory = category.toLowerCase();
            for (String keyword : keywords) {
                if (lowerCategory.contains(keyword.toLowerCase()) || 
                    keyword.toLowerCase().contains(lowerCategory)) {
                    return category;
                }
            }
        }
        return null;
    }
    

    

    

    
    public static class MemoryAnalysisResult {
        private final String categoryMatch;
        private final String newCategorySuggestion;
        private final String memoryToStore;
        private final String confidence;
        private final String memoryType;
        
        public MemoryAnalysisResult(String categoryMatch, String newCategorySuggestion, 
                                  String memoryToStore, String confidence, String memoryType) {
            this.categoryMatch = categoryMatch;
            this.newCategorySuggestion = newCategorySuggestion;
            this.memoryToStore = memoryToStore;
            this.confidence = confidence;
            this.memoryType = memoryType;
        }
        
        
        public String getCategoryMatch() { return categoryMatch; }
        public String getNewCategorySuggestion() { return newCategorySuggestion; }
        public String getMemoryToStore() { return memoryToStore; }
        public String getConfidence() { return confidence; }
        public String getMemoryType() { return memoryType; }
        
        public boolean shouldStore() {
            return !memoryToStore.equals("None") && 
                   !confidence.equals("filtered");
                   // Temporarily accept all confidence levels to test LLM output
        }
    }
}

