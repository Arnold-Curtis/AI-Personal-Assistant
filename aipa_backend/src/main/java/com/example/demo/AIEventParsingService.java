package com.example.demo;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Mono;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Logger;


@Service
public class AIEventParsingService {
    
    private static final Logger logger = Logger.getLogger(AIEventParsingService.class.getName());
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${gemini.api.key}")
    private String geminiApiKey;
    
    public AIEventParsingService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .baseUrl("https:
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
    
    
    public List<ParsedEvent> parseEventsFromNaturalLanguage(String userInput) {
        try {
            String prompt = buildEventParsingPrompt(userInput);
            
            String aiResponse = callAI(prompt).block();
            if (aiResponse == null) {
                logger.warning("AI returned null response for input: " + userInput);
                return new ArrayList<>();
            }
            
            return parseAIResponse(aiResponse, userInput);
            
        } catch (Exception e) {
            logger.severe("Error in AI event parsing: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    private String buildEventParsingPrompt(String userInput) {
        LocalDate today = LocalDate.now();
        String currentDate = today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"));
        
        return """
            You are an expert at parsing calendar events from natural language. Your job is to identify events that should be scheduled and extract precise information.
            
            TODAY'S DATE: %s
            
            INSTRUCTIONS:
            1. Identify ONLY genuine future event scheduling (not questions about existing events)
            2. Extract event title, calculate exact date, and determine days from today
            3. Handle complex time expressions accurately (e.g., "3 weeks 4 days" = 25 days)
            4. Generate clean, descriptive event titles
            5. If no schedulable events found, return "NO_EVENTS"
            
            EXAMPLES OF WHAT TO DETECT:
            ✓ "I have a wedding in 2 weeks" → Wedding, 14 days from today
            ✓ "meeting tomorrow at 3pm" → Meeting, 1 day from today  
            ✓ "car meet in 3 weeks 4 days" → Car Meet, 25 days from today
            ✓ "doctor appointment next Friday" → Doctor Appointment, [calculate days to next Friday]
            ✓ "birthday party in 1 month 2 days" → Birthday Party, 32 days from today
            
            EXAMPLES OF WHAT NOT TO DETECT:
            ✗ "when is my wedding?" (question about existing event)
            ✗ "what time is my meeting?" (asking for info)
            ✗ "my birthday is March 23" (stating fact, not scheduling)
            
            TIME CALCULATION RULES:
            - Handle compound expressions: "X weeks Y days" = (X*7) + Y
            - "next week" = 7 days, "next month" = 30 days
            - For weekdays: calculate days until next occurrence
            - For specific dates: calculate from today to that date
            
            RESPONSE FORMAT (JSON):
            {
              "events": [
                {
                  "title": "Clean Event Name",
                  "daysFromToday": number,
                  "confidence": 0.0-1.0,
                  "reasoning": "Why this is an event"
                }
              ]
            }
            
            If no events: {"events": []}
            
            USER INPUT: "%s"
            
            Parse this carefully and respond with JSON only:
            """.formatted(currentDate, userInput);
    }
    
    private Mono<String> callAI(String prompt) {
        Map<String, Object> request = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(
                    Map.of("text", prompt)
                ))
            ),
            "generationConfig", Map.of(
                "temperature", 0.2, 
                "topP", 0.8,
                "topK", 40,
                "maxOutputTokens", 1024
            )
        );
        
        return webClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("gemini-2.0-flash:generateContent")
                .queryParam("key", geminiApiKey)
                .build())
            .bodyValue(request)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(this::extractAIResponse)
            .onErrorReturn("Error in AI call");
    }
    
    private String extractAIResponse(JsonNode responseNode) {
        try {
            return responseNode
                .path("candidates").get(0)
                .path("content")
                .path("parts").get(0)
                .path("text")
                .asText()
                .trim();
        } catch (Exception e) {
            logger.warning("Error extracting AI response: " + e.getMessage());
            return "Error extracting response";
        }
    }
    
    private List<ParsedEvent> parseAIResponse(String aiResponse, String originalInput) {
        List<ParsedEvent> events = new ArrayList<>();
        
        try {
            
            String cleanResponse = aiResponse
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();
            
            JsonNode jsonResponse = objectMapper.readTree(cleanResponse);
            
            if (jsonResponse.has("events") && jsonResponse.get("events").isArray()) {
                for (JsonNode eventNode : jsonResponse.get("events")) {
                    try {
                        String title = eventNode.path("title").asText();
                        int daysFromToday = eventNode.path("daysFromToday").asInt();
                        double confidence = eventNode.path("confidence").asDouble(0.8);
                        String reasoning = eventNode.path("reasoning").asText("");
                        
                        
                        if (isValidEvent(title, daysFromToday, confidence)) {
                            LocalDate eventDate = LocalDate.now().plusDays(daysFromToday);
                            events.add(new ParsedEvent(title, eventDate, confidence, reasoning));
                        } else {
                            logger.warning("Invalid event filtered out: " + title + ", days: " + daysFromToday + ", confidence: " + confidence);
                        }
                    } catch (Exception e) {
                        logger.warning("Error parsing individual event: " + e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            logger.warning("Error parsing AI JSON response: " + e.getMessage() + ", Response: " + aiResponse);
        }
        
        return events;
    }
    
    private boolean isValidEvent(String title, int daysFromToday, double confidence) {
        return title != null && 
               !title.trim().isEmpty() && 
               !title.equalsIgnoreCase("NO_EVENTS") &&
               daysFromToday >= 0 && 
               daysFromToday <= 365 && 
               confidence >= 0.3; 
    }
    
    
    public boolean containsEventSchedulingIntent(String userInput) {
        try {
            String prompt = """
                Analyze if this input contains intent to SCHEDULE a future event (not ask about existing events).
                
                SCHEDULING INTENT (return true):
                - "I have a wedding in 2 weeks"
                - "meeting tomorrow"
                - "book appointment for Friday"
                - "schedule car meet next month"
                
                NOT SCHEDULING INTENT (return false):
                - "when is my wedding?" (asking about existing)
                - "what time is my meeting?" (requesting info)
                - "my birthday is March 23" (stating fact)
                - "remind me about my appointment" (reminder request)
                
                Respond with only: true or false
                
                INPUT: "%s"
                """.formatted(userInput);
            
            String response = callAI(prompt).block();
            return response != null && response.toLowerCase().contains("true");
            
        } catch (Exception e) {
            logger.warning("Error checking event scheduling intent: " + e.getMessage());
            return false;
        }
    }
    
    
    public int calculateDaysFromComplexExpression(String timeExpression) {
        try {
            LocalDate today = LocalDate.now();
            String currentDate = today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"));
            
            String prompt = """
                Calculate the exact number of days from today to the specified time.
                
                TODAY: %s
                
                TIME CALCULATION RULES:
                - "tomorrow" = 1 day
                - "next week" = 7 days
                - "next month" = 30 days
                - "3 weeks 4 days" = (3×7) + 4 = 25 days
                - "2 months 1 week" = (2×30) + 7 = 67 days
                - For weekdays: calculate to next occurrence
                - For dates: calculate from today
                
                EXAMPLES:
                - "in 2 weeks" → 14
                - "3 weeks 4 days" → 25
                - "next Friday" → [calculate to next Friday]
                - "2 months" → 60
                
                Respond with only the number of days (integer).
                
                TIME EXPRESSION: "%s"
                """.formatted(currentDate, timeExpression);
            
            String response = callAI(prompt).block();
            if (response != null) {
                try {
                    return Integer.parseInt(response.trim());
                } catch (NumberFormatException e) {
                    logger.warning("AI returned non-numeric response for date calculation: " + response);
                }
            }
            
        } catch (Exception e) {
            logger.warning("Error in AI date calculation: " + e.getMessage());
        }
        
        return -1; 
    }
    
    
    public static class ParsedEvent {
        private final String title;
        private final LocalDate date;
        private final double confidence;
        private final String reasoning;
        
        public ParsedEvent(String title, LocalDate date, double confidence, String reasoning) {
            this.title = title;
            this.date = date;
            this.confidence = confidence;
            this.reasoning = reasoning;
        }
        
        public String getTitle() { return title; }
        public LocalDate getDate() { return date; }
        public double getConfidence() { return confidence; }
        public String getReasoning() { return reasoning; }
        
        public int getDaysFromToday() {
            return (int) ChronoUnit.DAYS.between(LocalDate.now(), date);
        }
        
        @Override
        public String toString() {
            return String.format("ParsedEvent{title='%s', date=%s, confidence=%.2f, reasoning='%s'}", 
                               title, date, confidence, reasoning);
        }
    }
}

