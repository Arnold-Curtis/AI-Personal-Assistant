package com.example.demo;
import java.util.Map;


import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
public class LLMController {

    @PostMapping("/generate")  // Keep as POST
    public Flux<String> generateText(@RequestBody Map<String, String> request) {
        WebClient client = WebClient.create("https://2e7f-41-90-184-126.ngrok-free.app");
        
        return client.post()
            .uri("/api/generate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(  // Proper JSON construction
                "model", "phi3:3.8b",
                "prompt", request.get("prompt")
            ))
            .retrieve()
            .bodyToFlux(String.class)
            .map(chunk -> {
                if (chunk.contains("\"response\":")) {
                    return chunk.split("\"response\":\"")[1].split("\"")[0];
                }
                return "";
            });
    }
}