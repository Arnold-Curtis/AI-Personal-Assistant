package com.example.demo;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.*;
import java.util.logging.Logger;


@Service
public class EnhancedCalendarEventService {
    
    private static final Logger logger = Logger.getLogger(EnhancedCalendarEventService.class.getName());
    
    private final AIEventParsingService aiEventParsingService;
    private final UserRepository userRepository;
    private final CalendarEventEnhancementService legacyService; 
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Autowired
    public EnhancedCalendarEventService(
            AIEventParsingService aiEventParsingService,
            UserRepository userRepository,
            CalendarEventEnhancementService legacyService) {
        this.aiEventParsingService = aiEventParsingService;
        this.userRepository = userRepository;
        this.legacyService = legacyService;
    }
    
    
    public CalendarAnalysisResult analyzeForCalendarEventsAI(String userInput) {
        
        try {
            
            boolean hasSchedulingIntent = aiEventParsingService.containsEventSchedulingIntent(userInput);
            
            if (!hasSchedulingIntent) {
                return new CalendarAnalysisResult(false, new ArrayList<>(), "");
            }
            
            
            List<AIEventParsingService.ParsedEvent> aiParsedEvents = 
                aiEventParsingService.parseEventsFromNaturalLanguage(userInput);
            
            
            List<CalendarEventInfo> eventInfos = new ArrayList<>();
            for (AIEventParsingService.ParsedEvent aiEvent : aiParsedEvents) {
                CalendarEventInfo eventInfo = new CalendarEventInfo(
                    aiEvent.getTitle(),
                    aiEvent.getDaysFromToday(),
                    aiEvent.getConfidence(),
                    aiEvent.getReasoning()
                );
                eventInfos.add(eventInfo);
            }
            
            
            String promptAddition = generateAIPromptAddition(eventInfos);
            
            return new CalendarAnalysisResult(!eventInfos.isEmpty(), eventInfos, promptAddition);
            
        } catch (Exception e) {
            logger.severe("Error in AI calendar analysis: " + e.getMessage());
            
            logger.info("Falling back to legacy pattern-based detection");
            var legacyResult = legacyService.analyzeForCalendarEvents(userInput);
            return convertLegacyResult(legacyResult);
        }
    }
    
    
    @Transactional
    public EventCreationResult createEventsFromInputAI(UUID userId, String userInput) {
        
        List<CalendarEvent> createdEvents = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        try {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            
            List<AIEventParsingService.ParsedEvent> parsedEvents = 
                aiEventParsingService.parseEventsFromNaturalLanguage(userInput);
            
            for (AIEventParsingService.ParsedEvent parsedEvent : parsedEvents) {
                try {
                    CalendarEvent event = createCalendarEventFromAI(parsedEvent, user, userInput);
                    if (event != null) {
                        createdEvents.add(event);
                    }
                } catch (Exception e) {
                    String error = "Failed to create event '" + parsedEvent.getTitle() + "': " + e.getMessage();
                    errors.add(error);
                    logger.warning(error);
                }
            }
            
        } catch (Exception e) {
            errors.add("Failed to process input with AI: " + e.getMessage());
            logger.severe("Error creating events from AI input: " + e.getMessage());
        }
        
        return new EventCreationResult(createdEvents, errors);
    }
    
    private CalendarEvent createCalendarEventFromAI(AIEventParsingService.ParsedEvent parsedEvent, 
                                                   User user, String originalInput) {
        CalendarEvent event = new CalendarEvent();
        event.setUser(user);
        event.setTitle(parsedEvent.getTitle());
        event.setStart(parsedEvent.getDate());
        event.setAllDay(true);
        
        
        String description = String.format(
            "Created from: \"%s\"\nAI Confidence: %.1f%%\nReasoning: %s", 
            originalInput, 
            parsedEvent.getConfidence() * 100,
            parsedEvent.getReasoning()
        );
        event.setDescription(description);
        
        event.setEventColor(determineEventColorIntelligent(parsedEvent.getTitle()));
        
        
        entityManager.persist(event);
        
        return event;
    }
    
    
    private String determineEventColorIntelligent(String eventTitle) {
        String lowerTitle = eventTitle.toLowerCase();
        
        
        if (lowerTitle.contains("meeting") || lowerTitle.contains("interview") || 
            lowerTitle.contains("conference") || lowerTitle.contains("seminar")) {
            return "#2563eb"; 
        }
        
        
        if (lowerTitle.contains("wedding") || lowerTitle.contains("birthday") || 
            lowerTitle.contains("party") || lowerTitle.contains("celebration")) {
            return "#dc2626"; 
        }
        
        
        if (lowerTitle.contains("doctor") || lowerTitle.contains("appointment") || 
            lowerTitle.contains("checkup") || lowerTitle.contains("medical")) {
            return "#059669"; 
        }
        
        
        if (lowerTitle.contains("meet") || lowerTitle.contains("date") || 
            lowerTitle.contains("dinner") || lowerTitle.contains("lunch")) {
            return "#7c3aed"; 
        }
        
        
        if (lowerTitle.contains("exam") || lowerTitle.contains("test") || 
            lowerTitle.contains("class") || lowerTitle.contains("training")) {
            return "#ea580c"; 
        }
        
        
        if (lowerTitle.contains("trip") || lowerTitle.contains("vacation") || 
            lowerTitle.contains("travel") || lowerTitle.contains("flight")) {
            return "#0891b2"; 
        }
        
        return "#6b7280"; 
    }
    
    private String generateAIPromptAddition(List<CalendarEventInfo> events) {
        if (events.isEmpty()) {
            return "";
        }
        
        StringBuilder addition = new StringBuilder();
        addition.append("\n\n=== CALENDAR EVENTS CONFIRMED FOR CREATION ===\n");
        addition.append("CRITICAL: These are genuine event creation requests (NOT questions about existing info):\n");
        
        for (CalendarEventInfo event : events) {
            addition.append("- ").append(event.getTitle())
                   .append(" in ").append(event.getDaysFromToday())
                   .append(" days from today (Confidence: ")
                   .append(String.format("%.1f%%", event.getConfidence() * 100))
                   .append(")\n");
        }
        
        addition.append("\nMANDATORY RESPONSE FORMAT: You MUST include these events in your Calendar section using EXACTLY this format:\n");
        for (CalendarEventInfo event : events) {
            addition.append("Calendar: ").append(event.getDaysFromToday())
                   .append(" days from today ").append(event.getTitle()).append(".!.\n");
        }
        addition.append("\nIMPORTANT: These events were detected using advanced natural language understanding.\n");
        addition.append("Do NOT create calendar events for information questions.\n");
        addition.append("Only include events when the user is actually scheduling something for the future.\n");
        addition.append("================================\n");
        
        return addition.toString();
    }
    
    private CalendarAnalysisResult convertLegacyResult(
            CalendarEventEnhancementService.CalendarEventAnalysis legacyResult) {
        List<CalendarEventInfo> eventInfos = new ArrayList<>();
        
        for (var legacyEvent : legacyResult.getEvents()) {
            CalendarEventInfo eventInfo = new CalendarEventInfo(
                legacyEvent.getTitle(),
                legacyEvent.getDaysFromToday(),
                0.6, 
                "Legacy pattern detection"
            );
            eventInfos.add(eventInfo);
        }
        
        return new CalendarAnalysisResult(
            legacyResult.hasEvents(),
            eventInfos,
            legacyResult.getPromptAddition()
        );
    }
    
    
    public static class CalendarEventInfo {
        private final String title;
        private final int daysFromToday;
        private final double confidence;
        private final String reasoning;
        
        public CalendarEventInfo(String title, int daysFromToday, double confidence, String reasoning) {
            this.title = title;
            this.daysFromToday = daysFromToday;
            this.confidence = confidence;
            this.reasoning = reasoning;
        }
        
        public String getTitle() { return title; }
        public int getDaysFromToday() { return daysFromToday; }
        public double getConfidence() { return confidence; }
        public String getReasoning() { return reasoning; }
        
        @Override
        public String toString() {
            return String.format("CalendarEventInfo{title='%s', days=%d, confidence=%.2f, reasoning='%s'}", 
                               title, daysFromToday, confidence, reasoning);
        }
    }
    
    
    public static class CalendarAnalysisResult {
        private final boolean hasEvents;
        private final List<CalendarEventInfo> events;
        private final String promptAddition;
        
        public CalendarAnalysisResult(boolean hasEvents, List<CalendarEventInfo> events, String promptAddition) {
            this.hasEvents = hasEvents;
            this.events = events;
            this.promptAddition = promptAddition;
        }
        
        public boolean hasEvents() { return hasEvents; }
        public List<CalendarEventInfo> getEvents() { return events; }
        public String getPromptAddition() { return promptAddition; }
        
        @Override
        public String toString() {
            return "CalendarAnalysisResult{hasEvents=" + hasEvents + 
                   ", eventCount=" + events.size() + "}";
        }
    }
    
    
    public static class EventCreationResult {
        private final List<CalendarEvent> createdEvents;
        private final List<String> errors;
        
        public EventCreationResult(List<CalendarEvent> createdEvents, List<String> errors) {
            this.createdEvents = createdEvents;
            this.errors = errors;
        }
        
        public List<CalendarEvent> getCreatedEvents() { return createdEvents; }
        public List<String> getErrors() { return errors; }
        public boolean hasEvents() { return !createdEvents.isEmpty(); }
        public boolean hasErrors() { return !errors.isEmpty(); }
        
        @Override
        public String toString() {
            return String.format("EventCreationResult{created=%d, errors=%d}", 
                               createdEvents.size(), errors.size());
        }
    }
}

