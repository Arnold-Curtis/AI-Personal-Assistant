package com.example.demo;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    // Thread-safe event storage with sample data
    private static final List<Map<String, String>> events = new CopyOnWriteArrayList<>(Arrays.asList(
        createEvent("Team Meeting", LocalDate.now().plusDays(1)),
        createEvent("Project Deadline", LocalDate.now().plusDays(3))
    ));

    private static Map<String, String> createEvent(String title, LocalDate date) {
        Map<String, String> event = new HashMap<>();
        event.put("id", UUID.randomUUID().toString());
        event.put("title", title);
        event.put("start", date.format(DateTimeFormatter.ISO_DATE));
        return event;
    }

    @GetMapping("/events")
    public ResponseEntity<?> getEvents() {
        try {
            // Sort events by date before returning
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
    public ResponseEntity<?> addEvent(@RequestBody Map<String, String> eventRequest) {
        try {
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

            // Create and store new event
            Map<String, String> newEvent = createEvent(
                eventRequest.get("title").trim(),
                eventDate
            );

            // Check for duplicates
            boolean isDuplicate = events.stream()
                .anyMatch(e -> e.get("start").equals(newEvent.get("start")) && 
                              e.get("title").equalsIgnoreCase(newEvent.get("title")));
            
            if (!isDuplicate) {
                events.add(newEvent);
                return ResponseEntity.status(HttpStatus.CREATED)
                    .body(newEvent);
            } else {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                        "error", "Event already exists",
                        "existingEvent", events.stream()
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
    public ResponseEntity<?> removeEvent(@PathVariable String id) {
        try {
            boolean removed = events.removeIf(e -> id.equals(e.get("id")));
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
    public ResponseEntity<?> clearEvents() {
        try {
            int count = events.size();
            events.clear();
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
}