package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Service
public class MemoryAnalysisService {
    
    private final MemoryFilterService memoryFilterService;
    private final InputRoutingService inputRoutingService;
    
    @Autowired
    public MemoryAnalysisService(MemoryFilterService memoryFilterService, InputRoutingService inputRoutingService) {
        this.memoryFilterService = memoryFilterService;
        this.inputRoutingService = inputRoutingService;
    }
    
    
    public static final String ENHANCED_MEMORY_PROMPT = 
        "You are an AI memory system analyzer. Your job is to identify information that should be remembered for future conversations.\n\n" +
        
        "PERSONAL INFORMATION TO ALWAYS REMEMBER:\n" +
        "- Birthdays, anniversaries, special dates\n" +
        "- Names of family members, friends, pets\n" +
        "- Personal preferences (food, music, movies, etc.)\n" +
        "- Goals and aspirations (learning languages, career goals, etc.)\n" +
        "- Important life events (graduations, weddings, job changes)\n" +
        "- Health information and medical details\n" +
        "- Location information (where they live, work, etc.)\n" +
        "- Hobbies and interests\n" +
        "- Fears and dislikes\n" +
        "- Relationship status and relationship details\n\n" +
        
        "EXISTING CATEGORIES: %s\n\n" +
        
        "ANALYSIS INSTRUCTIONS:\n" +
        "1. Scan the input for ANY personal information worth remembering\n" +
        "2. Check if it relates to existing categories (use fuzzy matching - e.g., 'Personal Info' matches 'personal_information')\n" +
        "3. Extract specific, factual information (not opinions or temporary states)\n" +
        "4. Create descriptive, searchable memory entries\n\n" +
        
        "EXAMPLES OF GOOD MEMORY EXTRACTION:\n" +
        "Input: \"My birthday is March 15th\"\n" +
        "Output: {\"category_match\": \"Personal_Information\", \"new_category_suggestion\": \"None\", \"memory_to_store\": \"Birthday is March 15th\"}\n\n" +
        
        "Input: \"I love playing guitar and I'm learning Spanish\"\n" +
        "Output: {\"category_match\": \"None\", \"new_category_suggestion\": \"Hobbies_And_Learning\", \"memory_to_store\": \"Plays guitar, currently learning Spanish language\"}\n\n" +
        
        "Input: \"My dog Max is a golden retriever\"\n" +
        "Output: {\"category_match\": \"None\", \"new_category_suggestion\": \"Pets_And_Family\", \"memory_to_store\": \"Has a dog named Max, golden retriever breed\"}\n\n" +
        
        "RESPONSE FORMAT (JSON only, no extra text):\n" +
        "{\n" +
        "  \"category_match\": \"Exact_Category_Name/None\",\n" +
        "  \"new_category_suggestion\": \"New_Category_Name/None\",\n" +
        "  \"memory_to_store\": \"Specific_factual_information/None\",\n" +
        "  \"confidence\": \"high/medium/low\",\n" +
        "  \"memory_type\": \"personal_info/goal/preference/fact/relationship/None\"\n" +
        "}\n\n" +
        "USER INPUT: %s";
    
    public MemoryAnalysisResult analyzeForMemory(String userInput, List<String> existingCategories) {
        try {
            
            InputRoutingService.RoutingDecision routingDecision = inputRoutingService.routeInput(userInput);
            
            if (!routingDecision.shouldProcessMemory()) {
                String reason = routingDecision.getDestination() == InputRoutingService.RoutingDestination.CALENDAR_ONLY 
                    ? "routed_to_calendar" : "not_memory_worthy";
                return new MemoryAnalysisResult("None", "None", "None", reason, "None");
            }
            
            
            
            if (routingDecision.getConfidence() >= 0.8) {
                
                if (containsPersonalInfo(userInput)) {
                    return createHighConfidenceResult(userInput, existingCategories);
                } else {
                    return extractMemoryUsingPatterns(userInput, existingCategories);
                }
            }
            
            
            MemoryFilterService.MemoryWorthinessResult worthinessResult = 
                memoryFilterService.analyzeMemoryWorthiness(userInput);
            
            if (!worthinessResult.isWorthy()) {
                return new MemoryAnalysisResult("None", "None", "None", "filtered", "None");
            }
            
            
            if (containsPersonalInfo(userInput)) {
                return createHighConfidenceResult(userInput, existingCategories);
            }
            
            
            
            
            
            
            
            return extractMemoryUsingPatterns(userInput, existingCategories);
            
        } catch (Exception e) {
            System.err.println("Error in memory analysis: " + e.getMessage());
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
                extractBirthdayInfo(input),
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
                extractLearningInfo(input),
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
                extractPreferenceInfo(input),
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
        
        String lowerInput = input.toLowerCase();
        
        if (lowerInput.length() < 10) {
            return new MemoryAnalysisResult("None", "None", "None", "low", "None");
        }
        
        
        if (lowerInput.matches(".*\\b(i|my|me)\\b.*") && 
            (lowerInput.contains("is") || lowerInput.contains("have") || lowerInput.contains("am"))) {
            return new MemoryAnalysisResult(
                "None",
                "General_Information",
                input.trim(),
                "medium",
                "fact"
            );
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
    
    private String extractBirthdayInfo(String input) {
        
        Pattern datePattern = Pattern.compile("\\b(january|february|march|april|may|june|july|august|september|october|november|december)\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = datePattern.matcher(input);
        if (matcher.find()) {
            return "Birthday is " + matcher.group(1) + " " + matcher.group(2);
        }
        
        Pattern numericPattern = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})\\b");
        Matcher numMatcher = numericPattern.matcher(input);
        if (numMatcher.find()) {
            return "Birthday is " + numMatcher.group(1) + "/" + numMatcher.group(2) + "/" + numMatcher.group(3);
        }
        
        return "Birthday mentioned: " + input.trim();
    }
    
    private String extractLearningInfo(String input) {
        Pattern learningPattern = Pattern.compile("\\b(?:learning|studying|want to learn)\\s+([\\w\\s]+?)(?:\\.|,|$)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = learningPattern.matcher(input);
        if (matcher.find()) {
            return "Learning/Goal: " + matcher.group(1).trim();
        }
        return "Learning goal mentioned: " + input.trim();
    }
    
    private String extractPreferenceInfo(String input) {
        Pattern prefPattern = Pattern.compile("\\b(?:love|like|favorite|prefer)\\s+([\\w\\s]+?)(?:\\.|,|and|$)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = prefPattern.matcher(input);
        if (matcher.find()) {
            return "Likes: " + matcher.group(1).trim();
        }
        return "Preference mentioned: " + input.trim();
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
                   !confidence.equals("filtered") &&
                   (confidence.equals("high") || confidence.equals("medium"));
        }
    }
}

