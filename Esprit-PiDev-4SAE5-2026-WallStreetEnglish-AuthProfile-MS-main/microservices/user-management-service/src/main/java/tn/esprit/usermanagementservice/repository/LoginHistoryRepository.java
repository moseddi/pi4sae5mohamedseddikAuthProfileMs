package tn.esprit.usermanagementservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprit.usermanagementservice.entity.LoginHistory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    // Get last 20 events (initial load)
    List<LoginHistory> findTop20ByOrderByLoginTimeDesc();

    // Get events from last 24 hours
    List<LoginHistory> findByLoginTimeAfterOrderByLoginTimeDesc(LocalDateTime after);

    // Get ALL events (login AND logout) from last 24 hours
    @Query("SELECT l FROM LoginHistory l WHERE " +
            "(l.type = 'LOGIN' AND l.loginTime >= :since) OR " +
            "(l.type = 'LOGOUT' AND l.logoutTime >= :since) " +
            "ORDER BY COALESCE(l.logoutTime, l.loginTime) DESC")
    List<LoginHistory> findAllEventsLast24h(@Param("since") LocalDateTime since);

    // Find open session for logout matching
    Optional<LoginHistory> findTopByEmailAndActiveOrderByLoginTimeDesc(String email, boolean active);

    // Count active sessions
    long countByActive(boolean active);

    // Count suspicious logins in last 24h
    long countBySuspiciousAndLoginTimeAfter(boolean suspicious, LocalDateTime after);

    // For suspicious detection
    List<LoginHistory> findByEmailOrderByLoginTimeDesc(String email);

    // Count recent logins for suspicious detection
    long countByEmailAndLoginTimeAfterAndType(String email, LocalDateTime after, LoginHistory.EventType type);

    // Get formatted messages only (for display)
    @Query("SELECT l.message FROM LoginHistory l ORDER BY COALESCE(l.logoutTime, l.loginTime) DESC LIMIT 50")
    List<String> findTop50Messages();


    List<LoginHistory> findByActiveTrueOrderByLoginTimeDesc();



}