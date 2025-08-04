package com.example.demo;

import org.springframework.stereotype.Service;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Set;
import java.util.HashSet;

@Service
public class CalendarResponseValidationService {
    
    private static final Logger logger = Logger.getLogger(CalendarResponseValidationService.class.getName());
    
    // Patterns to detect calendar events in responses
    private static final Pattern CALENDAR_NONE_PATTERN = Pattern.compile(
        "Calendar:\\s*None\\.!\\.\\.!", Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern EVENT_MENTION_PATTERN = Pattern.compile(
        "\\b(wedding|meeting|appointment|birthday|party|conference|exam|interview|vacation|trip|date|dinner|lunch|call)\\b.*?\\b(tomorrow|today|next week|next month|in \\d+ (days?|weeks?|months?)|on (monday|tuesday|wednesday|thursday|friday|saturday|sunday))\\b",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    /**
     * Validates and potentially fixes calendar responses to ensure accuracy and prevent duplicates
     */
    public String validateAndFixCalendarResponse(String originalResponse, String userInput) {
        logger.info("Validating calendar response for input: " + userInput);
        
        // First, check for and fix duplicate calendar entries
        String dedupedResponse = removeDuplicateCalendarEntries(originalResponse);
        
        // Check if response says "Calendar: None" but events are clearly mentioned
        if (CALENDAR_NONE_PATTERN.matcher(dedupedResponse).find()) {
            
            // Check if events were mentioned in the user input
            Matcher eventMatcher = EVENT_MENTION_PATTERN.matcher(userInput);
            if (eventMatcher.find()) {
                logger.warning("Detected calendar parsing error - events mentioned but Calendar: None in response");
                
                // Try to extract and fix the calendar entry
                String fixedResponse = attemptCalendarFix(dedupedResponse, userInput);
                if (!fixedResponse.equals(dedupedResponse)) {
                    logger.info("Successfully fixed calendar response");
                    return fixedResponse;
                }
            }
        }
        
        // Additional validation checks
        String validatedResponse = performAdditionalValidation(dedupedResponse, userInput);
        
        return validatedResponse;
    }
    
    /**
     * Removes duplicate calendar entries from the response
     */
    private String removeDuplicateCalendarEntries(String response) {
        try {
            // Pattern to find all calendar entries
            Pattern calendarEntryPattern = Pattern.compile(
                "Calendar:\\s*(\\d+)\\s*days?\\s*from\\s*today\\s+([^.!\n\r]+?)(?:\\.!\\.|\\n|$)",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
            );
            
            Matcher matcher = calendarEntryPattern.matcher(response);
            Set<String> seenEvents = new HashSet<>();
            StringBuilder result = new StringBuilder();
            int lastEnd = 0;
            
            while (matcher.find()) {
                String days = matcher.group(1);
                String eventTitle = matcher.group(2).trim()
                    .replace("from today", "")
                    .replaceAll("\\s+", " ")
                    .trim();
                
                // Create signature for deduplication
                String signature = eventTitle.toLowerCase() + "_" + days;
                
                // Only keep the first occurrence of each event
                if (!seenEvents.contains(signature)) {
                    seenEvents.add(signature);
                    // Add the text before this match
                    result.append(response.substring(lastEnd, matcher.start()));
                    // Add the cleaned calendar entry
                    result.append("Calendar: ").append(days).append(" days from today ").append(eventTitle).append(".!.");
                    lastEnd = matcher.end();
                } else {
                    // Skip this duplicate, but preserve text before it
                    result.append(response.substring(lastEnd, matcher.start()));
                    lastEnd = matcher.end();
                    logger.info("Removed duplicate calendar entry: " + eventTitle);
                }
            }
            
            // Add remaining text
            result.append(response.substring(lastEnd));
            
            return result.toString();
            
        } catch (Exception e) {
            logger.warning("Error removing duplicates: " + e.getMessage());
            return response;
        }
    }
    
    private String attemptCalendarFix(String response, String userInput) {
        try {
            // Extract event information from user input
            CalendarEventInfo eventInfo = extractEventFromInput(userInput);
            
            if (eventInfo != null) {
                // Replace "Calendar: None.!..!." with proper calendar entry
                String fixedCalendarEntry = String.format(
                    "Calendar: %d days from today %s.!..!.",
                    eventInfo.getDaysFromToday(),
                    eventInfo.getEventTitle()
                );
                
                String fixedResponse = response.replaceFirst(
                    "Calendar:\\s*None\\.!\\.\\.!",
                    fixedCalendarEntry
                );
                
                // Also update Part 2 response to acknowledge the event
                String acknowledgment = String.format(
                    "I've noted your %s and added it to your calendar for %s so you don't forget!",
                    eventInfo.getEventTitle().toLowerCase(),
                    formatTimeReference(eventInfo.getDaysFromToday())
                );
                
                // Try to replace generic response with specific acknowledgment
                fixedResponse = fixedResponse.replaceFirst(
                    "I understand your request and will help you with that\\.",
                    acknowledgment
                );
                
                return fixedResponse;
            }
            
        } catch (Exception e) {
            logger.warning("Error attempting calendar fix: " + e.getMessage());
        }
        
        return response; // Return original if fix fails
    }
    
    private CalendarEventInfo extractEventFromInput(String userInput) {
        // Try various patterns to extract event information
        
        // Pattern: "I have a wedding in 2 weeks"
        Pattern pattern1 = Pattern.compile(
            "(?:i have|there's|i've got)\\s+(?:a|an)?\\s*(\\w+(?:\\s+\\w+)?)\\s+in\\s+(\\d+)\\s+(days?|weeks?|months?)",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher1 = pattern1.matcher(userInput);
        if (matcher1.find()) {
            String eventType = cleanEventTitle(matcher1.group(1));
            int days = calculateDays(Integer.parseInt(matcher1.group(2)), matcher1.group(3));
            return new CalendarEventInfo(eventType, days);
        }
        
        // Pattern: "wedding tomorrow"
        Pattern pattern2 = Pattern.compile(
            "(\\w+(?:\\s+\\w+)?)\\s+(tomorrow|today|next week|next month)",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher2 = pattern2.matcher(userInput);
        if (matcher2.find()) {
            String eventType = cleanEventTitle(matcher2.group(1));
            int days = calculateDaysFromTimeRef(matcher2.group(2));
            return new CalendarEventInfo(eventType, days);
        }
        
        // Pattern: "meeting on Monday"
        Pattern pattern3 = Pattern.compile(
            "(\\w+(?:\\s+\\w+)?)\\s+on\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher3 = pattern3.matcher(userInput);
        if (matcher3.find()) {
            String eventType = cleanEventTitle(matcher3.group(1));
            int days = calculateDaysUntilDayOfWeek(matcher3.group(2));
            return new CalendarEventInfo(eventType, days);
        }
        
        return null;
    }
    
    private String cleanEventTitle(String title) {
        // Capitalize first letter of each word
        String[] words = title.trim().split("\\s+");
        StringBuilder cleaned = new StringBuilder();
        
        for (int i = 0; i < words.length; i++) {
            if (i > 0) cleaned.append(" ");
            String word = words[i];
            cleaned.append(word.substring(0, 1).toUpperCase())
                  .append(word.substring(1).toLowerCase());
        }
        
        return cleaned.toString();
    }
    
    private int calculateDays(int number, String unit) {
        unit = unit.toLowerCase();
        if (unit.contains("day")) return number;
        if (unit.contains("week")) return number * 7;
        if (unit.contains("month")) return number * 30;
        return number;
    }
    
    private int calculateDaysFromTimeRef(String timeRef) {
        switch (timeRef.toLowerCase()) {
            case "today": return 0;
            case "tomorrow": return 1;
            case "next week": return 7;
            case "next month": return 30;
            default: return 1;
        }
    }
    
    private int calculateDaysUntilDayOfWeek(String dayName) {
        // Simplified calculation - assumes next occurrence of the day
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
    
    private String formatTimeReference(int days) {
        if (days == 0) return "today";
        if (days == 1) return "tomorrow";
        if (days == 7) return "next week";
        if (days <= 7) return days + " days";
        if (days <= 30) return (days / 7) + " weeks";
        return "in the future";
    }
    
    private String performAdditionalValidation(String response, String userInput) {
        // Check for formatting issues
        if (!response.contains(")*!")) {
            logger.warning("Response missing required )*! separators");
        }
        
        // Check for consistent calendar formatting
        if (response.contains("Calendar:") && !response.contains("..!.")) {
            logger.warning("Calendar entry missing proper termination");
        }
        
        return response;
    }
    
    // Helper class for event information
    private static class CalendarEventInfo {
        private final String eventTitle;
        private final int daysFromToday;
        
        public CalendarEventInfo(String eventTitle, int daysFromToday) {
            this.eventTitle = eventTitle;
            this.daysFromToday = daysFromToday;
        }
        
        public String getEventTitle() { return eventTitle; }
        public int getDaysFromToday() { return daysFromToday; }
    }
}
