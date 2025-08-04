package com.example.demo;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.List;
import java.util.regex.*;
import java.util.ArrayList;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.UUID;
import com.example.demo.User;
import com.example.demo.UserRepository;

@RestController
@RequestMapping("/api")
public class LLMController {
    
    private static String latestResponse = "";
    private static final String TEMP_FILE = "tempres.txt";
    private static final String ANALYSIS_RESULT_FILE = "fpromptres.txt";
    private static final String PROMPT_TEMPLATE_PATH = "promptmst.txt";
    
    private static final String ANALYSIS_CHECK_PROMPT = 
    "Analyze the given input and determine whether it is actionable or non-actionable based on the following enhanced criteria:\n\n" +
    
    "ACTIONABLE CRITERIA (Must meet at least one):\n" +
    "1. COMPLEX GOALS: Learning skills, building habits, long-term achievements (e.g., 'learn programming', 'get fit', 'start business')\n" +
    "2. CALENDAR EVENTS: Scheduled events with specific dates (e.g., 'meeting tomorrow', 'wedding in 2 weeks')\n" +
    "3. MULTI-STEP TASKS: Tasks requiring planning over multiple days/weeks\n" +
    "4. SKILL DEVELOPMENT: Goals involving sustained practice or study\n\n" +
    
    "NON-ACTIONABLE CRITERIA (Automatic NO):\n" +
    "1. SIMPLE QUESTIONS: Math problems, definitions, factual queries (e.g., 'What is 1+1?', 'What's the weather?')\n" +
    "2. INFORMATION REQUESTS: Time, date, simple lookups (e.g., 'What time is it?', 'Who is the president?')\n" +
    "3. BASIC GREETINGS: Simple conversational exchanges without goals\n" +
    "4. IMMEDIATE TASKS: Single-session activities (e.g., 'Write an email', 'Cook pasta')\n" +
    "5. TROUBLESHOOTING: Simple fix requests (e.g., 'Reset password', 'Install app')\n\n" +
    
    "ANALYSIS FACTORS:\n" +
    "- Complexity: Does it require multiple steps over time?\n" +
    "- Duration: Will it take more than one day/session?\n" +
    "- Skill Building: Does it involve learning or developing abilities?\n" +
    "- Calendar Events: Is it a scheduled event with a date?\n" +
    "- Goal Orientation: Is there a clear objective requiring sustained effort?\n\n" +
    
    "Response Format:\n" +
    "Thinking Space: Analyze the input against the above criteria, explaining your reasoning.\n" +
    "Final Decision: Return either ).* YES (actionable) or ).* NO (non-actionable)\n\n" +
    
    "Example 1 (Non-Actionable - Simple Question):\n" +
    "Input: \"What is 1 plus 1?\"\n" +
    "Output:\nThinking Space:\n" +
    "This is a simple math question requesting immediate factual information.\n" +
    "No complex goal, no multi-step process, no skill development needed.\n" +
    "Falls under 'Simple Questions' non-actionable criteria.\n" +
    "Can be answered directly without planning or sustained effort.\n" +
    ").* NO\n\n" +
    
    "Example 2 (Actionable - Complex Goal):\n" +
    "Input: \"I want to learn Java programming.\"\n" +
    "Output:\nThinking Space:\n" +
    "Learning programming is a complex goal requiring sustained effort over weeks/months.\n" +
    "Involves skill development, multiple sequential learning steps.\n" +
    "Cannot be completed in a single session - needs structured approach.\n" +
    "Meets 'Complex Goals' and 'Skill Development' actionable criteria.\n" +
    ").* YES\n\n" +
    
    "Example 3 (Actionable - Calendar Event):\n" +
    "Input: \"I have a wedding in 2 weeks.\"\n" +
    "Output:\nThinking Space:\n" +
    "This is a scheduled event with a specific timeframe (2 weeks).\n" +
    "Meets 'Calendar Events' actionable criteria.\n" +
    "Event has a fixed date and should be tracked.\n" +
    ").* YES\n\n" +
    
    "Example 4 (Non-Actionable - Simple Task):\n" +
    "Input: \"How do I cook pasta?\"\n" +
    "Output:\nThinking Space:\n" +
    "This is a request for instructions on a simple, immediate task.\n" +
    "Can be completed in a single session with basic steps.\n" +
    "No sustained effort or skill development required.\n" +
    "Falls under 'Immediate Tasks' non-actionable criteria.\n" +
    ").* NO";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient;
    private final MemoryService memoryService;
    private final UserRepository userRepository;
    private final PlanAnalysisService planAnalysisService;
    private final CalendarEventEnhancementService calendarEventEnhancementService;
    private final CalendarResponseValidationService calendarValidationService;
    private final SessionMemoryService sessionMemoryService;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public LLMController(WebClient.Builder webClientBuilder, MemoryService memoryService, UserRepository userRepository, PlanAnalysisService planAnalysisService, CalendarEventEnhancementService calendarEventEnhancementService, CalendarResponseValidationService calendarValidationService, SessionMemoryService sessionMemoryService) {
        this.webClient = webClientBuilder
            .baseUrl("https://generativelanguage.googleapis.com/v1beta/models/")
            .defaultHeader("Content-Type", "application/json")
            .build();
        this.memoryService = memoryService;
        this.userRepository = userRepository;
        this.planAnalysisService = planAnalysisService;
        this.calendarEventEnhancementService = calendarEventEnhancementService;
        this.calendarValidationService = calendarValidationService;
        this.sessionMemoryService = sessionMemoryService;
    }

    private void storeResponse(String response) {
        latestResponse = response;
        try {
            String content = "Generated at: " + Instant.now() + "\n" + response;
            Files.writeString(
                Paths.get(TEMP_FILE),
                content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            System.err.println("File storage failed: " + e.getMessage());
        }
    }

    private void storeAnalysisResult(String rawResponse, String finalDecision, String userInput) {
        try {
            String content = "=== Analysis Result ===\n" +
                           "Timestamp: " + Instant.now() + "\n" +
                           "User Input: " + userInput + "\n" +
                           "Final Decision: " + finalDecision + "\n" +
                           "Response Analysis:\n" + rawResponse + "\n" +
                           "========================\n\n";
            
            Files.writeString(
                Paths.get(ANALYSIS_RESULT_FILE),
                content,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            System.err.println("Failed to store analysis result: " + e.getMessage());
        }
    }

    public static class ChatMessage {
        private String text;
        private boolean isUser;
        
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public boolean getIsUser() { return isUser; }
        public void setIsUser(boolean isUser) { this.isUser = isUser; }
        
        public ChatMessage() {}
        public ChatMessage(String text, boolean isUser) {
            this.text = text;
            this.isUser = isUser;
        }
    }

    @PostMapping("/generate")
    public Flux<String> generateText(@RequestBody Map<String, Object> request, Authentication authentication) {
        String userInput = (String) request.get("prompt");
        final List<ChatMessage> chatHistory = new ArrayList<>();
        
        // Get user from authentication
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getUsername());
        if (user == null) {
            return Flux.just("{\"error\": \"User not found\"}");
        }
        UUID userId = user.getId();
        
        // Parse chat history if provided
        if (request.containsKey("history")) {
            try {
                List<ChatMessage> history = objectMapper.convertValue(
                    request.get("history"), 
                    new TypeReference<List<ChatMessage>>() {}
                );
                chatHistory.addAll(history);
            } catch (Exception e) {
                System.err.println("Failed to parse chat history: " + e.getMessage());
            }
        }
        
        if (userInput == null || userInput.trim().isEmpty()) {
            return Flux.just("{\"error\": \"Prompt is required\"}");
        }

        try {
            // NEW: Session memory tracking with calendar context
            String sessionId = (String) request.getOrDefault("sessionId", 
                "session_" + System.currentTimeMillis() + "_" + userId.toString().substring(0, 8));
            
            SessionMemoryService.SessionContextResult sessionContext = 
                sessionMemoryService.trackChatAndGetContext(userId, sessionId, userInput);
            
            System.out.println("📊 Session Context: " + sessionContext.toString());
            
            // Enhanced memory analysis and storage
            MemoryAnalysisService.MemoryAnalysisResult memoryAnalysis = 
                memoryService.analyzeAndStoreMemory(userId, userInput);
            
            // NEW: Enhanced calendar event detection and analysis
            CalendarEventEnhancementService.CalendarEventAnalysis calendarAnalysis = 
                calendarEventEnhancementService.analyzeForCalendarEvents(userInput);
            
            // NEW: Enhanced plan analysis to determine if this truly needs a plan
            PlanAnalysisService.PlanAnalysisResult planAnalysis = 
                planAnalysisService.analyzeForPlan(userInput);
            
            // Get relevant memories for context
            List<String> relevantMemories = memoryService.getRelevantMemories(userId, userInput);
            
            // Build enhanced context with session memory, memories, calendar events, and plan analysis
            StringBuilder contextWithMemories = new StringBuilder();
            
            // Add session calendar context if this is start of session or every 10th chat
            if (sessionContext.shouldSendContext() && !sessionContext.getCalendarContext().isEmpty()) {
                contextWithMemories.append(sessionContext.getCalendarContext()).append("\n");
            }
            
            if (!relevantMemories.isEmpty()) {
                contextWithMemories.append("\n=== RELEVANT USER MEMORIES ===\n");
                for (String memory : relevantMemories) {
                    contextWithMemories.append("• ").append(memory).append("\n");
                }
                contextWithMemories.append("===============================\n");
            }
            
            // Add calendar event analysis context
            if (calendarAnalysis.hasEvents()) {
                contextWithMemories.append("\n=== CALENDAR EVENTS DETECTED ===\n");
                contextWithMemories.append("CRITICAL: The following calendar events were detected and MUST be included in the response:\n");
                for (CalendarEventEnhancementService.DetectedEvent event : calendarAnalysis.getEvents()) {
                    contextWithMemories.append("- ").append(event.getTitle())
                                     .append(" in ").append(event.getDaysFromToday())
                                     .append(" days from today\n");
                }
                contextWithMemories.append("MANDATORY FORMAT: Use EXACTLY this format in Part 3:\n");
                for (CalendarEventEnhancementService.DetectedEvent event : calendarAnalysis.getEvents()) {
                    contextWithMemories.append("Calendar: ").append(event.getDaysFromToday())
                                     .append(" days from today ").append(event.getTitle()).append(".!.\n");
                }
                contextWithMemories.append("DO NOT use 'Calendar: None.!..!.' when events are detected!\n");
                contextWithMemories.append("================================\n");
            }
            
            // Add plan analysis context to help guide response
            contextWithMemories.append("\n=== PLAN ANALYSIS GUIDANCE ===\n");
            contextWithMemories.append("Plan Analysis Result: ").append(planAnalysis.toString()).append("\n");
            if (planAnalysis.shouldCreatePlan()) {
                contextWithMemories.append("GUIDANCE: This input represents a genuine complex goal that warrants a multi-step plan.\n");
                contextWithMemories.append("Plan Type: ").append(planAnalysis.getPlanType()).append("\n");
                contextWithMemories.append("Expected Duration: ").append(planAnalysis.getEstimatedDuration()).append("\n");
            } else {
                contextWithMemories.append("GUIDANCE: This input does NOT warrant a plan. Respond appropriately without creating a plan.\n");
                contextWithMemories.append("Reasoning: ").append(planAnalysis.getReasoning()).append("\n");
            }
            contextWithMemories.append("================================\n");
            
            // Log all analyses for debugging
            System.out.println("Memory analysis result: " + memoryAnalysis.getConfidence() + 
                             ", Type: " + memoryAnalysis.getMemoryType() + 
                             ", Should store: " + memoryAnalysis.shouldStore());
            System.out.println("Calendar analysis result: " + calendarAnalysis.toString());
            System.out.println("Plan analysis result: " + planAnalysis.toString());
                        
            // Continue with actionable/non-actionable check
            return webClient.post()
                .uri(uriBuilder -> uriBuilder
                    .path("gemini-2.0-flash:generateContent")
                    .queryParam("key", geminiApiKey)
                    .build())
                .bodyValue(createGeminiRequest(ANALYSIS_CHECK_PROMPT + userInput + contextWithMemories.toString()))
                .retrieve()
                .onStatus(status -> status.isError(), response -> 
                    response.bodyToMono(String.class)
                        .flatMap(errorBody -> Mono.error(new RuntimeException(
                            "API Error: " + response.statusCode() + " - " + errorBody
                        )))
                )
                .bodyToMono(JsonNode.class)
                .flatMapMany(analysisResponse -> {
                    try {
                        String fullResponse = extractGeminiResponse(analysisResponse);
                        System.out.println("Full Analysis Response:\n" + fullResponse);
                        
                        String finalDecision = parseFinalDecision(fullResponse);
                        System.out.println("Parsed Decision: " + finalDecision);
                        
                        storeAnalysisResult(fullResponse, finalDecision, userInput);

                        // Build final prompt with chat history context
                        StringBuilder promptWithHistory = new StringBuilder();
                        
                        // Use enhanced template for actionable prompts, with special calendar template for calendar events
                        if (finalDecision.equals("yes")) {
                            String promptTemplatePath = calendarAnalysis.hasEvents() ? 
                                "enhanced_calendar_promptmst.txt" : PROMPT_TEMPLATE_PATH;
                            String promptTemplate = Files.readString(Paths.get(promptTemplatePath));
                            
                            // Replace date placeholders with actual date
                            String formattedDate = getFormattedDate();
                            promptTemplate = promptTemplate.replace("[DAY_OF_WEEK] the [DAY] of [MONTH] [YEAR]", formattedDate);
                            
                            promptWithHistory.append(promptTemplate);
                            promptWithHistory.append("User Input & Today is: " + formattedDate + "\n" + userInput);
                            promptWithHistory.append("\n\n**Conversation History:**\n");
                        } else {
                            promptWithHistory.append("You are an AI assistant helping with a conversation.\n**Conversation History:**\n");
                        }
                        
                        // Add memory context to prompt (ENHANCED)
                        if (!relevantMemories.isEmpty()) {
                            promptWithHistory.append("\n**IMPORTANT USER MEMORIES (Consider these in your response):**\n");
                            for (String memory : relevantMemories) {
                                promptWithHistory.append("- ").append(memory).append("\n");
                            }
                            promptWithHistory.append("\n");
                        }
                        
                        // Add conversation history if available, limiting to last 10 messages
                        int startIndex = Math.max(0, chatHistory.size() - 10);
                        for (int i = startIndex; i < chatHistory.size(); i++) {
                            ChatMessage msg = chatHistory.get(i);
                            promptWithHistory.append(msg.getIsUser() ? "User: " : "Assistant: ");
                            promptWithHistory.append(msg.getText()).append("\n\n");
                        }
                        
                        // Add the current user input
                        promptWithHistory.append("User: ").append(userInput).append("\n\n");
                        promptWithHistory.append("Assistant: ");
                        
                        // Add response structure requirements for actionable prompts
                        if (finalDecision.equals("yes")) {
                            promptWithHistory.append("\n\n**Response Structure Requirements:**");
                            promptWithHistory.append("\n**Part 1: Analysis** - Detailed thinking process");
                            promptWithHistory.append("\n**Part 2: Response** - Actionable steps/advice");
                            promptWithHistory.append("\n**Part 3: Additional Notes** - Optional considerations");
                        }

                        return webClient.post()
                            .uri(uriBuilder -> uriBuilder
                                .path("gemini-2.0-flash:generateContent")
                                .queryParam("key", geminiApiKey)
                                .build())
                            .bodyValue(createGeminiRequest(promptWithHistory.toString()))
                            .retrieve()
                            .onStatus(status -> status.isError(), response -> 
                                response.bodyToMono(String.class)
                                    .flatMap(errorBody -> Mono.error(new RuntimeException(
                                        "API Error: " + response.statusCode() + " - " + errorBody
                                    )))
                            )
                            .bodyToMono(JsonNode.class)
                            .map(finalResponse -> {
                                String processed = extractGeminiResponse(finalResponse)
                                    .replace("\\n", "\n")
                                    .replace("\\\"", "\"");
                                
                                // NEW: Validate and fix calendar response if needed
                                if (calendarAnalysis.hasEvents()) {
                                    processed = calendarValidationService.validateAndFixCalendarResponse(processed, userInput);
                                }
                                
                                storeResponse(processed);
                                return processed;
                            })
                            .flux();
                    } catch (IOException e) {
                        return Flux.just("{\"error\": \"Failed to load prompt template: " + e.getMessage() + "\"}");
                    } catch (Exception e) {
                        return Flux.just("{\"error\": \"" + e.getMessage() + "\"}");
                    }
                })
                .onErrorResume(e -> Flux.just(
                    "{\"error\": \"" + e.getMessage().replace("\"", "\\\"") + "\"}"
                ));
        } catch (Exception e) {
            return Flux.just("{\"error\": \"Failed to process memory analysis: " + e.getMessage() + "\"}");
        }
    }

    private Map<String, Object> createGeminiRequest(String prompt) {
        // Ensure the prompt follows the required format
        if (!prompt.contains(")*!")) {
            prompt = "Instructions:\n" + prompt + "\n\nResponse Format:\n)*!\n[Part 1 Content]\n)*!\n[Part 2 Content]\n)*!\n[Part 3 Content]\n)*!";
        }
        
        return Map.of(
            "contents", List.of(
                Map.of("parts", List.of(
                    Map.of("text", prompt)
                ))
            ),
            "generationConfig", Map.of(
                "temperature", 0.7,
                "topP", 0.8,
                "topK", 40,
                "maxOutputTokens", 2048
            )
        );
    }

    private String extractGeminiResponse(JsonNode responseNode) {
        try {
            if (responseNode.has("error")) {
                return responseNode.get("error").asText();
            }
            
            String response = responseNode
                .path("candidates").get(0)
                .path("content")
                .path("parts").get(0)
                .path("text")
                .asText();
            
            return response.trim();
        } catch (Exception e) {
            return "Error extracting response: " + e.getMessage();
        }
    }

    private String parseFinalDecision(String fullResponse) {
        Pattern pattern = Pattern.compile("\\)\\.\\*\\s*(YES|NO)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(fullResponse);
        
        if (matcher.find()) {
            return matcher.group(1).toLowerCase();
        }
        
        // Fallback decision making
        String lowerResponse = fullResponse.toLowerCase();
        if (lowerResponse.contains("actionable") || lowerResponse.contains("yes")) {
            return "yes";
        }
        return "no";
    }

    private String getFormattedDate() {
        LocalDate today = LocalDate.now();
        String dayOfWeek = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        int day = today.getDayOfMonth();
        String month = today.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        int year = today.getYear();
        
        return dayOfWeek + " the " + day + " of " + month + " " + year;
    }

    @GetMapping("/latest")
    public String getLatestResponse() {
        return latestResponse;
    }
}
