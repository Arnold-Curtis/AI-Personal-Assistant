package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;

@Service
public class CalendarEventEnhancementService {
    
    private static final Logger logger = Logger.getLogger(CalendarEventEnhancementService.class.getName());
    
    private final MemoryFilterService memoryFilterService;
    
    @Autowired
    public CalendarEventEnhancementService(MemoryFilterService memoryFilterService) {
        this.memoryFilterService = memoryFilterService;
    }
    
    // Enhanced patterns for detecting calendar events - with better specificity
    private static final List<Pattern> EVENT_PATTERNS = Arrays.asList(
        // "I have a wedding in 2 weeks" - explicit event creation
        Pattern.compile("(?:i have|there'?s|i'?ve got)\\s+(?:a|an)?\\s*(\\w+(?:\\s+\\w+)?)\\s+in\\s+(\\d+)\\s+(days?|weeks?|months?)", Pattern.CASE_INSENSITIVE),
        // "wedding tomorrow" - when NOT preceded by question words
        Pattern.compile("(?<!when\\s|what\\s|where\\s)\\b(\\w+(?:\\s+\\w+)?)\\s+(tomorrow|today|next week|next month)\\b", Pattern.CASE_INSENSITIVE),
        // "meeting on Monday" - when NOT preceded by question words
        Pattern.compile("(?<!when\\s|what\\s|where\\s)\\b(\\w+(?:\\s+\\w+)?)\\s+on\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b", Pattern.CASE_INSENSITIVE),
        // "wedding in 3 weeks" - when NOT preceded by question words  
        Pattern.compile("(?<!when\\s|what\\s|where\\s)\\b(\\w+(?:\\s+\\w+)?)\\s+in\\s+(\\d+)\\s+(days?|weeks?|months?)\\b", Pattern.CASE_INSENSITIVE),
        // "my birthday is March 23" - ONLY when it's a statement with future context or explicit scheduling
        Pattern.compile("(?:i have|there'?s|schedule|plan|book)\\s+(?:a|an|my)?\\s*(\\w+(?:\\s+\\w+)?)\\s+(?:on\\s+)?(january|february|march|april|may|june|july|august|september|october|november|december)\\s+(\\d{1,2})", Pattern.CASE_INSENSITIVE),
        // "appointment next Friday" - when NOT preceded by question words
        Pattern.compile("(?<!when\\s|what\\s|where\\s)\\b(\\w+(?:\\s+\\w+)?)\\s+next\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b", Pattern.CASE_INSENSITIVE)
    );
    
    // Common event types that should be detected
    private static final Set<String> EVENT_TYPES = Set.of(
        "wedding", "meeting", "appointment", "birthday", "party", "conference", 
        "interview", "exam", "test", "vacation", "trip", "date", "call",
        "dinner", "lunch", "breakfast", "game", "match", "concert", "show",
        "graduation", "ceremony", "funeral", "reunion", "visit", "checkup"
    );
    
    private static final Map<String, Integer> MONTH_TO_NUMBER;
    static {
        Map<String, Integer> months = new HashMap<>();
        months.put("january", 1);
        months.put("february", 2);
        months.put("march", 3);
        months.put("april", 4);
        months.put("may", 5);
        months.put("june", 6);
        months.put("july", 7);
        months.put("august", 8);
        months.put("september", 9);
        months.put("october", 10);
        months.put("november", 11);
        months.put("december", 12);
        MONTH_TO_NUMBER = Collections.unmodifiableMap(months);
    }
    
    /**
     * Analyzes user input to detect and enhance calendar events
     */
    public CalendarEventAnalysis analyzeForCalendarEvents(String userInput) {
        logger.info("Analyzing input for calendar events: " + userInput);
        
        // FIRST: Use the memory filter to check if this is a question or chat fragment
        MemoryFilterService.MemoryWorthinessResult worthinessResult = 
            memoryFilterService.analyzeMemoryWorthiness(userInput);
        
        if (!worthinessResult.isWorthy() && worthinessResult.getReason().contains("Question")) {
            logger.info("Input filtered out as question by MemoryFilterService: " + userInput);
            return new CalendarEventAnalysis(false, new ArrayList<>(), "");
        }
        
        // SECOND: Check if this is a question asking for information rather than creating an event
        if (isQuestionAboutExistingInfo(userInput)) {
            logger.info("Input identified as information question, not event creation: " + userInput);
            return new CalendarEventAnalysis(false, new ArrayList<>(), "");
        }
        
        List<DetectedEvent> detectedEvents = new ArrayList<>();
        
        for (Pattern pattern : EVENT_PATTERNS) {
            Matcher matcher = pattern.matcher(userInput);
            while (matcher.find()) {
                DetectedEvent event = parseEventFromMatch(matcher, pattern);
                if (event != null && isValidEvent(event) && isEventCreationContext(userInput, matcher)) {
                    detectedEvents.add(event);
                    logger.info("Detected event: " + event);
                } else if (event != null) {
                    logger.info("Filtered out potential false positive: " + event + " from input: " + userInput);
                }
            }
        }
        
        // Remove duplicates and validate
        detectedEvents = deduplicateEvents(detectedEvents);
        
        return new CalendarEventAnalysis(
            !detectedEvents.isEmpty(),
            detectedEvents,
            generateEnhancedPromptAddition(detectedEvents)
        );
    }
    
    private DetectedEvent parseEventFromMatch(Matcher matcher, Pattern pattern) {
        try {
            String eventType = matcher.group(1).trim().toLowerCase();
            
            // Validate it's actually an event type
            if (!isRecognizedEventType(eventType)) {
                return null;
            }
            
            int daysFromToday = calculateDaysFromToday(matcher);
            if (daysFromToday < 0 || daysFromToday > 365) {
                return null; // Invalid date range
            }
            
            String cleanTitle = cleanEventTitle(eventType);
            String description = generateDescription(eventType, daysFromToday);
            
            return new DetectedEvent(cleanTitle, daysFromToday, description, eventType);
            
        } catch (Exception e) {
            logger.warning("Error parsing event from match: " + e.getMessage());
            return null;
        }
    }
    
    private int calculateDaysFromToday(Matcher matcher) {
        LocalDate today = LocalDate.now();
        
        if (matcher.groupCount() >= 2) {
            String timeRef = matcher.group(2).toLowerCase();
            
            // Handle relative references
            switch (timeRef) {
                case "today": return 0;
                case "tomorrow": return 1;
                case "next week": return 7;
                case "next month": return 30;
            }
            
            // Handle day names
            if (timeRef.matches("(monday|tuesday|wednesday|thursday|friday|saturday|sunday)")) {
                return calculateDaysUntilDayOfWeek(timeRef, false);
            }
            
            // Handle "next [day]"
            if (matcher.group(2).toLowerCase().startsWith("next ")) {
                String dayName = matcher.group(2).toLowerCase().replace("next ", "");
                return calculateDaysUntilDayOfWeek(dayName, true);
            }
            
            // Handle numbered time periods
            if (matcher.groupCount() >= 3) {
                try {
                    int number = Integer.parseInt(matcher.group(2));
                    String unit = matcher.group(3).toLowerCase();
                    
                    if (unit.contains("day")) return number;
                    if (unit.contains("week")) return number * 7;
                    if (unit.contains("month")) return number * 30;
                } catch (NumberFormatException e) {
                    // Continue to month parsing
                }
                
                // Handle month + day format
                String monthName = matcher.group(2).toLowerCase();
                if (MONTH_TO_NUMBER.containsKey(monthName) && matcher.groupCount() >= 3) {
                    try {
                        int month = MONTH_TO_NUMBER.get(monthName);
                        int day = Integer.parseInt(matcher.group(3));
                        
                        LocalDate targetDate = LocalDate.of(today.getYear(), month, day);
                        if (targetDate.isBefore(today)) {
                            targetDate = targetDate.plusYears(1); // Next year
                        }
                        
                        return (int) ChronoUnit.DAYS.between(today, targetDate);
                    } catch (Exception e) {
                        logger.warning("Error parsing month/day: " + e.getMessage());
                    }
                }
            }
        }
        
        return -1; // Invalid
    }
    
    private int calculateDaysUntilDayOfWeek(String dayName, boolean nextWeek) {
        LocalDate today = LocalDate.now();
        int targetDayOfWeek = getDayOfWeekNumber(dayName);
        int currentDayOfWeek = today.getDayOfWeek().getValue();
        
        int daysUntil = targetDayOfWeek - currentDayOfWeek;
        
        if (nextWeek || daysUntil <= 0) {
            daysUntil += 7;
        }
        
        return daysUntil;
    }
    
    private int getDayOfWeekNumber(String dayName) {
        switch (dayName.toLowerCase()) {
            case "monday": return 1;
            case "tuesday": return 2;
            case "wednesday": return 3;
            case "thursday": return 4;
            case "friday": return 5;
            case "saturday": return 6;
            case "sunday": return 7;
            default: return 1;
        }
    }
    
    private boolean isRecognizedEventType(String eventType) {
        return EVENT_TYPES.stream().anyMatch(type -> 
            eventType.contains(type) || type.contains(eventType)
        );
    }
    
    /**
     * Check if the input is asking for information about existing memories/data
     * rather than trying to create a new calendar event
     */
    private boolean isQuestionAboutExistingInfo(String userInput) {
        String lowerInput = userInput.toLowerCase().trim();
        
        // Common question patterns that indicate information requests, not event creation
        String[] questionPatterns = {
            "when is my",
            "what is my", 
            "what's my",
            "when's my",
            "what time is",
            "what day is",
            "tell me about",
            "what are the details",
            "remind me about",
            "do you know when",
            "do you remember when",
            "can you tell me",
            "what is the name of",
            "what's the name of",
            "who is my",
            "who's my",
            "where is my",
            "where's my",
            "how old is",
            "what date is"
        };
        
        // Check for question patterns
        for (String pattern : questionPatterns) {
            if (lowerInput.startsWith(pattern) || lowerInput.contains(pattern)) {
                logger.info("Detected question pattern: '" + pattern + "' in input: " + userInput);
                return true;
            }
        }
        
        // Check for question words at the beginning
        String[] questionWords = {"when", "what", "where", "who", "how", "which", "why"};
        for (String qWord : questionWords) {
            if (lowerInput.startsWith(qWord + " ")) {
                logger.info("Detected question word: '" + qWord + "' at start of input: " + userInput);
                return true;
            }
        }
        
        // Check for question marks (obvious questions)
        if (lowerInput.contains("?")) {
            logger.info("Detected question mark in input: " + userInput);
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if the matched text represents actual event creation rather than 
     * information recall or casual mention
     */
    private boolean isEventCreationContext(String userInput, Matcher matcher) {
        String lowerInput = userInput.toLowerCase().trim();
        
        // If this was already caught as a question, it's not event creation
        if (isQuestionAboutExistingInfo(userInput)) {
            return false;
        }
        
        // Look for event creation indicators
        String[] creationIndicators = {
            "i have a", "i have an", "i've got a", "i've got an",
            "there's a", "there's an", "there is a", "there is an",
            "i'm going to", "i will", "i'll have",
            "schedule", "book", "plan", "arrange",
            "reminder", "set up", "add to calendar"
        };
        
        for (String indicator : creationIndicators) {
            if (lowerInput.contains(indicator)) {
                logger.info("Detected event creation indicator: '" + indicator + "' in input: " + userInput);
                return true;
            }
        }
        
        // Look for future time references that suggest scheduling
        String[] futureIndicators = {
            "tomorrow", "next week", "next month", "in a few", "coming up",
            "this weekend", "next weekend", "later this", "next year"
        };
        
        for (String indicator : futureIndicators) {
            if (lowerInput.contains(indicator) && !isQuestionAboutExistingInfo(userInput)) {
                logger.info("Detected future time reference: '" + indicator + "' in input: " + userInput);
                return true;
            }
        }
        
        // Check for statement patterns vs question patterns
        // Statements typically start with "I", "My", "The", etc.
        // Questions typically start with "When", "What", "Where", etc.
        String[] statementStarters = {"i ", "my ", "the ", "a ", "an ", "this ", "that "};
        for (String starter : statementStarters) {
            if (lowerInput.startsWith(starter) && !isQuestionAboutExistingInfo(userInput)) {
                // Additional check: make sure it's not just "my birthday is..." as information sharing
                // vs "my birthday is next week" as event creation
                if (lowerInput.contains(" is ") && !containsFutureTimeReference(lowerInput)) {
                    return false; // "My birthday is March 15" = information, not event
                }
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Helper method to check if text contains future time references
     */
    private boolean containsFutureTimeReference(String text) {
        String[] futureRefs = {
            "tomorrow", "next", "in ", "coming", "upcoming", "later", "soon",
            "this weekend", "this week", "this month", "this year"
        };
        
        for (String ref : futureRefs) {
            if (text.contains(ref)) {
                return true;
            }
        }
        return false;
    }
    
    private String cleanEventTitle(String eventType) {
        // Clean and format the event title
        return Arrays.stream(eventType.split("\\s+"))
            .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
            .reduce((a, b) -> a + " " + b)
            .orElse(eventType);
    }
    
    private String generateDescription(String eventType, int daysFromToday) {
        String urgency = daysFromToday <= 3 ? "urgent" : daysFromToday <= 7 ? "upcoming" : "scheduled";
        
        if (eventType.contains("wedding")) {
            return daysFromToday <= 7 ? "Wedding celebration (soon!)" : "Wedding celebration";
        } else if (eventType.contains("meeting")) {
            return urgency.substring(0, 1).toUpperCase() + urgency.substring(1) + " meeting";
        } else if (eventType.contains("birthday")) {
            return "Birthday celebration 🎂";
        } else if (eventType.contains("appointment")) {
            return urgency.substring(0, 1).toUpperCase() + urgency.substring(1) + " appointment";
        } else {
            return urgency.substring(0, 1).toUpperCase() + urgency.substring(1) + " " + eventType;
        }
    }
    
    private boolean isValidEvent(DetectedEvent event) {
        if (event.getTitle() == null || event.getTitle().trim().isEmpty()) {
            return false;
        }
        
        if (event.getDaysFromToday() < 0 || event.getDaysFromToday() > 365) {
            return false;
        }
        
        // Additional validation: reject events with problematic titles
        String title = event.getTitle().toLowerCase();
        
        // Reject single words that are likely not events
        String[] problematicTitles = {
            "with", "from", "to", "in", "on", "at", "by", "for",
            "the", "a", "an", "and", "or", "but", "is", "are", "was", "were",
            "my", "your", "his", "her", "their", "our",
            "when", "what", "where", "who", "how", "why"
        };
        
        for (String problematic : problematicTitles) {
            if (title.equals(problematic) || title.startsWith(problematic + " ")) {
                logger.warning("Rejecting event with problematic title: " + event.getTitle());
                return false;
            }
        }
        
        // Reject if title is too short (likely not a real event)
        if (event.getTitle().trim().length() < 3) {
            logger.warning("Rejecting event with too short title: " + event.getTitle());
            return false;
        }
        
        return true;
    }
    
    private List<DetectedEvent> deduplicateEvents(List<DetectedEvent> events) {
        Map<String, DetectedEvent> uniqueEvents = new LinkedHashMap<>();
        
        for (DetectedEvent event : events) {
            String key = event.getTitle().toLowerCase() + "_" + event.getDaysFromToday();
            if (!uniqueEvents.containsKey(key)) {
                uniqueEvents.put(key, event);
            }
        }
        
        return new ArrayList<>(uniqueEvents.values());
    }
    
    private String generateEnhancedPromptAddition(List<DetectedEvent> events) {
        if (events.isEmpty()) {
            return "";
        }
        
        StringBuilder addition = new StringBuilder();
        addition.append("\n\n=== CALENDAR EVENTS CONFIRMED FOR CREATION ===\n");
        addition.append("CRITICAL: These are genuine event creation requests (NOT questions about existing info):\n");
        
        for (DetectedEvent event : events) {
            addition.append("- ").append(event.getTitle())
                   .append(" in ").append(event.getDaysFromToday())
                   .append(" days from today\n");
        }
        
        addition.append("\nMANDATORY RESPONSE FORMAT: You MUST include these events in your Calendar section using EXACTLY this format:\n");
        for (DetectedEvent event : events) {
            addition.append("Calendar: ").append(event.getDaysFromToday())
                   .append(" days from today ").append(event.getTitle()).append(".!.\n");
        }
        addition.append("\nIMPORTANT: Do NOT create calendar events for information questions like 'when is my birthday' or 'what's my father's name'.\n");
        addition.append("Only create events when the user is actually scheduling or planning something for the future.\n");
        addition.append("================================\n");
        
        return addition.toString();
    }
    
    // Inner classes
    public static class CalendarEventAnalysis {
        private final boolean hasEvents;
        private final List<DetectedEvent> events;
        private final String promptAddition;
        
        public CalendarEventAnalysis(boolean hasEvents, List<DetectedEvent> events, String promptAddition) {
            this.hasEvents = hasEvents;
            this.events = events;
            this.promptAddition = promptAddition;
        }
        
        public boolean hasEvents() { return hasEvents; }
        public List<DetectedEvent> getEvents() { return events; }
        public String getPromptAddition() { return promptAddition; }
        
        @Override
        public String toString() {
            return "CalendarEventAnalysis{hasEvents=" + hasEvents + 
                   ", eventCount=" + events.size() + "}";
        }
    }
    
    public static class DetectedEvent {
        private final String title;
        private final int daysFromToday;
        private final String description;
        private final String originalEventType;
        
        public DetectedEvent(String title, int daysFromToday, String description, String originalEventType) {
            this.title = title;
            this.daysFromToday = daysFromToday;
            this.description = description;
            this.originalEventType = originalEventType;
        }
        
        public String getTitle() { return title; }
        public int getDaysFromToday() { return daysFromToday; }
        public String getDescription() { return description; }
        public String getOriginalEventType() { return originalEventType; }
        
        @Override
        public String toString() {
            return "DetectedEvent{title='" + title + "', daysFromToday=" + daysFromToday + "}";
        }
    }
}
