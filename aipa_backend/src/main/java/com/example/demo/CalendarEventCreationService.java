package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.logging.Logger;
import jakarta.transaction.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class CalendarEventCreationService {
    
    private static final Logger logger = Logger.getLogger(CalendarEventCreationService.class.getName());
    
    private final UserRepository userRepository;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Autowired
    public CalendarEventCreationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    
    private static final List<EventPattern> EVENT_EXTRACTION_PATTERNS = Arrays.asList(
        new EventPattern(
            Pattern.compile("(?:i have|there'?s|i'?ve got)\\s+(?:a|an)?\\s*([^\\s]+(?:\\s+[^\\s]+)*)\\s+in\\s+(\\d+)\\s+(days?|weeks?|months?)", Pattern.CASE_INSENSITIVE),
            new int[]{1, 2, 3} 
        ),
        new EventPattern(
            Pattern.compile("(?:i have|there'?s|i'?ve got)\\s+(?:a|an)?\\s*([^\\s]+(?:\\s+[^\\s]+)*)\\s+(tomorrow|today)", Pattern.CASE_INSENSITIVE),
            new int[]{1, 2, -1} 
        ),
        new EventPattern(
            Pattern.compile("(?:i have|there'?s|i'?ve got)\\s+(?:a|an)?\\s*([^\\s]+(?:\\s+[^\\s]+)*)\\s+next\\s+(week|month|monday|tuesday|wednesday|thursday|friday|saturday|sunday)", Pattern.CASE_INSENSITIVE),
            new int[]{1, 2, -1} 
        ),
        new EventPattern(
            Pattern.compile("(?:i'm going|i'll be)\\s+(?:to\\s+)?(?:a|an)?\\s*([^\\s]+(?:\\s+[^\\s]+)*)\\s+(?:in\\s+)?(\\d+)\\s+(days?|weeks?|months?)", Pattern.CASE_INSENSITIVE),
            new int[]{1, 2, 3} 
        ),
        new EventPattern(
            Pattern.compile("([^\\s]+(?:\\s+[^\\s]+)*)\\s+(?:is\\s+)?(?:on\\s+)?(january|february|march|april|may|june|july|august|september|october|november|december)\\s+(\\d{1,2})(?:st|nd|rd|th)?", Pattern.CASE_INSENSITIVE),
            new int[]{1, 2, 3} 
        )
    );
    
    private static final Map<String, Integer> MONTH_TO_NUMBER;
    private static final Map<String, Integer> TIME_WORD_TO_DAYS;
    
    static {
        Map<String, Integer> months = new HashMap<>();
        months.put("january", 1); months.put("february", 2); months.put("march", 3);
        months.put("april", 4); months.put("may", 5); months.put("june", 6);
        months.put("july", 7); months.put("august", 8); months.put("september", 9);
        months.put("october", 10); months.put("november", 11); months.put("december", 12);
        MONTH_TO_NUMBER = Collections.unmodifiableMap(months);
        
        Map<String, Integer> timeWords = new HashMap<>();
        timeWords.put("today", 0); timeWords.put("tomorrow", 1);
        timeWords.put("week", 7); timeWords.put("month", 30);
        timeWords.put("monday", getDaysUntilWeekday(1)); timeWords.put("tuesday", getDaysUntilWeekday(2));
        timeWords.put("wednesday", getDaysUntilWeekday(3)); timeWords.put("thursday", getDaysUntilWeekday(4));
        timeWords.put("friday", getDaysUntilWeekday(5)); timeWords.put("saturday", getDaysUntilWeekday(6));
        timeWords.put("sunday", getDaysUntilWeekday(7));
        TIME_WORD_TO_DAYS = Collections.unmodifiableMap(timeWords);
    }
    
    private static int getDaysUntilWeekday(int targetDayOfWeek) {
        LocalDate today = LocalDate.now();
        int currentDayOfWeek = today.getDayOfWeek().getValue();
        int daysUntil = targetDayOfWeek - currentDayOfWeek;
        if (daysUntil <= 0) {
            daysUntil += 7; 
        }
        return daysUntil;
    }
    
    
    @Transactional
    public EventCreationResult createEventsFromInput(UUID userId, String userInput) {
        logger.info("Creating events from input: " + userInput);
        
        List<CalendarEvent> createdEvents = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        try {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            List<ParsedEvent> parsedEvents = parseEventsFromInput(userInput);
            
            for (ParsedEvent parsedEvent : parsedEvents) {
                try {
                    CalendarEvent event = createCalendarEvent(parsedEvent, user);
                    if (event != null) {
                        createdEvents.add(event);
                        logger.info("Successfully created event: " + event.getTitle() + " on " + event.getStart());
                    }
                } catch (Exception e) {
                    String error = "Failed to create event '" + parsedEvent.title + "': " + e.getMessage();
                    errors.add(error);
                    logger.warning(error);
                }
            }
            
        } catch (Exception e) {
            errors.add("Failed to process input: " + e.getMessage());
            logger.severe("Error creating events from input: " + e.getMessage());
        }
        
        return new EventCreationResult(createdEvents, errors);
    }
    
    private List<ParsedEvent> parseEventsFromInput(String userInput) {
        List<ParsedEvent> events = new ArrayList<>();
        
        for (EventPattern pattern : EVENT_EXTRACTION_PATTERNS) {
            Matcher matcher = pattern.pattern.matcher(userInput);
            while (matcher.find()) {
                try {
                    ParsedEvent event = parseEventFromMatch(matcher, pattern);
                    if (event != null && isValidEventTitle(event.title)) {
                        events.add(event);
                        logger.info("Parsed event: " + event);
                    }
                } catch (Exception e) {
                    logger.warning("Error parsing event from match: " + e.getMessage());
                }
            }
        }
        
        return removeDuplicateEvents(events);
    }
    
    private ParsedEvent parseEventFromMatch(Matcher matcher, EventPattern pattern) {
        String title = matcher.group(pattern.groups[0]).trim();
        title = cleanEventTitle(title);
        
        LocalDate eventDate = calculateEventDate(matcher, pattern);
        if (eventDate == null) {
            return null;
        }
        
        return new ParsedEvent(title, eventDate);
    }
    
    private LocalDate calculateEventDate(Matcher matcher, EventPattern pattern) {
        try {
            if (pattern.groups[1] == -1) {
                
                return LocalDate.now();
            }
            
            String secondGroup = matcher.group(pattern.groups[1]).toLowerCase();
            
            
            if (secondGroup.matches("\\d+")) {
                int number = Integer.parseInt(secondGroup);
                String unit = matcher.group(pattern.groups[2]).toLowerCase();
                
                LocalDate today = LocalDate.now();
                switch (unit) {
                    case "day":
                    case "days":
                        return today.plusDays(number);
                    case "week":
                    case "weeks":
                        return today.plusWeeks(number);
                    case "month":
                    case "months":
                        return today.plusMonths(number);
                    default:
                        return null;
                }
            }
            
            
            if (MONTH_TO_NUMBER.containsKey(secondGroup)) {
                int month = MONTH_TO_NUMBER.get(secondGroup);
                int day = Integer.parseInt(matcher.group(pattern.groups[2]));
                
                LocalDate today = LocalDate.now();
                LocalDate eventDate = LocalDate.of(today.getYear(), month, day);
                
                
                if (eventDate.isBefore(today)) {
                    eventDate = eventDate.plusYears(1);
                }
                
                return eventDate;
            }
            
            
            if (TIME_WORD_TO_DAYS.containsKey(secondGroup)) {
                int daysToAdd = TIME_WORD_TO_DAYS.get(secondGroup);
                if (secondGroup.equals("week") || secondGroup.equals("month")) {
                    
                    if (secondGroup.equals("week")) {
                        return LocalDate.now().plusWeeks(1);
                    } else {
                        return LocalDate.now().plusMonths(1);
                    }
                } else {
                    return LocalDate.now().plusDays(daysToAdd);
                }
            }
            
            return null;
            
        } catch (Exception e) {
            logger.warning("Error calculating event date: " + e.getMessage());
            return null;
        }
    }
    
    private String cleanEventTitle(String title) {
        return title.replaceAll("\\b(a|an|the)\\b", "")
                   .replaceAll("\\s+", " ")
                   .trim();
    }
    
    private boolean isValidEventTitle(String title) {
        if (title == null || title.trim().length() < 2) {
            return false;
        }
        
        String lowerTitle = title.toLowerCase().trim();
        
        
        Set<String> problematicTitles = Set.of(
            "with", "from", "to", "in", "on", "at", "by", "for",
            "the", "a", "an", "and", "or", "but", "is", "are", "was", "were",
            "when", "what", "where", "who", "how", "why"
        );
        
        return !problematicTitles.contains(lowerTitle) && 
               title.length() <= 100 &&
               !title.matches("^\\d+$");
    }
    
    private List<ParsedEvent> removeDuplicateEvents(List<ParsedEvent> events) {
        Set<String> seen = new HashSet<>();
        List<ParsedEvent> unique = new ArrayList<>();
        
        for (ParsedEvent event : events) {
            String signature = event.title.toLowerCase() + "_" + event.date.toString();
            if (!seen.contains(signature)) {
                seen.add(signature);
                unique.add(event);
            }
        }
        
        return unique;
    }
    
    private CalendarEvent createCalendarEvent(ParsedEvent parsedEvent, User user) {
        CalendarEvent event = new CalendarEvent();
        event.setUser(user);
        event.setTitle(formatEventTitle(parsedEvent.title));
        event.setStart(parsedEvent.date);
        event.setAllDay(true);
        event.setDescription("Created from: \"" + parsedEvent.title + "\"");
        event.setEventColor(determineEventColor(parsedEvent.title));
        
        
        entityManager.persist(event);
        
        return event;
    }
    
    private String formatEventTitle(String title) {
        
        return Arrays.stream(title.split("\\s+"))
                    .map(word -> word.substring(0, 1).toUpperCase() + 
                               (word.length() > 1 ? word.substring(1).toLowerCase() : ""))
                    .reduce((a, b) -> a + " " + b)
                    .orElse(title);
    }
    
    private String determineEventColor(String title) {
        String lowerTitle = title.toLowerCase();
        
        if (lowerTitle.contains("birthday")) return "#FF6B6B";
        if (lowerTitle.contains("meeting") || lowerTitle.contains("work")) return "#4ECDC4";
        if (lowerTitle.contains("appointment") || lowerTitle.contains("doctor")) return "#45B7D1";
        if (lowerTitle.contains("wedding") || lowerTitle.contains("party")) return "#96CEB4";
        if (lowerTitle.contains("exam") || lowerTitle.contains("test")) return "#FFEAA7";
        
        return "#DDA0DD"; 
    }
    
    
    private static class EventPattern {
        final Pattern pattern;
        final int[] groups; 
        
        EventPattern(Pattern pattern, int[] groups) {
            this.pattern = pattern;
            this.groups = groups;
        }
    }
    
    private static class ParsedEvent {
        final String title;
        final LocalDate date;
        
        ParsedEvent(String title, LocalDate date) {
            this.title = title;
            this.date = date;
        }
        
        @Override
        public String toString() {
            return String.format("ParsedEvent{title='%s', date=%s}", title, date);
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
            return String.format("EventCreationResult{events=%d, errors=%d}", 
                               createdEvents.size(), errors.size());
        }
    }
}

