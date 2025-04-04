package com.example.demo;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    // In-memory storage (replace with database repository in production)
    private static final Map<String, List<Map<String, String>>> userEvents = new HashMap<>();

    @GetMapping("/events")
    public ResponseEntity<?> getEvents(Authentication authentication) {
        try {
            String userId = getUserId(authentication);
            List<Map<String, String>> events = userEvents.getOrDefault(userId, new ArrayList<>());
            
            // Sort events by date
            List<Map<String, String>> sortedEvents = new ArrayList<>(events);
            sortedEvents.sort(Comparator.comparing(e -> LocalDate.parse(e.get("start"))));
            
            return ResponseEntity.ok(sortedEvents);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Failed to retrieve events",
                    "details", e.getMessage()
                ));
        }
    }

    @PostMapping("/add-event")
    public ResponseEntity<?> addEvent(
            @RequestBody Map<String, String> eventRequest,
            Authentication authentication) {
        try {
            String userId = getUserId(authentication);
            
            // Validate required fields
            if (eventRequest == null || !eventRequest.containsKey("title") || !eventRequest.containsKey("start")) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Both 'title' and 'start' fields are required"));
            }

            // Validate date format
            LocalDate eventDate;
            try {
                eventDate = LocalDate.parse(eventRequest.get("start"));
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest()
                    .body(Map.of(
                        "error", "Invalid date format",
                        "expectedFormat", "YYYY-MM-DD",
                        "received", eventRequest.get("start")
                    ));
            }

            // Create new event
            Map<String, String> newEvent = createEvent(
                eventRequest.get("title").trim(),
                eventDate
            );

            // Initialize user's event list if not exists
            userEvents.putIfAbsent(userId, new CopyOnWriteArrayList<>());
            
            // Check for duplicates
            boolean isDuplicate = userEvents.get(userId).stream()
                .anyMatch(e -> e.get("start").equals(newEvent.get("start")) && 
                              e.get("title").equalsIgnoreCase(newEvent.get("title")));
            
            if (!isDuplicate) {
                userEvents.get(userId).add(newEvent);
                return ResponseEntity.status(HttpStatus.CREATED)
                    .body(newEvent);
            } else {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                        "error", "Event already exists",
                        "existingEvent", userEvents.get(userId).stream()
                            .filter(e -> e.get("start").equals(newEvent.get("start")) && 
                                        e.get("title").equalsIgnoreCase(newEvent.get("title")))
                            .findFirst()
                            .orElse(null)
                    ));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Failed to add event",
                    "details", e.getMessage()
                ));
        }
    }

    @DeleteMapping("/remove-event/{id}")
    public ResponseEntity<?> removeEvent(
            @PathVariable String id,
            Authentication authentication) {
        try {
            String userId = getUserId(authentication);
            
            if (!userEvents.containsKey(userId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User has no events"));
            }

            boolean removed = userEvents.get(userId).removeIf(e -> id.equals(e.get("id")));
            
            return removed ? 
                ResponseEntity.ok(Map.of(
                    "message", "Event removed successfully",
                    "removedId", id
                )) :
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Event not found"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Failed to remove event",
                    "details", e.getMessage()
                ));
        }
    }

    @DeleteMapping("/clear-events")
    public ResponseEntity<?> clearEvents(Authentication authentication) {
        try {
            String userId = getUserId(authentication);
            
            if (!userEvents.containsKey(userId)) {
                return ResponseEntity.ok(Map.of(
                    "message", "No events to clear",
                    "count", 0
                ));
            }

            int count = userEvents.get(userId).size();
            userEvents.get(userId).clear();
            
            return ResponseEntity.ok(Map.of(
                "message", "All events cleared",
                "count", count
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Failed to clear events",
                    "details", e.getMessage()
                ));
        }
    }

    private Map<String, String> createEvent(String title, LocalDate date) {
        Map<String, String> event = new HashMap<>();
        event.put("id", UUID.randomUUID().toString());
        event.put("title", title);
        event.put("start", date.format(DateTimeFormatter.ISO_DATE));
        event.put("allDay", "true");
        return event;
    }

    private String getUserId(Authentication authentication) {
        // In a real application, get user ID from authentication principal
        // This is a placeholder implementation
        return authentication != null ? authentication.getName() : "default-user";
    }
}