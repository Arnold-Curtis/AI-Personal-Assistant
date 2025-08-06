package com.example.demo;

import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/public/test")
public class PlanTestController {
    
    @Autowired
    private PlanAnalysisService planAnalysisService;
    
    @PostMapping("/plan-analysis")
    public Map<String, Object> testPlanAnalysis(@RequestBody Map<String, String> request) {
        String userInput = request.get("input");
        
        if (userInput == null || userInput.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Input is required");
            return error;
        }
        
        try {
            PlanAnalysisService.PlanAnalysisResult result = planAnalysisService.analyzeForPlan(userInput);
            
            Map<String, Object> response = new HashMap<>();
            response.put("input", userInput);
            response.put("requiresPlan", result.requiresPlan());
            response.put("confidence", result.getConfidence());
            response.put("reasoning", result.getReasoning());
            response.put("planType", result.getPlanType());
            response.put("estimatedDuration", result.getEstimatedDuration());
            response.put("complexityScore", result.getComplexityScore());
            response.put("shouldCreatePlan", result.shouldCreatePlan());
            
            return response;
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to analyze plan: " + e.getMessage());
            return error;
        }
    }
    
    @GetMapping("/plan-examples")
    public Map<String, Object> getPlanExamples() {
        Map<String, Object> examples = new HashMap<>();
        
        
        String[] planWorthy = {
            "I want to learn Java programming",
            "I'd like to start a fitness routine",
            "I want to learn Spanish",
            "Help me start a small business",
            "I want to develop a morning routine",
            "I need to train for a marathon",
            "I want to improve my cooking skills"
        };
        
        
        String[] notPlanWorthy = {
            "What is 1 + 1?",
            "How do I cook pasta?",
            "What's the weather today?",
            "Can you help me write an email?",
            "What time is it?",
            "I have a wedding in 2 weeks",
            "Recommend some movies",
            "How do I reset my password?",
            "Install this app for me"
        };
        
        examples.put("planWorthy", planWorthy);
        examples.put("notPlanWorthy", notPlanWorthy);
        examples.put("instructions", "Test these inputs with the /plan-analysis endpoint to verify robust plan detection");
        
        return examples;
    }
}

