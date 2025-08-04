package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionMemoryRepository extends JpaRepository<SessionMemory, UUID> {
    
    @Query("SELECT s FROM SessionMemory s WHERE s.user.id = ?1 AND s.sessionId = ?2 AND s.isActive = true")
    Optional<SessionMemory> findActiveSessionByUserAndSessionId(UUID userId, String sessionId);
    
    @Query("SELECT s FROM SessionMemory s WHERE s.user.id = ?1 AND s.isActive = true ORDER BY s.lastActivity DESC")
    List<SessionMemory> findActiveSessionsByUser(UUID userId);
    
    @Query("SELECT s FROM SessionMemory s WHERE s.user.id = ?1 AND s.isActive = true ORDER BY s.lastActivity DESC")
    Optional<SessionMemory> findMostRecentActiveSession(UUID userId);
    
    @Query("UPDATE SessionMemory s SET s.isActive = false WHERE s.user.id = ?1 AND s.lastActivity < ?2")
    void deactivateOldSessions(UUID userId, LocalDateTime cutoffTime);
    
    @Query("SELECT COUNT(s) FROM SessionMemory s WHERE s.user.id = ?1 AND s.isActive = true")
    long countActiveSessionsByUser(UUID userId);
}
