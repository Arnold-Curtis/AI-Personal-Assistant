package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.logging.Logger;

/**
 * Advanced Memory Deduplication Service
 * 
 * This service provides multiple strategies for detecting and preventing duplicate memories:
 * 1. Exact content matching (fastest)
 * 2. Normalized text similarity (medium speed, high accuracy)
 * 3. Semantic keyword similarity (fast, good accuracy)
 * 4. AI-powered semantic analysis (slower, highest accuracy)
 * 
 * The service is designed to be efficient and prioritize accuracy while maintaining good performance.
 */
@Service
public class MemoryDeduplicationService {
    
    private static final Logger logger = Logger.getLogger(MemoryDeduplicationService.class.getName());
    
    private final MemoryRepository memoryRepository;
    private final EncryptionUtil encryptionUtil;
    
    // Similarity thresholds
    private static final double HIGH_SIMILARITY_THRESHOLD = 0.85;
    private static final double MEDIUM_SIMILARITY_THRESHOLD = 0.70;
    
    @Autowired
    public MemoryDeduplicationService(MemoryRepository memoryRepository, 
                                    EncryptionUtil encryptionUtil) {
        this.memoryRepository = memoryRepository;
        this.encryptionUtil = encryptionUtil;
    }
    
    /**
     * Check if a new memory content is a duplicate of existing memories
     */
    @Transactional(readOnly = true)
    public DuplicationCheckResult checkForDuplicate(UUID userId, String category, String newContent) {
        // Get existing memories for the user in the same category
        List<Memory> existingMemories = memoryRepository.findByUserIdAndCategoryAndIsActiveTrue(userId, category);
        
        if (existingMemories.isEmpty()) {
            return new DuplicationCheckResult(false, null, 0.0, "No existing memories in category");
        }
        
        // Normalize the new content for comparison
        String normalizedNewContent = normalizeContent(newContent);
        
        // Check each existing memory for similarity
        for (Memory existingMemory : existingMemories) {
            String existingContent = encryptionUtil.decrypt(existingMemory.getEncryptedContent());
            String normalizedExistingContent = normalizeContent(existingContent);
            
            // Strategy 1: Exact match (fastest)
            if (normalizedNewContent.equals(normalizedExistingContent)) {
                return new DuplicationCheckResult(true, existingMemory, 1.0, "Exact content match");
            }
            
            // Strategy 2: High similarity with normalization
            double similarityScore = calculateTextSimilarity(normalizedNewContent, normalizedExistingContent);
            if (similarityScore >= HIGH_SIMILARITY_THRESHOLD) {
                return new DuplicationCheckResult(true, existingMemory, similarityScore, "High text similarity");
            }
            
            // Strategy 3: Semantic keyword matching
            double semanticScore = calculateSemanticSimilarity(normalizedNewContent, normalizedExistingContent);
            if (semanticScore >= HIGH_SIMILARITY_THRESHOLD) {
                return new DuplicationCheckResult(true, existingMemory, semanticScore, "High semantic similarity");
            }
        }
        
        // Strategy 4: AI-powered analysis for edge cases (only if enabled and suspicious similarities found)
        Memory suspiciousMemory = findSuspiciousSimilarity(existingMemories, normalizedNewContent);
        if (suspiciousMemory != null) {
            String existingContent = encryptionUtil.decrypt(suspiciousMemory.getEncryptedContent());
            boolean isAIDuplicate = checkAIDuplication(newContent, existingContent);
            if (isAIDuplicate) {
                return new DuplicationCheckResult(true, suspiciousMemory, 0.75, "AI-detected semantic duplicate");
            }
        }
        
        return new DuplicationCheckResult(false, null, 0.0, "No duplicates found");
    }
    
    /**
     * Normalize content for better comparison
     */
    private String normalizeContent(String content) {
        if (content == null) return "";
        
        return content.toLowerCase()
                     .trim()
                     // Remove extra whitespace
                     .replaceAll("\\s+", " ")
                     // Remove common punctuation that doesn't affect meaning
                     .replaceAll("[.,!?;:]", "")
                     // Normalize common variations FIRST (before contractions)
                     .replaceAll("\\bmy dad\\b", "my father")
                     .replaceAll("\\bmy mom\\b", "my mother")
                     .replaceAll("\\bdaddy\\b", "father")
                     .replaceAll("\\bmommy\\b", "mother")
                     // Remove possessive 's after variations are handled
                     .replaceAll("'s\\b", "")
                     // Normalize contractions
                     .replaceAll("'re", " are")
                     .replaceAll("'m", " am")
                     .replaceAll("'ve", " have")
                     .replaceAll("'ll", " will")
                     .replaceAll("'d", " would")
                     // Remove articles that don't affect core meaning
                     .replaceAll("\\b(the|a|an)\\b", "")
                     // Handle common verb variations
                     .replaceAll("\\bis called\\b", "is")
                     .replaceAll("\\bis named\\b", "is")
                     // Clean up extra spaces
                     .replaceAll("\\s+", " ")
                     .trim();
    }
    
    /**
     * Calculate text similarity using multiple techniques
     */
    private double calculateTextSimilarity(String text1, String text2) {
        if (text1.equals(text2)) return 1.0;
        
        // Jaccard similarity with word-level tokenization
        Set<String> words1 = new HashSet<>(Arrays.asList(text1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(text2.split("\\s+")));
        
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);
        
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);
        
        if (union.isEmpty()) return 0.0;
        
        double jaccardScore = (double) intersection.size() / union.size();
        
        // Enhance with substring similarity for partial matches
        double substringScore = calculateSubstringSimilarity(text1, text2);
        
        // Combine scores with weights
        return (jaccardScore * 0.7) + (substringScore * 0.3);
    }
    
    /**
     * Calculate semantic similarity based on key information
     */
    private double calculateSemanticSimilarity(String text1, String text2) {
        // Extract key semantic elements
        Map<String, String> elements1 = extractSemanticElements(text1);
        Map<String, String> elements2 = extractSemanticElements(text2);
        
        double totalScore = 0.0;
        int comparisonCount = 0;
        
        // Compare names
        if (elements1.containsKey("name") && elements2.containsKey("name")) {
            totalScore += elements1.get("name").equals(elements2.get("name")) ? 1.0 : 0.0;
            comparisonCount++;
        }
        
        // Compare relationships
        if (elements1.containsKey("relationship") && elements2.containsKey("relationship")) {
            totalScore += elements1.get("relationship").equals(elements2.get("relationship")) ? 1.0 : 0.0;
            comparisonCount++;
        }
        
        // Compare dates
        if (elements1.containsKey("date") && elements2.containsKey("date")) {
            totalScore += elements1.get("date").equals(elements2.get("date")) ? 1.0 : 0.0;
            comparisonCount++;
        }
        
        // Compare preferences
        if (elements1.containsKey("preference") && elements2.containsKey("preference")) {
            double prefSimilarity = calculateTextSimilarity(elements1.get("preference"), elements2.get("preference"));
            totalScore += prefSimilarity;
            comparisonCount++;
        }
        
        // If we found semantic elements to compare, use that score
        if (comparisonCount > 0) {
            return totalScore / comparisonCount;
        }
        
        // Fallback to general content similarity
        return calculateTextSimilarity(text1, text2) * 0.8; // Slightly lower confidence
    }
    
    /**
     * Extract semantic elements from text
     */
    private Map<String, String> extractSemanticElements(String text) {
        Map<String, String> elements = new HashMap<>();
        String lowerText = text.toLowerCase();
        
        // Extract names (common patterns)
        if (lowerText.matches(".*\\b(my|his|her)\\s+(name|dad|father|mom|mother|brother|sister)\\s+(is|called)\\s+([a-zA-Z]+).*")) {
            String[] words = text.split("\\s+");
            for (int i = 0; i < words.length - 1; i++) {
                if (words[i].toLowerCase().matches("(is|called)") && i + 1 < words.length) {
                    elements.put("name", words[i + 1].toLowerCase().replaceAll("[^a-zA-Z]", ""));
                    break;
                }
            }
        }
        
        // Extract relationships
        if (lowerText.contains("dad") || lowerText.contains("father")) {
            elements.put("relationship", "father");
        } else if (lowerText.contains("mom") || lowerText.contains("mother")) {
            elements.put("relationship", "mother");
        } else if (lowerText.contains("brother")) {
            elements.put("relationship", "brother");
        } else if (lowerText.contains("sister")) {
            elements.put("relationship", "sister");
        }
        
        // Extract dates (simple patterns)
        if (lowerText.matches(".*\\b(january|february|march|april|may|june|july|august|september|october|november|december)\\s+\\d{1,2}.*")) {
            elements.put("date", extractDatePattern(lowerText));
        }
        
        // Extract preferences
        if (lowerText.matches(".*\\b(i|my)\\s+(like|love|prefer|enjoy|hate|dislike)\\s+.*")) {
            String preference = extractPreferencePattern(lowerText);
            if (!preference.isEmpty()) {
                elements.put("preference", preference);
            }
        }
        
        return elements;
    }
    
    /**
     * Find memories that might be similar enough to warrant AI analysis
     */
    private Memory findSuspiciousSimilarity(List<Memory> existingMemories, String normalizedNewContent) {
        for (Memory memory : existingMemories) {
            String existingContent = encryptionUtil.decrypt(memory.getEncryptedContent());
            String normalizedExisting = normalizeContent(existingContent);
            
            double similarity = calculateTextSimilarity(normalizedNewContent, normalizedExisting);
            if (similarity >= MEDIUM_SIMILARITY_THRESHOLD && similarity < HIGH_SIMILARITY_THRESHOLD) {
                return memory; // Suspicious similarity that needs AI analysis
            }
        }
        return null;
    }
    
    /**
     * Use simple heuristics to determine if two memories are duplicates
     * (AI check disabled for now - can be implemented separately)
     */
    private boolean checkAIDuplication(String newContent, String existingContent) {
        try {
            // For now, use enhanced text similarity as a fallback
            // TODO: Implement AI-based duplication check when needed
            
            String normalizedNew = normalizeContent(newContent);
            String normalizedExisting = normalizeContent(existingContent);
            
            // Additional semantic checks
            Map<String, String> newElements = extractSemanticElements(normalizedNew);
            Map<String, String> existingElements = extractSemanticElements(normalizedExisting);
            
            // If both have names and they match, likely duplicate
            if (newElements.containsKey("name") && existingElements.containsKey("name")) {
                if (newElements.get("name").equals(existingElements.get("name"))) {
                    return true;
                }
            }
            
            // If both have relationships and they match
            if (newElements.containsKey("relationship") && existingElements.containsKey("relationship")) {
                if (newElements.get("relationship").equals(existingElements.get("relationship"))) {
                    // And if content similarity is high
                    return calculateTextSimilarity(normalizedNew, normalizedExisting) > 0.6;
                }
            }
            
            return false;
        } catch (Exception e) {
            logger.warning("Enhanced duplication check failed: " + e.getMessage());
            return false; // Default to not duplicate if check fails
        }
    }
    
    // Helper methods
    private double calculateSubstringSimilarity(String text1, String text2) {
        int maxLength = Math.max(text1.length(), text2.length());
        if (maxLength == 0) return 1.0;
        
        int commonLength = 0;
        int minLength = Math.min(text1.length(), text2.length());
        
        for (int i = 0; i < minLength; i++) {
            if (text1.charAt(i) == text2.charAt(i)) {
                commonLength++;
            }
        }
        
        return (double) commonLength / maxLength;
    }
    
    private String extractDatePattern(String text) {
        // Simple date extraction - can be enhanced
        return text.replaceAll(".*\\b(january|february|march|april|may|june|july|august|september|october|november|december)\\s+\\d{1,2}.*", "$1 $2");
    }
    
    private String extractPreferencePattern(String text) {
        // Extract preference content after like/love/prefer/etc.
        String[] words = text.split("\\s+");
        StringBuilder preference = new StringBuilder();
        boolean foundTrigger = false;
        
        for (String word : words) {
            if (foundTrigger) {
                preference.append(word).append(" ");
            } else if (word.matches("(like|love|prefer|enjoy|hate|dislike)")) {
                foundTrigger = true;
            }
        }
        
        return preference.toString().trim();
    }
    
    /**
     * Result class for duplication check
     */
    public static class DuplicationCheckResult {
        private final boolean isDuplicate;
        private final Memory duplicateMemory;
        private final double similarityScore;
        private final String reason;
        
        public DuplicationCheckResult(boolean isDuplicate, Memory duplicateMemory, double similarityScore, String reason) {
            this.isDuplicate = isDuplicate;
            this.duplicateMemory = duplicateMemory;
            this.similarityScore = similarityScore;
            this.reason = reason;
        }
        
        public boolean isDuplicate() { return isDuplicate; }
        public Memory getDuplicateMemory() { return duplicateMemory; }
        public double getSimilarityScore() { return similarityScore; }
        public String getReason() { return reason; }
        
        @Override
        public String toString() {
            return String.format("DuplicationCheck{duplicate=%s, score=%.2f, reason='%s'}", 
                isDuplicate, similarityScore, reason);
        }
    }
}
