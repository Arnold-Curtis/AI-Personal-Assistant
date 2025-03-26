package com.example.demo;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
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
        try {
            String promptTemplate = Files.readString(Paths.get(PROMPT_TEMPLATE_PATH));
            String fullPrompt = promptTemplate + "\nUser Input: " + request.get("prompt");

            WebClient client = WebClient.create("https://aa42-41-90-184-126.ngrok-free.app");
            StringBuilder responseBuilder = new StringBuilder();

            return client.post()
                .uri("/api/generate?timestamp=" + System.currentTimeMillis())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                    "model", "phi3:3.8b",
                    "prompt", fullPrompt,
                    "options", Map.of("num_ctx", 2048)
                ))
                .retrieve()
                .bodyToFlux(String.class)
                .map(chunk -> {
                    String processedChunk = "";
                    if (chunk.contains("\"response\":")) {
                        processedChunk = chunk.split("\"response\":\"")[1].split("\"")[0];
                        responseBuilder.append(processedChunk);
                    }
                    return processedChunk;
                })
                .doOnComplete(() -> {
                    String fullResponse = responseBuilder.toString();
                    storeResponse(fullResponse);
                    System.out.println("Full response received: " + fullResponse);
                });

        } catch (IOException e) {
            System.err.println("Template load error: " + e.getMessage());
            return Flux.just("Error: Could not load prompt template");
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
            return "File not found";
        }
    }
}