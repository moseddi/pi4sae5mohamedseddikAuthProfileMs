package tn.esprit.usermanagementservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.esprit.usermanagementservice.dto.UserActivityDTO;
import tn.esprit.usermanagementservice.dto.UserActivityStatsDTO;
import tn.esprit.usermanagementservice.entity.UserProfile;
import tn.esprit.usermanagementservice.repository.UserProfileRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatsService {

    private final UserProfileRepository userRepository;

    public UserActivityStatsDTO getActivityStats() {
        List<UserProfile> allUsers = userRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        UserActivityStatsDTO stats = new UserActivityStatsDTO();

        // 1. KPIs de base
        stats.setTotalUsers(allUsers.size());

        // 2. Utilisateurs actifs
        stats.setActiveUsersLastDay(countActiveUsersSince(now.minusDays(1), allUsers));
        stats.setActiveUsersLastWeek(countActiveUsersSince(now.minusDays(7), allUsers));
        stats.setActiveUsersLastMonth(countActiveUsersSince(now.minusDays(30), allUsers));

        // 3. Nouveaux utilisateurs
        stats.setNewUsersToday(countNewUsersSince(now.toLocalDate().atStartOfDay(), allUsers));
        stats.setNewUsersThisWeek(countNewUsersSince(now.minusDays(7), allUsers));
        stats.setNewUsersThisMonth(countNewUsersSince(now.minusDays(30), allUsers));

        // 4. Répartition par rôle
        stats.setUsersByRole(allUsers.stream()
                .collect(Collectors.groupingBy(
                        u -> u.getRole() != null ? u.getRole().toString() : "UNKNOWN",
                        Collectors.counting()
                )));

        // 5. Répartition par ville
        stats.setUsersByCity(allUsers.stream()
                .filter(u -> u.getCity() != null && !u.getCity().isEmpty())
                .collect(Collectors.groupingBy(
                        UserProfile::getCity,
                        Collectors.counting()
                )));

        // 6. Connexions par jour (30 derniers jours)
        stats.setLoginsPerDay(getLoginsPerDay(allUsers, 30));

        // 7. Connexions par heure (pour heures de pointe)
        stats.setLoginsPerHour(getLoginsPerHour(allUsers));

        // 8. Top 10 des plus actifs
        stats.setMostActiveUsers(getMostActiveUsers(allUsers, 10));

        // 9. 10 dernières connexions
        stats.setRecentLogins(getRecentLogins(allUsers, 10));

        // 10. Taux de rétention et moyennes
        stats.setRetentionRate(calculateRetentionRate(allUsers));
        stats.setAverageLoginsPerUser(calculateAverageLogins(allUsers));

        return stats;
    }

    private long countActiveUsersSince(LocalDateTime since, List<UserProfile> users) {
        return users.stream()
                .filter(u -> u.getLastActivityAt() != null && u.getLastActivityAt().isAfter(since))
                .count();
    }

    private long countNewUsersSince(LocalDateTime since, List<UserProfile> users) {
        return users.stream()
                .filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isAfter(since))
                .count();
    }

    private Map<LocalDate, Long> getLoginsPerDay(List<UserProfile> users, int days) {
        Map<LocalDate, Long> loginsPerDay = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();

        // Initialise tous les jours
        for (int i = days; i >= 0; i--) {
            loginsPerDay.put(today.minusDays(i), 0L);
        }

        // Compte les connexions
        users.stream()
                .filter(u -> u.getLastLoginAt() != null)
                .forEach(u -> {
                    LocalDate loginDate = u.getLastLoginAt().toLocalDate();
                    if (!loginDate.isBefore(today.minusDays(days))) {
                        loginsPerDay.merge(loginDate, 1L, Long::sum);
                    }
                });

        return loginsPerDay;
    }

    private Map<Integer, Long> getLoginsPerHour(List<UserProfile> users) {
        Map<Integer, Long> loginsPerHour = new HashMap<>();

        // Initialise les 24 heures
        for (int i = 0; i < 24; i++) {
            loginsPerHour.put(i, 0L);
        }

        users.stream()
                .filter(u -> u.getLastLoginAt() != null)
                .forEach(u -> {
                    int hour = u.getLastLoginAt().getHour();
                    loginsPerHour.merge(hour, 1L, Long::sum);
                });

        return loginsPerHour;
    }

    private List<UserActivityDTO> getMostActiveUsers(List<UserProfile> users, int limit) {
        return users.stream()
                .filter(u -> u.getLoginCount() != null && u.getLoginCount() > 0)
                .map(this::convertToActivityDTO)
                .sorted((a, b) -> Integer.compare(b.getLoginCount(), a.getLoginCount()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private List<UserActivityDTO> getRecentLogins(List<UserProfile> users, int limit) {
        return users.stream()
                .filter(u -> u.getLastLoginAt() != null)
                .map(this::convertToActivityDTO)
                .sorted((a, b) -> b.getLastLogin().compareTo(a.getLastLogin()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private UserActivityDTO convertToActivityDTO(UserProfile user) {
        UserActivityDTO dto = new UserActivityDTO();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setRole(user.getRole() != null ? user.getRole().toString() : null);
        dto.setCity(user.getCity());
        dto.setLastLogin(user.getLastLoginAt());
        dto.setLastActivity(user.getLastActivityAt());
        dto.setLoginCount(user.getLoginCount() != null ? user.getLoginCount() : 0);

        if (user.getLastLoginAt() != null) {
            dto.setDaysSinceLastLogin(ChronoUnit.DAYS.between(user.getLastLoginAt(), LocalDateTime.now()));
        }

        if (user.getLoginCount() != null && user.getLoginCount() > 0 && user.getCreatedAt() != null) {
            long months = ChronoUnit.MONTHS.between(user.getCreatedAt(), LocalDateTime.now()) + 1;
            dto.setAverageLoginsPerMonth((double) user.getLoginCount() / months);
        }

        return dto;
    }

    private double calculateRetentionRate(List<UserProfile> users) {
        long totalWithLogin = users.stream()
                .filter(u -> u.getLoginCount() != null && u.getLoginCount() > 0)
                .count();

        long returnedUsers = users.stream()
                .filter(u -> u.getLoginCount() != null && u.getLoginCount() > 1)
                .count();

        return totalWithLogin > 0 ? (double) returnedUsers / totalWithLogin * 100 : 0;
    }

    private double calculateAverageLogins(List<UserProfile> users) {
        return users.stream()
                .filter(u -> u.getLoginCount() != null)
                .mapToInt(UserProfile::getLoginCount)
                .average()
                .orElse(0);
    }

    // Stats pour un utilisateur spécifique
    public UserActivityDTO getUserActivityStats(Long userId) {
        UserProfile user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return convertToActivityDTO(user);
    }
}