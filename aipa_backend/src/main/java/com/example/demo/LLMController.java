package com.example.demo;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.regex.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api")
public class LLMController {
    
    private static String latestResponse = "";
    private static final String TEMP_FILE = "tempres.txt";
    private static final String ANALYSIS_RESULT_FILE = "fpromptres.txt";
    private static final String PROMPT_TEMPLATE_PATH = "promptmst.txt";
    
    private static final String ANALYSIS_CHECK_PROMPT = 
"ROLE: Binary Decision Analyst " +
"GOAL: Output ONLY yes or no based on a 9-point scoring system. " +

"DECISION RULES: " +
"1. Actionable Verb: +1 yes if learn/build/fix/help/understand, +1 no if none " +
"2. Timeframe: +1 yes if ≥3 days, +1 no if <3 days " +
"3. Same-Day Conflict: +1 no if same-day events exist " +
"4. Priority Severity: +1 no if high-priority conflict exists " +
"5. Time Flexibility: +1 yes if flexible schedule mentioned " +
"6. Resource Availability: +1 yes if resources available " +
"7. Goal Specificity: +1 yes if clear goal specified " +
"8. Dependency Check: +1 no if blocked by dependencies " +
"9. Historical Context: +1 yes if positive history with similar tasks " +

"FORMAT: " +
"1. Actionable Verb: [yes/no] " +
"2. Timeframe: [X days] [yes/no] " +
"3. Same-Day Conflict: [no if conflict] " +
"4. Priority Severity: [no if high] " +
"5. Time Flexibility: [yes/no] " +
"6. Resource Availability: [yes/no] " +
"7. Goal Specificity: [yes/no] " +
"8. Dependency Check: [no if blocked] " +
"9. Historical Context: [yes/no] " +
"TOTAL: yes:[X] vs no:[Y] FINAL: [yes/no] " +

"EXAMPLE 1 (YES): " +
"Actionable Verb: yes " +
"Timeframe: 21 days yes " +
"Same-Day Conflict: none " +
"Priority Severity: none " +
"Time Flexibility: yes " +
"Resource Availability: yes " +
"Goal Specificity: yes " +
"Dependency Check: none " +
"Historical Context: yes " +
"TOTAL: yes:7 vs no:0 FINAL: yes " +

"EXAMPLE 2 (NO): " +
"Actionable Verb: yes " +
"Timeframe: 1 day no " +
"Same-Day Conflict: yes no " +
"Priority Severity: yes no " +
"Time Flexibility: no " +
"Resource Availability: no " +
"Goal Specificity: yes " +
"Dependency Check: none " +
"Historical Context: no " +
"TOTAL: yes:2 vs no:7 FINAL: no " +

"DIRECTIVE: You must follow the scoring exactly. If yes > no, output yes. Never override the tally. " +
"USER INPUT:";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient;

    public LLMController(WebClient.Builder webClientBuilder) {
        HttpClient httpClient = HttpClient.create()
            .responseTimeout(Duration.ofSeconds(45))
            .keepAlive(true)
            .compress(true)
            .wiretap(true);

        this.webClient = webClientBuilder
            .baseUrl("https://2059-41-90-178-43.ngrok-free.app")
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader("Connection", "keep-alive")
            .defaultHeader("Keep-Alive", "timeout=45")
            .defaultHeader("ngrok-skip-browser-warning", "true")
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

    @PostMapping("/generate")
    public Flux<String> generateText(@RequestBody Map<String, String> request) {
        String userInput = request.get("prompt");
        
        return webClient.post()
            .uri("/api/generate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "model", "phi3:3.8b",
                "prompt", ANALYSIS_CHECK_PROMPT + userInput,
                "options", Map.of("num_ctx", 2048)
            ))
            .retrieve()
            .bodyToFlux(String.class)
            .timeout(Duration.ofSeconds(40))
            .retry(3)
            .doOnError(e -> System.err.println("API Error: " + e.getClass().getSimpleName() + ": " + e.getMessage()))
            .collect(StringBuilder::new, (sb, chunk) -> {
                try {
                    JsonNode node = objectMapper.readTree(chunk);
                    sb.append(node.path("response").asText());
                } catch (JsonProcessingException e) {
                    System.err.println("JSON Parse Error: " + chunk);
                }
            })
            .flatMapMany(analysisResponse -> {
                String fullResponse = analysisResponse.toString();
                System.out.println("Full Analysis Response:\n" + fullResponse);
                
                String finalDecision = parseFinalDecision(fullResponse);
                System.out.println("Parsed Decision: " + finalDecision);
                
                storeAnalysisResult(fullResponse, finalDecision, userInput);

                try {
                    String finalPrompt = finalDecision.equals("yes") 
                        ? Files.readString(Paths.get(PROMPT_TEMPLATE_PATH)) + "\nUser Input: " + userInput
                        : userInput;

                    return webClient.post()
                        .uri("/api/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                            "model", "phi3:3.8b",
                            "prompt", finalPrompt,
                            "options", Map.of("num_ctx", 2048)
                        ))
                        .retrieve()
                        .bodyToFlux(String.class)
                        .timeout(Duration.ofSeconds(40))
                        .retry(3)
                        .collect(StringBuilder::new, (sb, chunk) -> {
                            try {
                                JsonNode node = objectMapper.readTree(chunk);
                                sb.append(node.path("response").asText());
                            } catch (JsonProcessingException e) {
                                System.err.println("JSON Parse Error: " + chunk);
                            }
                        })
                        .map(finalResponse -> {
                            String processed = finalResponse.toString()
                                .replace("\\n", "\n")
                                .replace("\\\"", "\"");
                            storeResponse(processed);
                            return processed;
                        })
                        .flux();
                } catch (IOException e) {
                    return Flux.just("Error: Failed to load prompt template");
                }
            })
            .onErrorResume(e -> {
                if (e instanceof reactor.netty.http.client.PrematureCloseException) {
                    return Flux.just("Error: Connection to AI service failed. Please try again.");
                }
                return Flux.just("Error: " + e.getMessage());
            });
    }

    private String parseFinalDecision(String fullResponse) {
        Pattern pattern = Pattern.compile(
            "^(?:Decision|Final Decision):\\s*(yes|no)$", 
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
        );
        Matcher matcher = pattern.matcher(fullResponse.trim());
        return matcher.find() ? matcher.group(1).toLowerCase() : "no";
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
            return "Error: Could not load response file";
        }
    }
    
    @GetMapping("/analysis-results")
    public String getAnalysisResults() {
        try {
            return Files.readString(Paths.get(ANALYSIS_RESULT_FILE));
        } catch (IOException e) {
            return "Error: Could not load analysis results";
        }
    }
}