package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface MemoryRepository extends JpaRepository<Memory, UUID> {
    List<Memory> findByUserIdAndCategoryAndIsActiveTrue(UUID userId, String category);
    
    List<Memory> findByUserIdAndIsActiveTrue(UUID userId);
    
    @Query("SELECT DISTINCT m.category FROM Memory m WHERE m.user.id = ?1 AND m.isActive = true")
    List<String> findDistinctCategoriesByUserId(UUID userId);
    
    @Query("SELECT m FROM Memory m WHERE m.user.id = ?1 AND m.category = ?2 AND m.isActive = true ORDER BY m.updatedAt DESC")
    List<Memory> findLatestMemoriesByCategory(UUID userId, String category);
} 
