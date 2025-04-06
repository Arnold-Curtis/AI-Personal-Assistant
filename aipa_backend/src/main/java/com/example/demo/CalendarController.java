package com.example.demo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private static final Logger logger = Logger.getLogger(CalendarController.class.getName());

    @PersistenceContext
    private EntityManager entityManager;

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public CalendarController(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @PostMapping("/add-event")
    @Transactional
    public ResponseEntity<?> addEvent(@RequestBody CalendarEvent event, 
                                    HttpServletRequest request) {
        try {
            String token = extractToken(request);
            String email = jwtUtil.extractUsername(token);
            User user = userRepository.findByEmail(email);
            
            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }

            // If event has an ID, it's an update
            if (event.getId() != null) {
                return updateEvent(event, user);
            }

            // Check for duplicate events
            List<CalendarEvent> existingEvents = entityManager
                .createQuery("SELECT e FROM CalendarEvent e WHERE e.title = :title AND e.start = :start AND e.user = :user", CalendarEvent.class)
                .setParameter("title", event.getTitle())
                .setParameter("start", event.getStart())
                .setParameter("user", user)
                .getResultList();
            
            if (!existingEvents.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Event already exists"));
            }

            event.setUser(user);
            entityManager.persist(event);
            
            // Return a converted DTO to avoid circular references
            Map<String, Object> eventDto = convertToDto(event);
            return ResponseEntity.ok(eventDto);
            
        } catch (Exception e) {
            logger.severe("Error adding event: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized: " + e.getMessage()));
        }
    }

    @Transactional
    private ResponseEntity<?> updateEvent(CalendarEvent updatedEvent, User user) {
        try {
            CalendarEvent existingEvent = entityManager.find(CalendarEvent.class, updatedEvent.getId());
            
            if (existingEvent == null || !existingEvent.getUser().equals(user)) {
                return ResponseEntity.status(404).body(Map.of("error", "Event not found"));
            }
            
            // Update fields
            existingEvent.setTitle(updatedEvent.getTitle());
            if (updatedEvent.getDescription() != null) {
                existingEvent.setDescription(updatedEvent.getDescription());
            }
            if (updatedEvent.getStart() != null) {
                existingEvent.setStart(updatedEvent.getStart());
            }
            if (updatedEvent.getAllDay() != null) {
                existingEvent.setAllDay(updatedEvent.getAllDay());
            }
            if (updatedEvent.getEventColor() != null) {
                existingEvent.setEventColor(updatedEvent.getEventColor());
            }
            
            entityManager.merge(existingEvent);
            
            logger.info("Event updated successfully: " + existingEvent.getId());
            return ResponseEntity.ok(convertToDto(existingEvent));
            
        } catch (Exception e) {
            logger.severe("Error updating event: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Failed to update event: " + e.getMessage()));
        }
    }

    @GetMapping("/events")
    public ResponseEntity<?> getEvents(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            String email = jwtUtil.extractUsername(token);
            User user = userRepository.findByEmail(email);
            
            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }

            List<CalendarEvent> events = entityManager
                .createQuery("SELECT e FROM CalendarEvent e WHERE e.user = :user", CalendarEvent.class)
                .setParameter("user", user)
                .getResultList();
            
            // Convert to DTOs to prevent serialization issues
            List<Map<String, Object>> eventDtos = new ArrayList<>();
            for (CalendarEvent event : events) {
                eventDtos.add(convertToDto(event));
            }
            
            return ResponseEntity.ok(eventDtos);
            
        } catch (Exception e) {
            logger.severe("Error retrieving events: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized: " + e.getMessage()));
        }
    }

    @DeleteMapping("/events/{id}")
    @Transactional
    public ResponseEntity<?> deleteEvent(@PathVariable Long id,
                                       HttpServletRequest request) {
        try {
            String token = extractToken(request);
            String email = jwtUtil.extractUsername(token);
            User user = userRepository.findByEmail(email);
            
            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }

            CalendarEvent event = entityManager.find(CalendarEvent.class, id);
            if (event == null || !event.getUser().equals(user)) {
                return ResponseEntity.status(404).body(Map.of("error", "Event not found"));
            }
            
            entityManager.remove(event);
            return ResponseEntity.ok(Map.of("message", "Event deleted successfully"));
            
        } catch (Exception e) {
            logger.severe("Error deleting event: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized: " + e.getMessage()));
        }
    }

    @GetMapping("/events/upcoming")
    public ResponseEntity<?> getUpcomingEvents(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            String email = jwtUtil.extractUsername(token);
            User user = userRepository.findByEmail(email);
            
            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }

            LocalDate today = LocalDate.now();
            List<CalendarEvent> upcomingEvents = entityManager
                .createQuery("SELECT e FROM CalendarEvent e WHERE e.user = :user AND e.start >= :today ORDER BY e.start", CalendarEvent.class)
                .setParameter("user", user)
                .setParameter("today", today)
                .getResultList();
            
            // Convert to DTOs to prevent serialization issues
            List<Map<String, Object>> eventDtos = new ArrayList<>();
            for (CalendarEvent event : upcomingEvents) {
                eventDtos.add(convertToDto(event));
            }
                
            return ResponseEntity.ok(eventDtos);
            
        } catch (Exception e) {
            logger.severe("Error retrieving upcoming events: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized: " + e.getMessage()));
        }
    }

    /**
     * Converts a CalendarEvent entity to a Map DTO to avoid circular reference issues
     */
    private Map<String, Object> convertToDto(CalendarEvent event) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", event.getId());
        dto.put("title", event.getTitle());
        dto.put("start", event.getStart().toString());
        dto.put("description", event.getDescription());
        dto.put("isAllDay", event.getAllDay());
        dto.put("eventColor", event.getEventColor());
        return dto;
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Invalid authorization header");
        }
        return authHeader.substring(7);
    }
}