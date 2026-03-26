package tn.esprit.usermanagementservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.usermanagementservice.entity.UserProfile;
import tn.esprit.usermanagementservice.repository.UserProfileRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users/stats")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class StatsController {

    private final UserProfileRepository userProfileRepository;

    @GetMapping("/dashboard")
    // @PreAuthorize("hasRole('ADMIN')")  // Commenté pour les tests
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        List<UserProfile> allUsers = userProfileRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> stats = new HashMap<>();

        // 1. KPIs de base
        stats.put("totalUsers", allUsers.size());

        // 2. Utilisateurs actifs
        stats.put("activeUsersLastDay", countActiveUsersSince(now.minusDays(1), allUsers));
        stats.put("activeUsersLastWeek", countActiveUsersSince(now.minusDays(7), allUsers));
        stats.put("activeUsersLastMonth", countActiveUsersSince(now.minusDays(30), allUsers));

        // 3. Nouveaux utilisateurs
        stats.put("newUsersToday", countNewUsersSince(now.toLocalDate().atStartOfDay(), allUsers));
        stats.put("newUsersThisWeek", countNewUsersSince(now.minusDays(7), allUsers));
        stats.put("newUsersThisMonth", countNewUsersSince(now.minusDays(30), allUsers));

        // 4. Répartition par rôle
        Map<String, Long> usersByRole = allUsers.stream()
                .filter(u -> u.getRole() != null)
                .collect(Collectors.groupingBy(
                        u -> u.getRole().toString(),
                        Collectors.counting()
                ));
        stats.put("usersByRole", usersByRole);

        // 5. Répartition par ville
        Map<String, Long> usersByCity = allUsers.stream()
                .filter(u -> u.getCity() != null && !u.getCity().isEmpty())
                .collect(Collectors.groupingBy(
                        UserProfile::getCity,
                        Collectors.counting()
                ));
        stats.put("usersByCity", usersByCity);

        // 6. Connexions par jour (30 derniers jours)
        stats.put("loginsPerDay", getLoginsPerDay(allUsers, 30));

        // 7. Connexions par heure
        stats.put("loginsPerHour", getLoginsPerHour(allUsers));

        // 8. Top 10 des plus actifs
        stats.put("mostActiveUsers", getMostActiveUsers(allUsers, 10));

        // 9. 10 dernières connexions
        stats.put("recentLogins", getRecentLogins(allUsers, 10));

        // 10. Taux de rétention
        stats.put("retentionRate", calculateRetentionRate(allUsers));

        return ResponseEntity.ok(stats);
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

        for (int i = days; i >= 0; i--) {
            loginsPerDay.put(today.minusDays(i), 0L);
        }

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

    private List<Map<String, Object>> getMostActiveUsers(List<UserProfile> users, int limit) {
        return users.stream()
                .filter(u -> u.getLoginCount() != null && u.getLoginCount() > 0)
                .sorted((a, b) -> Integer.compare(b.getLoginCount(), a.getLoginCount()))
                .limit(limit)
                .map(u -> {
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("id", u.getId());
                    userMap.put("email", u.getEmail());
                    userMap.put("firstName", u.getFirstName());
                    userMap.put("lastName", u.getLastName());
                    userMap.put("role", u.getRole() != null ? u.getRole().toString() : null);
                    userMap.put("city", u.getCity());
                    userMap.put("lastLogin", u.getLastLoginAt());
                    userMap.put("loginCount", u.getLoginCount());
                    return userMap;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> getRecentLogins(List<UserProfile> users, int limit) {
        return users.stream()
                .filter(u -> u.getLastLoginAt() != null)
                .sorted((a, b) -> b.getLastLoginAt().compareTo(a.getLastLoginAt()))
                .limit(limit)
                .map(u -> {
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("id", u.getId());
                    userMap.put("email", u.getEmail());
                    userMap.put("firstName", u.getFirstName());
                    userMap.put("lastName", u.getLastName());
                    userMap.put("lastLogin", u.getLastLoginAt());
                    userMap.put("loginCount", u.getLoginCount());
                    return userMap;
                })
                .collect(Collectors.toList());
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
    // ========== STATISTIQUES AVANCÉES ==========

    // 1. HEATMAP des connexions (jour/heure)
    // 1. HEATMAP des connexions (jour/heure)
    @GetMapping("/heatmap")
    public ResponseEntity<Map<String, Object>> getLoginHeatmap() {
        List<UserProfile> users = userProfileRepository.findAll();
        Map<String, Object> heatmap = new HashMap<>();

        // Matrice 24h x 7j
        int[][] heatmapData = new int[24][7];

        // Utilise un tableau à une case pour contourner le problème de final
        int[] maxValue = {0};

        users.stream()
                .filter(u -> u.getLastLoginAt() != null)
                .forEach(u -> {
                    int hour = u.getLastLoginAt().getHour();
                    int day = u.getLastLoginAt().getDayOfWeek().getValue() - 1; // 0 = Lundi
                    heatmapData[hour][day]++;
                    if (heatmapData[hour][day] > maxValue[0]) {
                        maxValue[0] = heatmapData[hour][day];
                    }
                });

        heatmap.put("matrix", heatmapData);
        heatmap.put("maxValue", maxValue[0]);
        heatmap.put("days", List.of("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"));

        List<String> hours = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            hours.add(String.format("%02dh", i));
        }
        heatmap.put("hours", hours);

        return ResponseEntity.ok(heatmap);
    }
    // 2. ACTIVITÉ QUOTIDIENNE (30 derniers jours)
    @GetMapping("/daily-activity")
    public ResponseEntity<List<Map<String, Object>>> getDailyActivity() {
        List<UserProfile> users = userProfileRepository.findAll();
        LocalDate today = LocalDate.now();

        List<Map<String, Object>> dailyActivity = new ArrayList<>();

        for (int i = 29; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();

            long logins = users.stream()
                    .filter(u -> u.getLastLoginAt() != null
                            && u.getLastLoginAt().isAfter(start)
                            && u.getLastLoginAt().isBefore(end))
                    .count();

            long newUsers = users.stream()
                    .filter(u -> u.getCreatedAt() != null
                            && u.getCreatedAt().isAfter(start)
                            && u.getCreatedAt().isBefore(end))
                    .count();

            Map<String, Object> dayStats = new HashMap<>();
            dayStats.put("date", date.toString());
            dayStats.put("logins", logins);
            dayStats.put("newUsers", newUsers);
            dayStats.put("dayName", date.getDayOfWeek().toString());

            dailyActivity.add(dayStats);
        }

        return ResponseEntity.ok(dailyActivity);
    }


    // 3. TOP ACTIVITÉ PAR JOUR DE LA SEMAINE
    @GetMapping("/weekday-activity")
    public ResponseEntity<Map<String, Object>> getWeekdayActivity() {
        List<UserProfile> users = userProfileRepository.findAll();

        Map<String, Long> loginsByWeekday = new HashMap<>();
        Map<String, Long> usersByWeekday = new HashMap<>();

        String[] days = {"LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI", "SAMEDI", "DIMANCHE"};

        for (String day : days) {
            loginsByWeekday.put(day, 0L);
            usersByWeekday.put(day, 0L);
        }

        users.stream()
                .filter(u -> u.getLastLoginAt() != null)
                .forEach(u -> {
                    String day = u.getLastLoginAt().getDayOfWeek().toString();
                    loginsByWeekday.merge(day, 1L, Long::sum);
                });

        users.stream()
                .filter(u -> u.getCreatedAt() != null)
                .forEach(u -> {
                    String day = u.getCreatedAt().getDayOfWeek().toString();
                    usersByWeekday.merge(day, 1L, Long::sum);
                });

        Map<String, Object> result = new HashMap<>();
        result.put("loginsByWeekday", loginsByWeekday);
        result.put("usersByWeekday", usersByWeekday);

        return ResponseEntity.ok(result);
    }

    // 4. ACTIVITÉ DES 7 DERNIERS JOURS (pour le grand tableau)
    @GetMapping("/recent-activity")
    public ResponseEntity<List<Map<String, Object>>> getRecentActivity() {
        List<UserProfile> users = userProfileRepository.findAll();
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);

        List<Map<String, Object>> recentActivity = users.stream()
                .filter(u -> u.getLastLoginAt() != null && u.getLastLoginAt().isAfter(weekAgo))
                .map(u -> {
                    Map<String, Object> activity = new HashMap<>();
                    activity.put("userId", u.getId());
                    activity.put("email", u.getEmail());
                    activity.put("firstName", u.getFirstName());
                    activity.put("lastName", u.getLastName());
                    activity.put("role", u.getRole());
                    activity.put("city", u.getCity());
                    activity.put("lastLogin", u.getLastLoginAt());
                    activity.put("loginCount", u.getLoginCount());
                    activity.put("accountAge", ChronoUnit.DAYS.between(u.getCreatedAt(), LocalDateTime.now()));
                    return activity;
                })
                .sorted((a, b) -> ((LocalDateTime) b.get("lastLogin")).compareTo((LocalDateTime) a.get("lastLogin")))
                .collect(Collectors.toList());

        return ResponseEntity.ok(recentActivity);
    }
    @GetMapping("/users-heatmap")
    public ResponseEntity<Map<String, Object>> getUsersHeatmap() {
        List<UserProfile> users = userProfileRepository.findAll();

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> usersData = new ArrayList<>();

        for (UserProfile user : users) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("userId", user.getId());
            userData.put("email", user.getEmail());
            userData.put("firstName", user.getFirstName());
            userData.put("lastName", user.getLastName());
            userData.put("role", user.getRole());
            userData.put("city", user.getCity());
            userData.put("loginCount", user.getLoginCount());

            // Créer un tableau de 7 jours (0-6) avec le nombre de connexions
            int[] weeklyActivity = new int[7];
            if (user.getLastLoginAt() != null) {
                int dayOfWeek = user.getLastLoginAt().getDayOfWeek().getValue() - 1;
                weeklyActivity[dayOfWeek] = 1; // ou le nombre de connexions si tu as l'historique
            }
            userData.put("weeklyActivity", weeklyActivity);

            // Dernière connexion formatée
            userData.put("lastLogin", user.getLastLoginAt() != null ?
                    user.getLastLoginAt().toString() : null);

            // Âge du compte
            if (user.getCreatedAt() != null) {
                long daysActive = ChronoUnit.DAYS.between(user.getCreatedAt(), LocalDateTime.now());
                userData.put("accountAge", daysActive);
            } else {
                userData.put("accountAge", 0);
            }

            usersData.add(userData);
        }

        result.put("users", usersData);
        result.put("totalUsers", users.size());
        result.put("days", List.of("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"));

        return ResponseEntity.ok(result);
    }

    // 5. STATISTIQUES GÉNÉRALES AMÉLIORÉES
    @GetMapping("/advanced-stats")
    public ResponseEntity<Map<String, Object>> getAdvancedStats() {
        List<UserProfile> users = userProfileRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> stats = new HashMap<>();

        // Moyennes
        stats.put("avgLoginsPerUser", users.stream()
                .filter(u -> u.getLoginCount() != null)
                .mapToInt(UserProfile::getLoginCount)
                .average()
                .orElse(0));

        stats.put("totalLogins", users.stream()
                .mapToInt(u -> u.getLoginCount() != null ? u.getLoginCount() : 0)
                .sum());

        stats.put("activeUsersToday", countActiveUsersSince(now.minusDays(1), users));
        stats.put("activeUsersThisHour", countActiveUsersSince(now.minusHours(1), users));

        // Utilisateurs sans activité
        stats.put("inactiveUsers", users.stream()
                .filter(u -> u.getLastLoginAt() == null || u.getLastLoginAt().isBefore(now.minusMonths(1)))
                .count());

        // Répartition par ville
        Map<String, Long> usersByCity = users.stream()
                .filter(u -> u.getCity() != null && !u.getCity().isEmpty())
                .collect(Collectors.groupingBy(
                        UserProfile::getCity,
                        TreeMap::new,
                        Collectors.counting()
                ));
        stats.put("usersByCity", usersByCity);

        return ResponseEntity.ok(stats);
    }
}