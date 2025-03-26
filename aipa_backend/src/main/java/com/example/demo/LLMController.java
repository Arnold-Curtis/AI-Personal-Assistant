// LLMController.java
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
    
    // In-Memory Storage (Latest response only)
    private static String latestResponse = "";
    
    // File Storage Configuration
    private static final String TEMP_FILE = "tempres.txt";
    
    private void storeResponse(String response) {
        // Store in memory (replace previous)
        latestResponse = response;
        System.out.println("Memory stored: " + latestResponse.substring(0, Math.min(20, latestResponse.length())) + "...");

        // Store in file (overwrite completely)
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
        WebClient client = WebClient.create("https://aa42-41-90-184-126.ngrok-free.app");
        StringBuilder responseBuilder = new StringBuilder();

        return client.post()
            .uri("/api/generate?timestamp=" + System.currentTimeMillis()) // Unique URL each call
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "model", "phi3:3.8b",
                "prompt", request.get("prompt"),
                "options", Map.of("num_ctx", 2048) // Explicit context reset
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
                // Final storage processing
                String fullResponse = responseBuilder.toString();
                storeResponse(fullResponse);
            });
    }
    
    // Debugging Endpoints (Optional)
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