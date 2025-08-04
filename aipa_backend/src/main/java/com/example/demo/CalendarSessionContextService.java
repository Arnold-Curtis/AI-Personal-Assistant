package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class CalendarSessionContextService {
    
    private final CalendarController calendarController;
    
    @Autowired
    public CalendarSessionContextService(CalendarController calendarController) {
        this.calendarController = calendarController;
    }
    
    /**
     * Generate context about calendar events for session memory
     * This provides lightweight information about user's upcoming events
     */
    @Transactional(readOnly = true)
    public String generateCalendarEventsContext(UUID userId, String sessionId) {
        try {
            StringBuilder context = new StringBuilder();
            context.append("\n=== SESSION CALENDAR EVENTS CONTEXT ===\n");
            context.append("Session ID: ").append(sessionId).append("\n");
            context.append("Generated at: ").append(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)).append("\n\n");
            
            // Get all user's calendar events
            List<CalendarEvent> events = getAllUserEvents(userId);
            
            if (events.isEmpty()) {
                context.append("CALENDAR STATUS: No upcoming events found.\n");
            } else {
                context.append("CALENDAR STATUS: ").append(events.size()).append(" events found.\n");
                context.append("EVENTS OVERVIEW:\n");
                
                LocalDate today = LocalDate.now();
                int eventCount = 0;
                
                for (CalendarEvent event : events) {
                    if (eventCount >= 10) { // Limit to 10 events to keep context light
                        context.append("... and ").append(events.size() - eventCount).append(" more events\n");
                        break;
                    }
                    
                    LocalDate eventDate = event.getStart();
                    long daysFromToday = java.time.temporal.ChronoUnit.DAYS.between(today, eventDate);
                    
                    context.append("- ").append(event.getTitle());
                    
                    if (daysFromToday == 0) {
                        context.append(" (TODAY)");
                    } else if (daysFromToday == 1) {
                        context.append(" (TOMORROW)");
                    } else if (daysFromToday > 0) {
                        context.append(" (in ").append(daysFromToday).append(" days)");
                    } else {
                        context.append(" (").append(Math.abs(daysFromToday)).append(" days ago)");
                    }
                    
                    if (event.getDescription() != null && !event.getDescription().trim().isEmpty()) {
                        String shortDesc = event.getDescription().length() > 50 ? 
                            event.getDescription().substring(0, 47) + "..." : 
                            event.getDescription();
                        context.append(" - ").append(shortDesc);
                    }
                    
                    context.append("\n");
                    eventCount++;
                }
            }
            
            context.append("\nCONTEXT INSTRUCTIONS FOR AI:\n");
            context.append("- Be aware of these events when user mentions dates or scheduling\n");
            context.append("- Detect potential conflicts when user suggests new events\n");
            context.append("- Reference existing events naturally when relevant\n");
            context.append("- Help user remember events they might have forgotten\n");
            context.append("- Suggest reminders for important upcoming events\n");
            context.append("================================================\n");
            
            return context.toString();
            
        } catch (Exception e) {
            System.err.println("Error generating calendar events context: " + e.getMessage());
            e.printStackTrace();
            return "\n=== SESSION CALENDAR EVENTS CONTEXT ===\nERROR: Could not retrieve calendar events.\n================================================\n";
        }
    }
    
    /**
     * Get all calendar events for a user
     * This is a simplified version that directly queries the database
     */
    private List<CalendarEvent> getAllUserEvents(UUID userId) {
        try {
            // Use a direct database query to avoid HTTP request complexity
            // This method should be implemented to get events directly from the database
            return calendarController.getUserCalendarEventsDirect(userId);
        } catch (Exception e) {
            System.err.println("Error fetching user calendar events: " + e.getMessage());
            return List.of(); // Return empty list on error
        }
    }
    
    /**
     * Generate a concise summary for debugging
     */
    public String generateEventsSummary(UUID userId) {
        try {
            List<CalendarEvent> events = getAllUserEvents(userId);
            return String.format("User %s has %d calendar events", 
                userId.toString().substring(0, 8), events.size());
        } catch (Exception e) {
            return "Could not generate events summary: " + e.getMessage();
        }
    }
}
