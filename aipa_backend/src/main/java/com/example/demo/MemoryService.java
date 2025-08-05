package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Service
public class MemoryService {
    private final MemoryRepository memoryRepository;
    private final EncryptionUtil encryptionUtil;
    private final UserRepository userRepository;
    private final MemoryAnalysisService memoryAnalysisService;
    private final MemoryDeduplicationService memoryDeduplicationService;

    @Autowired
    public MemoryService(MemoryRepository memoryRepository, EncryptionUtil encryptionUtil, 
                        UserRepository userRepository, MemoryAnalysisService memoryAnalysisService,
                        MemoryDeduplicationService memoryDeduplicationService) {
        this.memoryRepository = memoryRepository;
        this.encryptionUtil = encryptionUtil;
        this.userRepository = userRepository;
        this.memoryAnalysisService = memoryAnalysisService;
        this.memoryDeduplicationService = memoryDeduplicationService;
    }

    @Transactional
    public Memory storeMemory(UUID userId, String category, String content) {
        // Check for duplicates before storing
        MemoryDeduplicationService.DuplicationCheckResult duplicationCheck = 
            memoryDeduplicationService.checkForDuplicate(userId, category, content);
        
        if (duplicationCheck.isDuplicate()) {
            // Update the existing memory's timestamp to show it's still relevant
            Memory existingMemory = duplicationCheck.getDuplicateMemory();
            existingMemory.setUpdatedAt(LocalDateTime.now());
            return memoryRepository.save(existingMemory);
        }
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
            
        Memory memory = new Memory();
        memory.setUser(user);
        memory.setCategory(category);
        memory.setEncryptedContent(encryptionUtil.encrypt(content));
        
        return memoryRepository.save(memory);
    }

    @Transactional(readOnly = true)
    public List<String> getCategories(UUID userId) {
        return memoryRepository.findDistinctCategoriesByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCategoriesWithCounts(UUID userId) {
        List<String> categories = getCategories(userId);
        return categories.stream()
            .map(category -> {
                long count = memoryRepository.findByUserIdAndCategoryAndIsActiveTrue(userId, category).size();
                Map<String, Object> categoryInfo = new HashMap<>();
                categoryInfo.put("name", category);
                categoryInfo.put("count", count);
                return categoryInfo;
            })
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<String> getMemoriesByCategory(UUID userId, String category) {
        return memoryRepository.findByUserIdAndCategoryAndIsActiveTrue(userId, category)
            .stream()
            .map(memory -> encryptionUtil.decrypt(memory.getEncryptedContent()))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<String> getAllMemories(UUID userId) {
        return memoryRepository.findByUserIdAndIsActiveTrue(userId)
            .stream()
            .map(memory -> encryptionUtil.decrypt(memory.getEncryptedContent()))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllMemoriesWithDetails(UUID userId) {
        return memoryRepository.findByUserIdAndIsActiveTrue(userId)
            .stream()
            .map(memory -> {
                Map<String, Object> memoryDetails = new HashMap<>();
                memoryDetails.put("id", memory.getId());
                memoryDetails.put("content", encryptionUtil.decrypt(memory.getEncryptedContent()));
                memoryDetails.put("category", memory.getCategory());
                memoryDetails.put("createdAt", memory.getCreatedAt());
                memoryDetails.put("updatedAt", memory.getUpdatedAt());
                memoryDetails.put("isActive", memory.isActive());
                return memoryDetails;
            })
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMemoryDetail(UUID userId, UUID memoryId) {
        return memoryRepository.findById(memoryId)
            .filter(memory -> memory.getUser().getId().equals(userId) && memory.isActive())
            .map(memory -> {
                Map<String, Object> memoryDetails = new HashMap<>();
                memoryDetails.put("id", memory.getId());
                memoryDetails.put("content", encryptionUtil.decrypt(memory.getEncryptedContent()));
                memoryDetails.put("category", memory.getCategory());
                memoryDetails.put("createdAt", memory.getCreatedAt());
                memoryDetails.put("updatedAt", memory.getUpdatedAt());
                memoryDetails.put("isActive", memory.isActive());
                return memoryDetails;
            })
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchMemories(UUID userId, String query, String category) {
        List<Memory> memories;
        
        if (category != null && !category.trim().isEmpty()) {
            memories = memoryRepository.findByUserIdAndCategoryAndIsActiveTrue(userId, category);
        } else {
            memories = memoryRepository.findByUserIdAndIsActiveTrue(userId);
        }
        
        return memories.stream()
            .map(memory -> {
                String decryptedContent = encryptionUtil.decrypt(memory.getEncryptedContent());
                Map<String, Object> memoryDetails = new HashMap<>();
                memoryDetails.put("id", memory.getId());
                memoryDetails.put("content", decryptedContent);
                memoryDetails.put("category", memory.getCategory());
                memoryDetails.put("createdAt", memory.getCreatedAt());
                memoryDetails.put("updatedAt", memory.getUpdatedAt());
                memoryDetails.put("isActive", memory.isActive());
                return memoryDetails;
            })
            .filter(memoryDetails -> {
                String content = (String) memoryDetails.get("content");
                String memoryCategory = (String) memoryDetails.get("category");
                return content.toLowerCase().contains(query.toLowerCase()) ||
                       memoryCategory.toLowerCase().contains(query.toLowerCase());
            })
            .collect(Collectors.toList());
    }

    @Transactional
    public void deactivateMemory(UUID memoryId) {
        memoryRepository.findById(memoryId).ifPresent(memory -> {
            memory.setActive(false);
            memoryRepository.save(memory);
        });
    }

    @Transactional(readOnly = true)
    public List<String> getLatestMemoriesByCategory(UUID userId, String category) {
        return memoryRepository.findLatestMemoriesByCategory(userId, category)
            .stream()
            .map(memory -> encryptionUtil.decrypt(memory.getEncryptedContent()))
            .collect(Collectors.toList());
    }

    /**
     * Enhanced memory analysis and storage
     */
    @Transactional
    public MemoryAnalysisService.MemoryAnalysisResult analyzeAndStoreMemory(UUID userId, String userInput) {
        try {
            // Get existing categories
            List<String> existingCategories = getCategories(userId);
            
            // Analyze the input for memory content
            MemoryAnalysisService.MemoryAnalysisResult analysis = 
                memoryAnalysisService.analyzeForMemory(userInput, existingCategories);
            
            // Store memory if analysis indicates we should
            if (analysis.shouldStore()) {
                String categoryToUse = !analysis.getCategoryMatch().equals("None") 
                    ? analysis.getCategoryMatch() 
                    : analysis.getNewCategorySuggestion();
                    
                if (!categoryToUse.equals("None")) {
                    storeMemory(userId, categoryToUse, analysis.getMemoryToStore());
                }
            }
            
            return analysis;
        } catch (Exception e) {
            return new MemoryAnalysisService.MemoryAnalysisResult("None", "None", "None", "low", "None");
        }
    }

    /**
     * Enhanced memory retrieval with fuzzy matching
     */
    @Transactional(readOnly = true)
    public List<String> getRelevantMemories(UUID userId, String context) {
        List<String> allMemories = getAllMemories(userId);
        List<String> relevantMemories = new ArrayList<>();
        
        String lowerContext = context.toLowerCase();
        
        // Direct keyword matching
        for (String memory : allMemories) {
            String lowerMemory = memory.toLowerCase();
            if (containsRelevantKeywords(lowerContext, lowerMemory)) {
                relevantMemories.add(memory);
            }
        }
        
        // If no direct matches, look for category-based matches
        if (relevantMemories.isEmpty()) {
            List<String> categories = getCategories(userId);
            for (String category : categories) {
                if (categoryMatches(lowerContext, category.toLowerCase())) {
                    List<String> categoryMemories = getMemoriesByCategory(userId, category);
                    relevantMemories.addAll(categoryMemories);
                    if (relevantMemories.size() >= 5) break; // Limit to 5 memories
                }
            }
        }
        
        return relevantMemories;
    }

    private boolean containsRelevantKeywords(String context, String memory) {
        // Check for common keywords
        String[] contextWords = context.split("\\s+");
        String[] memoryWords = memory.split("\\s+");
        
        for (String contextWord : contextWords) {
            if (contextWord.length() > 3) { // Only check meaningful words
                for (String memoryWord : memoryWords) {
                    if (memoryWord.toLowerCase().contains(contextWord.toLowerCase()) ||
                        contextWord.toLowerCase().contains(memoryWord.toLowerCase())) {
                        return true;
                    }
                }
            }
        }
        
        // Check for specific patterns
        if (context.contains("birthday") && memory.toLowerCase().contains("birthday")) return true;
        if (context.contains("learn") && memory.toLowerCase().contains("learn")) return true;
        if (context.contains("like") && memory.toLowerCase().contains("like")) return true;
        if (context.contains("goal") && memory.toLowerCase().contains("goal")) return true;
        
        return false;
    }

    private boolean categoryMatches(String context, String category) {
        // Check if context relates to category
        if (context.contains("birthday") && category.contains("personal")) return true;
        if (context.contains("learn") && category.contains("goal")) return true;
        if (context.contains("like") && category.contains("preference")) return true;
        if (context.contains("family") && category.contains("family")) return true;
        
        return false;
    }

    /**
     * Update memory content (for correcting or updating existing memories)
     */
    @Transactional
    public boolean updateMemory(UUID memoryId, String newContent) {
        return memoryRepository.findById(memoryId)
            .map(memory -> {
                memory.setEncryptedContent(encryptionUtil.encrypt(newContent));
                memory.setUpdatedAt(LocalDateTime.now());
                memoryRepository.save(memory);
                return true;
            })
            .orElse(false);
    }

    /**
     * Get memory statistics for debugging
     */
    @Transactional(readOnly = true)
    public String getMemoryStats(UUID userId) {
        List<String> categories = getCategories(userId);
        int totalMemories = getAllMemories(userId).size();
        
        StringBuilder stats = new StringBuilder();
        stats.append("Memory Statistics:\n");
        stats.append("Total Categories: ").append(categories.size()).append("\n");
        stats.append("Total Memories: ").append(totalMemories).append("\n");
        stats.append("Categories: ").append(String.join(", ", categories)).append("\n");
        
        return stats.toString();
    }
    
    /**
     * Clean up existing duplicate memories
     */
    @Transactional
    public Map<String, Object> cleanupDuplicates(UUID userId) {
        List<Map<String, Object>> allMemories = getAllMemoriesWithDetails(userId);
        int originalCount = allMemories.size();
        int duplicatesRemoved = 0;
        
        // Group memories by category
        Map<String, List<Map<String, Object>>> memoriesByCategory = new HashMap<>();
        for (Map<String, Object> memory : allMemories) {
            String category = (String) memory.get("category");
            memoriesByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(memory);
        }
        
        // Check each category for duplicates
        for (Map.Entry<String, List<Map<String, Object>>> entry : memoriesByCategory.entrySet()) {
            String category = entry.getKey();
            List<Map<String, Object>> memories = entry.getValue();
            
            // Sort by creation date (oldest first)
            memories.sort((m1, m2) -> 
                ((LocalDateTime) m1.get("createdAt"))
                .compareTo((LocalDateTime) m2.get("createdAt"))
            );
            
            Set<UUID> processedMemories = new HashSet<>();
            
            for (int i = 0; i < memories.size(); i++) {
                Map<String, Object> memory1 = memories.get(i);
                UUID memory1Id = (UUID) memory1.get("id");
                
                if (processedMemories.contains(memory1Id)) continue;
                
                // Check all subsequent memories for duplicates
                for (int j = i + 1; j < memories.size(); j++) {
                    Map<String, Object> memory2 = memories.get(j);
                    UUID memory2Id = (UUID) memory2.get("id");
                    
                    if (processedMemories.contains(memory2Id)) continue;
                    
                    String content2 = (String) memory2.get("content");
                    
                    // Check if this is a duplicate
                    MemoryDeduplicationService.DuplicationCheckResult result = 
                        memoryDeduplicationService.checkForDuplicate(userId, category, content2);
                    
                    if (result.isDuplicate() && result.getDuplicateMemory() != null && 
                        result.getDuplicateMemory().getId().equals(memory1Id)) {
                        // Deactivate the newer duplicate
                        deactivateMemory(memory2Id);
                        processedMemories.add(memory2Id);
                        duplicatesRemoved++;
                    }
                }
                
                processedMemories.add(memory1Id);
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("originalMemoryCount", originalCount);
        result.put("duplicatesRemoved", duplicatesRemoved);
        result.put("finalMemoryCount", originalCount - duplicatesRemoved);
        result.put("message", "Cleaned up " + duplicatesRemoved + " duplicate memories");
        
        return result;
    }
} 