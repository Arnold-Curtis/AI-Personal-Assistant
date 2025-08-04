package com.example.demo;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Service
public class PlanAnalysisService {
    
    // Enhanced prompt specifically for plan detection
    public static final String PLAN_ANALYSIS_PROMPT = 
        "You are a specialized AI plan analyzer. Your job is to determine if a user input represents a genuine PLAN-WORTHY goal that requires multiple steps over time.\n\n" +
        
        "CRITERIA FOR PLAN-WORTHY GOALS:\n" +
        "1. COMPLEXITY: Cannot be completed in a single session or day\n" +
        "2. TIME SPAN: Requires multiple days, weeks, or months\n" +
        "3. MULTIPLE STEPS: Needs a sequence of different activities\n" +
        "4. SKILL BUILDING: Involves learning, developing habits, or achieving long-term objectives\n" +
        "5. PREPARATION REQUIRED: Needs research, practice, or gradual progress\n\n" +
        
        "EXAMPLES OF PLAN-WORTHY GOALS:\n" +
        "✓ \"I want to learn Java programming\" → Complex skill requiring months of practice\n" +
        "✓ \"I'd like to start a skincare routine\" → Multi-step daily habits over time\n" +
        "✓ \"I want to learn how to drive\" → Multi-phase skill with lessons, practice, test\n" +
        "✓ \"I want to get fit and lose weight\" → Long-term lifestyle changes\n" +
        "✓ \"I want to start a small business\" → Complex multi-month endeavor\n" +
        "✓ \"I want to learn Spanish fluently\" → Long-term language acquisition\n" +
        "✓ \"I want to train for a marathon\" → Months of progressive training\n" +
        "✓ \"I want to improve my cooking skills\" → Gradual skill development\n\n" +
        
        "EXAMPLES OF NON-PLAN-WORTHY INPUTS:\n" +
        "✗ \"What is 1 + 1?\" → Simple question, immediate answer\n" +
        "✗ \"How do I cook pasta?\" → Single-session task with simple steps\n" +
        "✗ \"What's the weather today?\" → Information request\n" +
        "✗ \"Can you help me write an email?\" → Single task, completable now\n" +
        "✗ \"What movies should I watch?\" → Simple recommendation request\n" +
        "✗ \"How do I reset my password?\" → Simple troubleshooting\n" +
        "✗ \"What time is it?\" → Information request\n" +
        "✗ \"I have a meeting tomorrow\" → Calendar event, not a plan\n\n" +
        
        "ANALYSIS INSTRUCTIONS:\n" +
        "1. Look for INTENT to achieve something complex over time\n" +
        "2. Check if the goal requires SUSTAINED EFFORT across multiple sessions\n" +
        "3. Determine if it involves SKILL DEVELOPMENT or HABIT FORMATION\n" +
        "4. Assess if it needs SEQUENTIAL STEPS that build upon each other\n" +
        "5. Consider if it's something that benefits from PROGRESS TRACKING\n\n" +
        
        "RESPONSE FORMAT (JSON only, no extra text):\n" +
        "{\n" +
        "  \"requires_plan\": true/false,\n" +
        "  \"confidence\": \"high/medium/low\",\n" +
        "  \"reasoning\": \"Brief explanation of why this does/doesn't need a plan\",\n" +
        "  \"plan_type\": \"learning/fitness/skill/business/creative/habit/none\",\n" +
        "  \"estimated_duration\": \"days/weeks/months/none\",\n" +
        "  \"complexity_score\": 1-10\n" +
        "}\n\n" +
        "USER INPUT: %s";
    
    public PlanAnalysisResult analyzeForPlan(String userInput) {
        try {
            // First, do basic pattern-based pre-filtering
            if (isObviouslyNotPlanWorthy(userInput)) {
                return new PlanAnalysisResult(false, "high", 
                    "Simple question or information request - no plan needed", 
                    "none", "none", 1);
            }
            
            // Check for strong plan indicators
            if (hasStrongPlanIndicators(userInput)) {
                return createPlanWorthyResult(userInput);
            }
            
            // Use the enhanced prompt for AI analysis
            // In a full implementation, this would be sent to the AI service
            // For now, use sophisticated pattern matching
            return analyzePlanNeed(userInput);
            
        } catch (Exception e) {
            System.err.println("Error in plan analysis: " + e.getMessage());
            return new PlanAnalysisResult(false, "low", "Error in analysis", "none", "none", 1);
        }
    }
    
    private boolean isObviouslyNotPlanWorthy(String input) {
        String lowerInput = input.toLowerCase().trim();
        
        // Question patterns that are clearly not plan-worthy
        String[] questionPatterns = {
            "what is", "what's", "how much", "when is", "where is", "who is",
            "what time", "what day", "which", "why is", "how do you",
            "can you tell me", "do you know", "what does", "how does"
        };
        
        for (String pattern : questionPatterns) {
            if (lowerInput.startsWith(pattern)) {
                return true;
            }
        }
        
        // Math or simple calculation patterns
        if (lowerInput.matches(".*\\d+\\s*[+\\-*/]\\s*\\d+.*") ||
            lowerInput.contains("plus") || lowerInput.contains("minus") ||
            lowerInput.contains("times") || lowerInput.contains("divided")) {
            return true;
        }
        
        // Weather, time, date requests
        if (lowerInput.contains("weather") || lowerInput.contains("temperature") ||
            lowerInput.matches(".*what time.*") || lowerInput.matches(".*what day.*")) {
            return true;
        }
        
        // Simple greetings without goals
        if (lowerInput.matches("^(hi|hello|hey|good morning|good afternoon|good evening|what's up|how are you|how's it going)\\??$")) {
            return true;
        }
        
        // Short inputs (likely not complex goals)
        if (lowerInput.length() < 15 && !containsLearningKeywords(lowerInput)) {
            return true;
        }
        
        return false;
    }
    
    private boolean hasStrongPlanIndicators(String input) {
        String lowerInput = input.toLowerCase();
        
        // Learning goals
        String[] learningPatterns = {
            "i want to learn", "i'd like to learn", "i would like to learn",
            "i need to learn", "help me learn", "teach me",
            "i want to study", "i want to master", "i want to understand",
            "i want to get better at", "i want to improve my"
        };
        
        // Long-term goals
        String[] longTermPatterns = {
            "i want to start", "i'd like to start", "i want to begin",
            "i want to build", "i want to create", "i want to develop",
            "i want to achieve", "i want to become", "my goal is to"
        };
        
        // Habit/routine goals
        String[] habitPatterns = {
            "routine", "habit", "daily", "regularly", "consistently",
            "every day", "workout plan", "diet plan", "schedule"
        };
        
        // Check all patterns
        for (String pattern : learningPatterns) {
            if (lowerInput.contains(pattern)) return true;
        }
        for (String pattern : longTermPatterns) {
            if (lowerInput.contains(pattern)) return true;
        }
        for (String pattern : habitPatterns) {
            if (lowerInput.contains(pattern)) return true;
        }
        
        return false;
    }
    
    private boolean containsLearningKeywords(String input) {
        String lowerInput = input.toLowerCase();
        String[] keywords = {"learn", "study", "train", "practice", "skill", "course"};
        for (String keyword : keywords) {
            if (lowerInput.contains(keyword)) return true;
        }
        return false;
    }
    
    private PlanAnalysisResult createPlanWorthyResult(String input) {
        String lowerInput = input.toLowerCase();
        
        // Determine plan type
        String planType = "none";
        String duration = "weeks";
        int complexity = 7;
        
        if (lowerInput.contains("learn") || lowerInput.contains("study")) {
            planType = "learning";
            duration = lowerInput.contains("language") ? "months" : "weeks";
            complexity = lowerInput.contains("programming") ? 8 : 7;
        } else if (lowerInput.contains("fitness") || lowerInput.contains("workout") || 
                   lowerInput.contains("exercise") || lowerInput.contains("lose weight")) {
            planType = "fitness";
            duration = "months";
            complexity = 7;
        } else if (lowerInput.contains("business") || lowerInput.contains("startup")) {
            planType = "business";
            duration = "months";
            complexity = 9;
        } else if (lowerInput.contains("routine") || lowerInput.contains("habit")) {
            planType = "habit";
            duration = "weeks";
            complexity = 6;
        } else if (lowerInput.contains("skill")) {
            planType = "skill";
            duration = "weeks";
            complexity = 7;
        } else {
            planType = "learning";
            complexity = 6;
        }
        
        return new PlanAnalysisResult(true, "high", 
            "Complex goal requiring multiple steps and sustained effort over time", 
            planType, duration, complexity);
    }
    
    private PlanAnalysisResult analyzePlanNeed(String input) {
        String lowerInput = input.toLowerCase();
        
        // Score various factors
        int complexityScore = 0;
        String reasoning = "";
        String planType = "none";
        String duration = "none";
        
        // Check for complexity indicators
        if (lowerInput.matches(".*\\b(learn|study|master|understand|improve)\\b.*")) {
            complexityScore += 3;
            planType = "learning";
            reasoning += "Contains learning/improvement goals. ";
        }
        
        if (lowerInput.matches(".*\\b(build|create|develop|design|make)\\b.*")) {
            complexityScore += 2;
            planType = "creative";
            reasoning += "Involves creation/building. ";
        }
        
        if (lowerInput.matches(".*\\b(routine|habit|daily|weekly|regularly)\\b.*")) {
            complexityScore += 2;
            planType = "habit";
            reasoning += "Involves routine/habit formation. ";
        }
        
        // Check for time indicators
        if (lowerInput.matches(".*\\b(month|months|year|years|long.?term)\\b.*")) {
            complexityScore += 2;
            duration = "months";
            reasoning += "Long-term timeframe mentioned. ";
        } else if (lowerInput.matches(".*\\b(week|weeks|few days)\\b.*")) {
            complexityScore += 1;
            duration = "weeks";
            reasoning += "Medium-term timeframe. ";
        }
        
        // Check for goal complexity
        if (lowerInput.length() > 50) {
            complexityScore += 1;
            reasoning += "Detailed description suggests complexity. ";
        }
        
        // Determine result
        boolean requiresPlan = complexityScore >= 4;
        String confidence = complexityScore >= 5 ? "high" : complexityScore >= 3 ? "medium" : "low";
        
        if (!requiresPlan && reasoning.isEmpty()) {
            reasoning = "Input appears to be a simple request or question that doesn't require a multi-step plan.";
            planType = "none";
            duration = "none";
            complexityScore = Math.max(1, complexityScore);
        }
        
        if (planType.equals("none") && requiresPlan) {
            planType = "learning"; // Default for complex goals
        }
        
        if (duration.equals("none") && requiresPlan) {
            duration = "weeks"; // Default duration
        }
        
        return new PlanAnalysisResult(requiresPlan, confidence, reasoning.trim(), 
                                    planType, duration, Math.max(1, complexityScore));
    }
    
    public static class PlanAnalysisResult {
        private final boolean requiresPlan;
        private final String confidence;
        private final String reasoning;
        private final String planType;
        private final String estimatedDuration;
        private final int complexityScore;
        
        public PlanAnalysisResult(boolean requiresPlan, String confidence, String reasoning,
                                String planType, String estimatedDuration, int complexityScore) {
            this.requiresPlan = requiresPlan;
            this.confidence = confidence;
            this.reasoning = reasoning;
            this.planType = planType;
            this.estimatedDuration = estimatedDuration;
            this.complexityScore = complexityScore;
        }
        
        // Getters
        public boolean requiresPlan() { return requiresPlan; }
        public String getConfidence() { return confidence; }
        public String getReasoning() { return reasoning; }
        public String getPlanType() { return planType; }
        public String getEstimatedDuration() { return estimatedDuration; }
        public int getComplexityScore() { return complexityScore; }
        
        public boolean shouldCreatePlan() {
            return requiresPlan && (confidence.equals("high") || confidence.equals("medium"));
        }
        
        @Override
        public String toString() {
            return String.format("PlanAnalysis{requiresPlan=%b, confidence=%s, type=%s, duration=%s, complexity=%d, reasoning='%s'}", 
                requiresPlan, confidence, planType, estimatedDuration, complexityScore, reasoning);
        }
    }
}
