package com.example.demo;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Advanced filtering service to determine if user input contains 
 * valuable information worthy of being stored as a persistent memory.
 * 
 * This service prevents the storage of:
 * - Questions and queries
 * - Chat fragments and conversation fillers
 * - Temporary states or emotions
 * - Requests for information retrieval
 * - Commands and instructions
 */
@Service
public class MemoryFilterService {
    
    // Patterns that indicate questions or information requests
    private static final List<Pattern> QUESTION_PATTERNS = Arrays.asList(
        // Direct question words at start
        Pattern.compile("^\\s*(when|what|where|who|how|why|which|can|could|would|will|should|do|does|did|is|are|was|were|have|has|had)\\s+", Pattern.CASE_INSENSITIVE),
        // Question phrases
        Pattern.compile("\\b(tell me|remind me|show me|let me know|do you know|do you remember|can you|could you|would you|will you)\\b", Pattern.CASE_INSENSITIVE),
        // Information seeking patterns
        Pattern.compile("\\b(what is|what's|when is|when's|where is|where's|who is|who's|how is|how's)\\b", Pattern.CASE_INSENSITIVE),
        // Memory retrieval requests
        Pattern.compile("\\b(what was|what were|when was|when were|where was|where were)\\b", Pattern.CASE_INSENSITIVE),
        // Question marks
        Pattern.compile("\\?"),
        // Checking/verification patterns
        Pattern.compile("\\b(check|verify|confirm|look up|find out)\\b", Pattern.CASE_INSENSITIVE)
    );
    
    // Patterns that indicate chat fragments or non-memory worthy content
    private static final List<Pattern> CHAT_FRAGMENT_PATTERNS = Arrays.asList(
        // Single words or very short responses
        Pattern.compile("^\\s*(yes|no|ok|okay|sure|fine|thanks|thank you|please|hello|hi|bye|goodbye)\\s*$", Pattern.CASE_INSENSITIVE),
        // Filler phrases
        Pattern.compile("^\\s*(i see|i understand|got it|alright|right|yeah|yep|nope|hmm|uh|um|well)\\s*$", Pattern.CASE_INSENSITIVE),
        // Commands or instructions
        Pattern.compile("^\\s*(add|remove|delete|update|change|set|clear|reset|stop|start|continue)\\s+", Pattern.CASE_INSENSITIVE),
        // Temporary emotional states
        Pattern.compile("\\b(i feel|i'm feeling|i'm sad|i'm happy|i'm tired|i'm confused)\\b", Pattern.CASE_INSENSITIVE),
        // System or technical references
        Pattern.compile("\\b(error|bug|issue|problem|system|database|server|code|file|folder)\\b", Pattern.CASE_INSENSITIVE),
        // Very short inputs (likely not meaningful memories)
        Pattern.compile("^.{1,5}$"),
        // Numbers or codes without context
        Pattern.compile("^\\s*\\d+\\s*$"),
        // Random characters or gibberish
        Pattern.compile("^\\s*[^a-zA-Z]*\\s*$")
    );
    
    // Patterns that indicate valuable memory content
    private static final List<Pattern> VALUABLE_MEMORY_PATTERNS = Arrays.asList(
        // Personal information statements
        Pattern.compile("\\b(my name is|i am|i'm|call me)\\s+[a-zA-Z]+", Pattern.CASE_INSENSITIVE),
        // Family and relationships
        Pattern.compile("\\b(my \\w+ is|my \\w+'s name is|i have a|i live with)\\s+[a-zA-Z]+", Pattern.CASE_INSENSITIVE),
        // Dates and events
        Pattern.compile("\\b(my birthday|anniversary|graduation|wedding)\\b", Pattern.CASE_INSENSITIVE),
        // Preferences and interests
        Pattern.compile("\\b(i love|i like|i enjoy|i prefer|my favorite|i hate|i dislike)\\s+", Pattern.CASE_INSENSITIVE),
        // Goals and aspirations
        Pattern.compile("\\b(i want to|i'm learning|i'm studying|my goal|i plan to|i hope to)\\s+", Pattern.CASE_INSENSITIVE),
        // Location and work
        Pattern.compile("\\b(i live in|i work at|i'm from|my job|my career)\\s+", Pattern.CASE_INSENSITIVE),
        // Health and medical
        Pattern.compile("\\b(i have|i suffer from|i'm allergic to|my doctor|my medication)\\s+", Pattern.CASE_INSENSITIVE),
        // Personal facts with dates
        Pattern.compile("\\b(january|february|march|april|may|june|july|august|september|october|november|december)\\s+\\d{1,2}", Pattern.CASE_INSENSITIVE),
        // Factual statements about self
        Pattern.compile("\\b(i am \\d+|i'm \\d+|i was born|i graduated|i started|i moved)\\b", Pattern.CASE_INSENSITIVE)
    );
    
    // Keywords that often appear in non-memory worthy chat
    private static final Set<String> NOISE_KEYWORDS = Set.of(
        "again", "still", "just", "maybe", "perhaps", "probably", "actually",
        "basically", "literally", "obviously", "clearly", "apparently",
        "anyway", "whatever", "somehow", "somewhere", "something", "anything"
    );
    
    /**
     * Comprehensive analysis to determine if user input is worthy of being stored as memory
     */
    public MemoryWorthinessResult analyzeMemoryWorthiness(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return new MemoryWorthinessResult(false, "Empty input", 0.0);
        }
        
        String cleanInput = userInput.trim();
        
        // Step 1: Filter out obvious questions
        if (isQuestion(cleanInput)) {
            return new MemoryWorthinessResult(false, "Question or information request", 0.1);
        }
        
        // Step 2: Filter out chat fragments and noise
        if (isChatFragment(cleanInput)) {
            return new MemoryWorthinessResult(false, "Chat fragment or filler", 0.1);
        }
        
        // Step 3: Check for minimum content quality
        if (!hasMinimumQuality(cleanInput)) {
            return new MemoryWorthinessResult(false, "Insufficient quality or context", 0.2);
        }
        
        // Step 4: Check for valuable memory patterns
        double valueScore = calculateValueScore(cleanInput);
        
        if (valueScore >= 0.7) {
            return new MemoryWorthinessResult(true, "High-value personal information", valueScore);
        } else if (valueScore >= 0.4) {
            return new MemoryWorthinessResult(true, "Moderate-value information", valueScore);
        } else {
            return new MemoryWorthinessResult(false, "Low-value information", valueScore);
        }
    }
    
    /**
     * Check if input is a question or information request
     */
    private boolean isQuestion(String input) {
        for (Pattern pattern : QUESTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if input is a chat fragment or filler content
     */
    private boolean isChatFragment(String input) {
        for (Pattern pattern : CHAT_FRAGMENT_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }
        
        // Check for high noise keyword density
        String[] words = input.toLowerCase().split("\\s+");
        if (words.length > 0) {
            long noiseWords = Arrays.stream(words)
                .filter(NOISE_KEYWORDS::contains)
                .count();
            double noiseRatio = (double) noiseWords / words.length;
            if (noiseRatio > 0.3) { // More than 30% noise words
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check minimum quality requirements
     */
    private boolean hasMinimumQuality(String input) {
        // Too short (less than 10 characters meaningful content)
        String meaningfulContent = input.replaceAll("[^a-zA-Z0-9\\s]", "").trim();
        if (meaningfulContent.length() < 10) {
            return false;
        }
        
        // Must have at least 2 words
        String[] words = meaningfulContent.split("\\s+");
        if (words.length < 2) {
            return false;
        }
        
        // Must contain at least one personal pronoun or name
        String lowerInput = input.toLowerCase();
        if (!lowerInput.matches(".*\\b(i|my|me|mine|myself|\\b[A-Z][a-z]+\\b).*")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Calculate value score based on content patterns
     */
    private double calculateValueScore(String input) {
        double score = 0.0;
        
        // Check for valuable memory patterns
        for (Pattern pattern : VALUABLE_MEMORY_PATTERNS) {
            if (pattern.matcher(input).find()) {
                score += 0.3;
            }
        }
        
        String lowerInput = input.toLowerCase();
        
        // Bonus for specific valuable content types
        if (containsDateInformation(lowerInput)) score += 0.2;
        if (containsNameInformation(lowerInput)) score += 0.2;
        if (containsLocationInformation(lowerInput)) score += 0.15;
        if (containsRelationshipInformation(lowerInput)) score += 0.15;
        if (containsPreferenceInformation(lowerInput)) score += 0.1;
        if (containsGoalInformation(lowerInput)) score += 0.1;
        if (containsFactualInformation(lowerInput)) score += 0.1;
        
        // Penalty for question-like elements even if not caught earlier
        if (lowerInput.contains("?") || lowerInput.startsWith("when ") || 
            lowerInput.startsWith("what ") || lowerInput.startsWith("where ")) {
            score -= 0.3;
        }
        
        // Bonus for first-person statements
        if (lowerInput.matches(".*\\b(i am|i have|my \\w+ is|i live|i work|i was born)\\b.*")) {
            score += 0.2;
        }
        
        return Math.min(1.0, Math.max(0.0, score));
    }
    
    private boolean containsDateInformation(String input) {
        return input.matches(".*\\b(january|february|march|april|may|june|july|august|september|october|november|december)\\s+\\d{1,2}.*") ||
               input.matches(".*\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}.*") ||
               input.matches(".*\\b(birthday|anniversary|graduation|born)\\b.*");
    }
    
    private boolean containsNameInformation(String input) {
        return input.matches(".*\\b(my name|i'm|i am|call me|named|known as)\\s+[A-Za-z]+.*") ||
               input.matches(".*\\b(my \\w+('?s)? name is|my \\w+ is called|my \\w+ is named)\\s+[A-Za-z]+.*");
    }
    
    private boolean containsLocationInformation(String input) {
        return input.matches(".*\\b(i live|i'm from|i work|my address|my home|my office)\\b.*") ||
               input.matches(".*\\b(in [A-Z][a-z]+(\\s+[A-Z][a-z]+)*)\\b.*");
    }
    
    private boolean containsRelationshipInformation(String input) {
        return input.matches(".*\\b(my wife|my husband|my partner|my boyfriend|my girlfriend|my family|my parents|my children|my kids|my mom|my dad|my mother|my father|my sister|my brother)\\b.*") ||
               input.matches(".*\\b(my \\w+('?s)? (name is|is named|is called))\\b.*");
    }
    
    private boolean containsPreferenceInformation(String input) {
        return input.matches(".*\\b(i love|i like|i enjoy|i prefer|my favorite|i hate|i dislike|i don't like)\\s+.*");
    }
    
    private boolean containsGoalInformation(String input) {
        return input.matches(".*\\b(i want to|i'm learning|i'm studying|my goal|i plan to|i hope to|i'm trying to)\\s+.*");
    }
    
    private boolean containsFactualInformation(String input) {
        return input.matches(".*\\b(i have|i am|i work as|i studied|i graduated|my job|my career|my education)\\b.*");
    }
    
    /**
     * Result class for memory worthiness analysis
     */
    public static class MemoryWorthinessResult {
        private final boolean isWorthy;
        private final String reason;
        private final double score;
        
        public MemoryWorthinessResult(boolean isWorthy, String reason, double score) {
            this.isWorthy = isWorthy;
            this.reason = reason;
            this.score = score;
        }
        
        public boolean isWorthy() { return isWorthy; }
        public String getReason() { return reason; }
        public double getScore() { return score; }
        
        @Override
        public String toString() {
            return String.format("MemoryWorthiness{worthy=%s, score=%.2f, reason='%s'}", 
                isWorthy, score, reason);
        }
    }
}
