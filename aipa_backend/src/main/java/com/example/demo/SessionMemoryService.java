package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionMemoryService {
    
    private final SessionMemoryRepository sessionRepository;
    private final UserRepository userRepository;
    private final CalendarSessionContextService calendarContextService;
    
    @Autowired
    public SessionMemoryService(SessionMemoryRepository sessionRepository, 
                               UserRepository userRepository,
                               CalendarSessionContextService calendarContextService) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.calendarContextService = calendarContextService;
    }
    
    /**
     * Track a new chat message in a session and determine if context should be sent
     */
    @Transactional
    public SessionContextResult trackChatAndGetContext(UUID userId, String sessionId, String userInput) {
        try {
            // Clean up old sessions first (sessions inactive for more than 24 hours)
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);
            sessionRepository.deactivateOldSessions(userId, cutoffTime);
            
            // Get or create session
            SessionMemory session = getOrCreateSession(userId, sessionId);
            
            // Increment chat count
            session.incrementChatCount();
            
            // Check if we should send calendar context
            boolean shouldSendContext = session.shouldSendContext();
            String calendarContext = "";
            
            if (shouldSendContext) {
                calendarContext = calendarContextService.generateCalendarEventsContext(userId, sessionId);
                session.markContextSent();
            }
            
            // Save session
            sessionRepository.save(session);
            
            return new SessionContextResult(
                session.getChatCount(),
                shouldSendContext,
                calendarContext,
                session.getSessionId()
            );
            
        } catch (Exception e) {
            System.err.println("Error tracking chat session: " + e.getMessage());
            e.printStackTrace();
            
            // Return minimal result on error
            return new SessionContextResult(1, false, "", sessionId);
        }
    }
    
    /**
     * Get or create a session for the user
     */
    @Transactional
    private SessionMemory getOrCreateSession(UUID userId, String sessionId) {
        // Try to find existing active session
        Optional<SessionMemory> existingSession = sessionRepository
            .findActiveSessionByUserAndSessionId(userId, sessionId);
            
        if (existingSession.isPresent()) {
            return existingSession.get();
        }
        
        // Create new session
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
            
        SessionMemory newSession = new SessionMemory(user, sessionId);
        return sessionRepository.save(newSession);
    }
    
    /**
     * Get session statistics for debugging
     */
    @Transactional(readOnly = true)
    public String getSessionStats(UUID userId) {
        try {
            long activeSessionCount = sessionRepository.countActiveSessionsByUser(userId);
            Optional<SessionMemory> recentSession = sessionRepository.findMostRecentActiveSession(userId);
            
            StringBuilder stats = new StringBuilder();
            stats.append("Session Statistics for user ").append(userId.toString().substring(0, 8)).append(":\n");
            stats.append("Active sessions: ").append(activeSessionCount).append("\n");
            
            if (recentSession.isPresent()) {
                SessionMemory session = recentSession.get();
                stats.append("Most recent session: ").append(session.getSessionId()).append("\n");
                stats.append("Chat count: ").append(session.getChatCount()).append("\n");
                stats.append("Last activity: ").append(session.getLastActivity()).append("\n");
                stats.append("Should send context: ").append(session.shouldSendContext()).append("\n");
            } else {
                stats.append("No active sessions found\n");
            }
            
            return stats.toString();
        } catch (Exception e) {
            return "Error getting session stats: " + e.getMessage();
        }
    }
    
    /**
     * Force context send for next message (for testing)
     */
    @Transactional
    public void forceContextOnNextMessage(UUID userId, String sessionId) {
        Optional<SessionMemory> session = sessionRepository
            .findActiveSessionByUserAndSessionId(userId, sessionId);
            
        if (session.isPresent()) {
            SessionMemory s = session.get();
            // Set chat count to a multiple of 10 minus 1, so next increment triggers context
            s.setChatCount(9);
            sessionRepository.save(s);
        }
    }
    
    /**
     * Result class for session context operations
     */
    public static class SessionContextResult {
        private final int chatCount;
        private final boolean shouldSendContext;
        private final String calendarContext;
        private final String sessionId;
        
        public SessionContextResult(int chatCount, boolean shouldSendContext, 
                                  String calendarContext, String sessionId) {
            this.chatCount = chatCount;
            this.shouldSendContext = shouldSendContext;
            this.calendarContext = calendarContext;
            this.sessionId = sessionId;
        }
        
        public int getChatCount() { return chatCount; }
        public boolean shouldSendContext() { return shouldSendContext; }
        public String getCalendarContext() { return calendarContext; }
        public String getSessionId() { return sessionId; }
        
        @Override
        public String toString() {
            return String.format("SessionContextResult{chatCount=%d, shouldSend=%s, hasContext=%s, sessionId=%s}", 
                chatCount, shouldSendContext, !calendarContext.isEmpty(), sessionId);
        }
    }
}
