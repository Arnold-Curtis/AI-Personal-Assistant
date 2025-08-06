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
    
    
    private static final Pattern CALENDAR_NONE_PATTERN = Pattern.compile(
        "Calendar:\\s*None\\.!\\.\\.!", Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern EVENT_MENTION_PATTERN = Pattern.compile(
        "\\b(wedding|meeting|appointment|birthday|party|conference|exam|interview|vacation|trip|date|dinner|lunch|call)\\b.*?\\b(tomorrow|today|next week|next month|in \\d+ (days?|weeks?|months?)|on (monday|tuesday|wednesday|thursday|friday|saturday|sunday))\\b",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    
    public String validateAndFixCalendarResponse(String originalResponse, String userInput) {
        logger.info("Validating calendar response for input: " + userInput);
        
        
        String dedupedResponse = removeDuplicateCalendarEntries(originalResponse);
        
        
        if (CALENDAR_NONE_PATTERN.matcher(dedupedResponse).find()) {
            
            
            Matcher eventMatcher = EVENT_MENTION_PATTERN.matcher(userInput);
            if (eventMatcher.find()) {
                logger.warning("Detected calendar parsing error - events mentioned but Calendar: None in response");
                
                
                String fixedResponse = attemptCalendarFix(dedupedResponse, userInput);
                if (!fixedResponse.equals(dedupedResponse)) {
                    logger.info("Successfully fixed calendar response");
                    return fixedResponse;
                }
            }
        }
        
        
        String validatedResponse = performAdditionalValidation(dedupedResponse, userInput);
        
        return validatedResponse;
    }
    
    
    private String removeDuplicateCalendarEntries(String response) {
        try {
            
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
                
                
                String signature = eventTitle.toLowerCase() + "_" + days;
                
                
                if (!seenEvents.contains(signature)) {
                    seenEvents.add(signature);
                    
                    result.append(response.substring(lastEnd, matcher.start()));
                    
                    result.append("Calendar: ").append(days).append(" days from today ").append(eventTitle).append(".!.");
                    lastEnd = matcher.end();
                } else {
                    
                    result.append(response.substring(lastEnd, matcher.start()));
                    lastEnd = matcher.end();
                    logger.info("Removed duplicate calendar entry: " + eventTitle);
                }
            }
            
            
            result.append(response.substring(lastEnd));
            
            return result.toString();
            
        } catch (Exception e) {
            logger.warning("Error removing duplicates: " + e.getMessage());
            return response;
        }
    }
    
    private String attemptCalendarFix(String response, String userInput) {
        try {
            
            CalendarEventInfo eventInfo = extractEventFromInput(userInput);
            
            if (eventInfo != null) {
                
                String fixedCalendarEntry = String.format(
                    "Calendar: %d days from today %s.!..!.",
                    eventInfo.getDaysFromToday(),
                    eventInfo.getEventTitle()
                );
                
                String fixedResponse = response.replaceFirst(
                    "Calendar:\\s*None\\.!\\.\\.!",
                    fixedCalendarEntry
                );
                
                
                String acknowledgment = String.format(
                    "I've noted your %s and added it to your calendar for %s so you don't forget!",
                    eventInfo.getEventTitle().toLowerCase(),
                    formatTimeReference(eventInfo.getDaysFromToday())
                );
                
                
                fixedResponse = fixedResponse.replaceFirst(
                    "I understand your request and will help you with that\\.",
                    acknowledgment
                );
                
                return fixedResponse;
            }
            
        } catch (Exception e) {
            logger.warning("Error attempting calendar fix: " + e.getMessage());
        }
        
        return response; 
    }
    
    private CalendarEventInfo extractEventFromInput(String userInput) {
        
        
        
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
        
        if (!response.contains(")*!")) {
            logger.warning("Response missing required )*! separators");
        }
        
        
        if (response.contains("Calendar:") && !response.contains("..!.")) {
            logger.warning("Calendar entry missing proper termination");
        }
        
        return response;
    }
    
    
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

