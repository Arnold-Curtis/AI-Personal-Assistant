package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;
import java.util.logging.Logger;

@Service
public class InputRoutingService {
    
    private static final Logger logger = Logger.getLogger(InputRoutingService.class.getName());
    
    private final MemoryFilterService memoryFilterService;
    
    @Autowired
    public InputRoutingService(MemoryFilterService memoryFilterService) {
        this.memoryFilterService = memoryFilterService;
    }
    
    
    private static final List<Pattern> TEMPORAL_PATTERNS = Arrays.asList(
        
        Pattern.compile("\\b(in\\s+)?(\\d+)\\s+(days?|weeks?|months?)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(tomorrow|today|tonight)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(next)\\s+(week|month|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(this)\\s+(week|weekend|month|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(january|february|march|april|may|june|july|august|september|october|november|december)\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})\\b"), 
        Pattern.compile("\\b(at\\s+)?(\\d{1,2})[:.]?(\\d{2})?\\s*(am|pm)\\b", Pattern.CASE_INSENSITIVE) 
    );
    
    
    private static final Set<String> EVENT_INDICATORS = Set.of(
        "i have", "there's", "i'm going", "i'll be", "we have", "schedule", "plan", "book", "appointment", "meeting",
        "going to", "traveling to", "visiting"
    );
    
    
    private static final Set<String> EVENT_TYPES = Set.of(
        "wedding", "meeting", "appointment", "birthday", "party", "conference", 
        "interview", "exam", "test", "vacation", "trip", "date", "call",
        "dinner", "lunch", "breakfast", "game", "match", "concert", "show",
        "graduation", "ceremony", "funeral", "reunion", "visit", "checkup",
        "event", "gathering", "celebration", "session", "class", "lesson"
    );
    
    
    private static final Set<String> PERSONAL_INFO_KEYWORDS = Set.of(
        "my name is", "i'm", "i am", "call me", "my favorite", "i love", "i like", 
        "i enjoy", "i prefer", "i hate", "i dislike", "my goal", "i want to learn",
        "i'm learning", "my family", "my friend", "my pet", "my job", "i work",
        "i live", "my address", "my phone", "my email", "my dad", "my father", 
        "my mom", "my mother", "my girlfriend", "my boyfriend", "my wife", "my husband",
        "my sister", "my brother", "my son", "my daughter", "my grandpa", "my grandma",
        "i studied", "i graduated", "my degree", "my university", "my college", "my school",
        "i drive", "my car", "i own", "my house", "my apartment", "i was born", "born in"
    );
    
    
    public RoutingDecision routeInput(String userInput) {
        logger.info("Routing input: " + userInput);
        
        
        MemoryFilterService.MemoryWorthinessResult worthinessResult = 
            memoryFilterService.analyzeMemoryWorthiness(userInput);
        
        if (!worthinessResult.isWorthy() && worthinessResult.getReason().contains("Question")) {
            logger.info("Input filtered out as question: " + userInput);
            return new RoutingDecision(RoutingDestination.NEITHER, 
                "User input is a question asking for information", 0.0);
        }
        
        String lowerInput = userInput.toLowerCase().trim();
        
        
        boolean hasTemporalContext = hasTemporalContext(lowerInput);
        
        
        boolean hasEventIndicators = hasEventIndicators(lowerInput);
        
        
        boolean hasEventTypes = hasEventTypes(lowerInput);
        
        
        boolean hasPersonalInfo = hasPersonalInfo(lowerInput);
        
        
        double calendarScore = calculateCalendarScore(hasTemporalContext, hasEventIndicators, hasEventTypes, lowerInput);
        double memoryScore = calculateMemoryScore(hasPersonalInfo, hasTemporalContext, lowerInput);
        
        
        RoutingDecision decision = makeRoutingDecision(calendarScore, memoryScore, userInput);
        
        logger.info("Routing decision: " + decision.toString());
        return decision;
    }
    
    private boolean hasTemporalContext(String input) {
        for (Pattern pattern : TEMPORAL_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }
    
    private boolean hasEventIndicators(String input) {
        for (String indicator : EVENT_INDICATORS) {
            if (input.contains(indicator)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean hasEventTypes(String input) {
        for (String eventType : EVENT_TYPES) {
            if (input.contains(eventType)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean hasPersonalInfo(String input) {
        for (String keyword : PERSONAL_INFO_KEYWORDS) {
            if (input.contains(keyword)) {
                return true;
            }
        }
        
        
        if (input.matches(".*\\b(my \\w+ is|my \\w+'s name is)\\s+.*")) {
            return true;
        }
        
        
        if (input.matches(".*\\b(my \\w+s? name is)\\s+.*")) {
            return true;
        }
        
        
        if (input.matches(".*\\b(my \\w+ is named)\\s+.*")) {
            return true;
        }
        
        return false;
    }
    
    private double calculateCalendarScore(boolean hasTemporalContext, boolean hasEventIndicators, 
                                        boolean hasEventTypes, String input) {
        double score = 0.0;
        
        if (hasTemporalContext) score += 0.4;
        if (hasEventIndicators) score += 0.3;
        if (hasEventTypes) score += 0.3;
        
        
        if (input.matches(".*\\b(i have|there'?s)\\s+(?:a|an)?\\s*\\w+\\s+.*")) {
            score += 0.2;
        }
        
        
        if (input.toLowerCase().matches(".*\\b(i'?m going|going to)\\b.*\\b(this|next|tomorrow|today)\\b.*")) {
            score += 0.4;
        }
        
        
        if (input.matches("^(when|what|where|who|how)\\s+.*\\?.*") || 
            input.matches(".*\\?\\s*$")) {
            score -= 0.5;
        }
        
        return Math.max(0.0, Math.min(1.0, score));
    }
    
    private double calculateMemoryScore(boolean hasPersonalInfo, boolean hasTemporalContext, String input) {
        double score = 0.0;
        
        if (hasPersonalInfo) score += 0.8; // Increased from 0.7 - be more generous with personal info 
        
        
        if (!hasTemporalContext && input.matches(".*\\b(my|i am|i'm|i have)\\b.*")) {
            score += 0.3;
        }
        
        
        if (!hasTemporalContext && input.matches(".*\\b(i like|i love|i want to|my goal)\\b.*")) {
            score += 0.4;
        }
        
        
        if (input.toLowerCase().matches(".*\\b(my \\w+s? name is|my \\w+ is named)\\b.*")) {
            score += 0.4; 
        }
        
        
        if (input.matches(".*\\b(i have|there'?s)\\s+(?:a|an)?\\s*\\w+\\s+in\\s+\\d+\\s+.*")) {
            score -= 0.2; // Reduced penalty from 0.4 to 0.2
        }
        
        
        if (input.contains("birthday")) {
            // Birthday information is always valuable for memory, regardless of temporal context
            score += 0.5; // Strong memory signal for birthday info
        }
        
        return Math.max(0.0, Math.min(1.0, score));
    }
    
    private RoutingDecision makeRoutingDecision(double calendarScore, double memoryScore, String input) {
        final double THRESHOLD = 0.5;
        final double CONFIDENCE_DIFF_THRESHOLD = 0.15; // Reduced from 0.2 to make it easier to pick memory
        
        // Special case for birthday information - always memory
        if (input.toLowerCase().contains("birthday") && !input.toLowerCase().matches(".*\\bin\\s+\\d+\\s+(days?|weeks?).*")) {
            return new RoutingDecision(RoutingDestination.MEMORY_ONLY, 
                "Birthday information is personal data for memory storage", memoryScore);
        }
        
        if (calendarScore >= THRESHOLD && memoryScore >= THRESHOLD) {
            
            if (calendarScore - memoryScore > CONFIDENCE_DIFF_THRESHOLD) {
                return new RoutingDecision(RoutingDestination.CALENDAR_ONLY, 
                    "Strong temporal context detected with event indicators", calendarScore);
            } else if (memoryScore - calendarScore > CONFIDENCE_DIFF_THRESHOLD) {
                return new RoutingDecision(RoutingDestination.MEMORY_ONLY, 
                    "Strong personal information context without clear temporal scheduling", memoryScore);
            } else {
                
                if (input.toLowerCase().matches(".*\\bin\\s+\\d+\\s+(days?|weeks?).*")) {
                    return new RoutingDecision(RoutingDestination.CALENDAR_ONLY, 
                        "Explicit future timeframe detected", calendarScore);
                } else {
                    // When in doubt, prefer memory for personal facts
                    return new RoutingDecision(RoutingDestination.MEMORY_ONLY, 
                        "Personal facts preferred for memory storage when ambiguous", memoryScore);
                }
            }
        } else if (calendarScore >= THRESHOLD) {
            return new RoutingDecision(RoutingDestination.CALENDAR_ONLY, 
                "Clear calendar event with temporal context", calendarScore);
        } else if (memoryScore >= THRESHOLD) {
            return new RoutingDecision(RoutingDestination.MEMORY_ONLY, 
                "Personal information or preferences without scheduling", memoryScore);
        } else {
            return new RoutingDecision(RoutingDestination.NEITHER, 
                "Low confidence for both memory and calendar routing", Math.max(calendarScore, memoryScore));
        }
    }
    
    public enum RoutingDestination {
        CALENDAR_ONLY,
        MEMORY_ONLY,
        NEITHER
    }
    
    public static class RoutingDecision {
        private final RoutingDestination destination;
        private final String reasoning;
        private final double confidence;
        
        public RoutingDecision(RoutingDestination destination, String reasoning, double confidence) {
            this.destination = destination;
            this.reasoning = reasoning;
            this.confidence = confidence;
        }
        
        public RoutingDestination getDestination() { return destination; }
        public String getReasoning() { return reasoning; }
        public double getConfidence() { return confidence; }
        
        public boolean shouldProcessCalendar() {
            return destination == RoutingDestination.CALENDAR_ONLY;
        }
        
        public boolean shouldProcessMemory() {
            return destination == RoutingDestination.MEMORY_ONLY;
        }
        
        @Override
        public String toString() {
            return String.format("RoutingDecision{destination=%s, reasoning='%s', confidence=%.2f}", 
                               destination, reasoning, confidence);
        }
    }
}

