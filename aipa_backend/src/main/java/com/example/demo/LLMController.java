package com.example.demo;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LLMController {
    
    private static String latestResponse = "";
    private static final String TEMP_FILE = "tempres.txt";
    private static final String PROMPT_TEMPLATE_PATH = "promptmst.txt";
    private static final String ANALYSIS_CHECK_PROMPT = 
        "Respond EXACTLY 'yes' or 'no' with no other text or formatting. " +
        "Does this input require analysis for tasks/plans/dates/suggestions? " +
        "Greetings and casual conversation should return 'no'. Input: ";
    
    private void storeResponse(String response) {
        latestResponse = response;
        System.out.println("Memory stored: " + latestResponse.substring(0, Math.min(20, latestResponse.length())) + "...");

        try {
            String content = "Generated at: " + Instant.now() + "\n" + response;
            Files.writeString(
                Paths.get(TEMP_FILE),
                content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
            System.out.println("File written to: " + Paths.get(TEMP_FILE).toAbsolutePath());
        } catch (IOException e) {
            System.err.println("File storage failed: " + e.getMessage());
        }
    }

    @PostMapping("/generate")
    public Flux<String> generateText(@RequestBody Map<String, String> request) {
        String userInput = request.get("prompt");
        WebClient client = WebClient.create("https://aa42-41-90-184-126.ngrok-free.app");

        // Step 1: Check if analysis is needed
        return client.post()
            .uri("/api/generate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "model", "phi3:3.8b",
                "prompt", ANALYSIS_CHECK_PROMPT + userInput,
                "options", Map.of("num_ctx", 2048)
            ))
            .retrieve()
            .bodyToMono(String.class)
            .flatMapMany(analysisDecision -> {
                try {
                    boolean needsAnalysis = analysisDecision.trim().equalsIgnoreCase("yes");
                    String finalPrompt;
                    
                    if (needsAnalysis) {
                        // Use structured template for analyzable inputs
                        String promptTemplate = Files.readString(Paths.get(PROMPT_TEMPLATE_PATH));
                        finalPrompt = promptTemplate + "\nUser Input: " + userInput;
                    } else {
                        // Direct response for greetings/casual inputs
                        finalPrompt = userInput;
                    }

                    StringBuilder responseBuilder = new StringBuilder();
                    
                    // Step 2: Process the actual request
                    return client.post()
                        .uri("/api/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                            "model", "phi3:3.8b",
                            "prompt", finalPrompt,
                            "options", Map.of("num_ctx", 2048)
                        ))
                        .retrieve()
                        .bodyToFlux(String.class)
                        .map(chunk -> {
                            if (chunk.contains("\"response\":")) {
                                String processedChunk = chunk.split("\"response\":\"")[1].split("\"")[0]
                                    .replace("\\n", "\n")
                                    .replace("\\\"", "\"");
                                responseBuilder.append(processedChunk);
                                return processedChunk;
                            }
                            return "";
                        })
                        .doOnComplete(() -> storeResponse(responseBuilder.toString()));

                } catch (IOException e) {
                    return Flux.just("Error: Could not load prompt template");
                }
            })
            .onErrorResume(e -> Flux.just("Error: " + e.getMessage()));
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
            return "File not found";
        }
    }
}