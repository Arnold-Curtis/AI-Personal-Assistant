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

@RestController
@RequestMapping("/api")
public class LLMController {
    
    private static String latestResponse = "";
    private static final String TEMP_FILE = "tempres.txt";
    private static final String ANALYSIS_RESULT_FILE = "fpromptres.txt";
    private static final String PROMPT_TEMPLATE_PATH = "promptmst.txt";
    
    private static final String ANALYSIS_CHECK_PROMPT = 
    "Analyze the given input and determine whether it is actionable or non-actionable based on the following guiding factors:\n" +
    "Actionable Verb: Does it contain verbs like learn, build, fix, help, understand that imply action?\n" +
    "Timeframe: Is the task something that extends beyond 3 days, or is it immediate? If the input is a scheduled event (e.g., a meeting, wedding, appointment), consider it actionable.\n" +
    "Same-Day Conflict: Is there any mention of an urgent, same-day event that would interfere?\n" +
    "Priority Severity: Is the task blocked by a high-priority issue?\n" +
    "Time Flexibility: Does it mention flexibility in scheduling?\n" +
    "Resource Availability: Are the necessary resources available to complete the task?\n" +
    "Goal Specificity: Is there a clear and specific goal being described?\n" +
    "Dependency Check: Is the task dependent on something else before it can be done?\n" +
    "Historical Context: Has the person successfully done similar tasks before?\n" +
    "Special Case - Calendar Events: If the input specifies a scheduled event (e.g., 'I have a wedding in 2 weeks' or 'My meeting is next Friday'), then it is considered actionable, even if no action verb is explicitly stated.\n" +
    "Response Format:\n" +
    "The response should be split into two sections:\n" +
    "Thinking Space: This is where the AI evaluates the prompt, breaking it down based on the above factors. The AI should \"think out loud,\" explaining its reasoning.\n" +
    "Final Decision: The AI should strictly return either YES (actionable) or NO (non-actionable), formatted as: ).* YES or ).* NO\n" +
    "Example 1 (Non-Actionable Input):\n" +
    "Input:\n\"Hey man, how's it going?\"\n" +
    "Output:\nThinking Space:\n" +
    "No actionable verb detected.\n" +
    "No timeframe mentioned.\n" +
    "No indication of resources, specific goals, or dependencies.\n" +
    "This is purely conversational with no clear task to complete.\n" +
    ").* NO\n" +
    "Example 2 (Actionable Input - Learning Task):\n" +
    "Input:\n\"Hey, I would love to learn Java and get closer to my family.\"\n" +
    "Output:\nThinking Space:\n" +
    "\"Learn\" is an actionable verb.\n" +
    "No specific timeframe, but learning is generally long-term.\n" +
    "No conflicts or urgent blockers mentioned.\n" +
    "Clear goal (learning Java, improving relationships).\n" +
    "Resources for learning Java are widely available.\n" +
    "No dependencies mentioned.\n" +
    "Historical context is unknown, but learning Java is feasible.\n" +
    ").* YES\n" +
    "Example 3 (Actionable Input - Scheduled Event):\n" +
    "Input:\n\"Whats up man, I have a wedding in 2 weeks.\"\n" +
    "Output:\nThinking Space:\n" +
    "No actionable verb present, but a scheduled event is mentioned.\n" +
    "Timeframe: The wedding is in two weeks, which is a defined future event.\n" +
    "Same-Day Conflict: Not applicable unless another task is in conflict.\n" +
    "Priority Severity: Not relevant since it’s an event, not a task.\n" +
    "Time Flexibility: The event is on a fixed date.\n" +
    "Resource Availability: Not applicable.\n" +
    "Goal Specificity: The goal (attending a wedding) is clear.\n" +
    "Dependency Check: No dependencies needed for recognizing the event.\n" +
    "Since it is a scheduled event, it qualifies as actionable.\n" +
    ").* YES";


    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public LLMController(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .baseUrl("https://generativelanguage.googleapis.com/v1beta/models/")
            .defaultHeader("Content-Type", "application/json")
            .build();
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

    // Class to represent a chat message
    static class ChatMessage {
        private String text;
        private boolean isUser;
        
        public String getText() {
            return text;
        }
        
        public void setText(String text) {
            this.text = text;
        }
        
        public boolean getIsUser() {
            return isUser;
        }
        
        public void setIsUser(boolean isUser) {
            this.isUser = isUser;
        }
    }

    @PostMapping("/generate")
    public Flux<String> generateText(@RequestBody Map<String, Object> request) {
        String userInput = (String) request.get("prompt");
        final List<ChatMessage> chatHistory = new ArrayList<>();
        
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
        
        return webClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("gemini-2.0-flash:generateContent")
                .queryParam("key", geminiApiKey)
                .build())
            .bodyValue(createGeminiRequest(ANALYSIS_CHECK_PROMPT + userInput))
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
                    
                    // Use template for actionable prompts
                    if (finalDecision.equals("yes")) {
                        String promptTemplate = Files.readString(Paths.get(PROMPT_TEMPLATE_PATH));
                        
                        // Replace date placeholders with actual date
                        String formattedDate = getFormattedDate();
                        promptTemplate = promptTemplate.replace("[DAY_OF_WEEK] the [DAY] of [MONTH] [YEAR]", formattedDate);
                        
                        promptWithHistory.append(promptTemplate);
                        promptWithHistory.append("User Input & Today is: " + formattedDate + "\n" + userInput);
                        promptWithHistory.append("\n\n**Conversation History:**\n");
                    } else {
                        promptWithHistory.append("You are an AI assistant helping with a conversation.\n**Conversation History:**\n");
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
    }

    private Map<String, Object> createGeminiRequest(String prompt) {
        return Map.of(
            "contents", List.of(
                Map.of("parts", List.of(
                    Map.of("text", prompt)
                ))
            ),
            "generationConfig", Map.of(
                "temperature", 0.9,
                "topP", 1,
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
            return responseNode
                .path("candidates").get(0)
                .path("content")
                .path("parts").get(0)
                .path("text")
                .asText();
        } catch (Exception e) {
            System.err.println("Failed to parse Gemini response: " + responseNode);
            return "{\"error\": \"Failed to parse API response\"}";
        }
    }

    private String parseFinalDecision(String fullResponse) {
        try {
            Pattern pattern = Pattern.compile(
                "\\*\\s*(yes|no)\\s*$", 
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
            );
            Matcher matcher = pattern.matcher(fullResponse.trim());
            return matcher.find() ? matcher.group(1).toLowerCase() : "no";
        } catch (Exception e) {
            return "no";
        }
    }
    
    @GetMapping("/last-response")
    public String getLastResponse() {
        return latestResponse;
    }
    
    @GetMapping("/last-response-file")
    public String getLastResponseFile() {
        try {
            return Files.readString(Paths.get(TEMP_FILE));
        } catch (IOException e) {
            return "{\"error\": \"Could not load response file\"}";
        }
    }
    
    @GetMapping("/analysis-results")
    public String getAnalysisResults() {
        try {
            return Files.readString(Paths.get(ANALYSIS_RESULT_FILE));
        } catch (IOException e) {
            return "{\"error\": \"Could not load analysis results\"}";
        }
    }

    private String getFormattedDate() {
        LocalDate today = LocalDate.now();
        String dayOfWeek = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        int day = today.getDayOfMonth();
        String month = today.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        int year = today.getYear();
        
        return dayOfWeek + " the " + day + " of " + month + " " + year;
    }
}