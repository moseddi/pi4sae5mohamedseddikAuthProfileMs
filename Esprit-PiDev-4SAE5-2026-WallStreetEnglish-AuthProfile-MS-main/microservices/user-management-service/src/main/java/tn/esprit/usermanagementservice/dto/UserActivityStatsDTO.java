package tn.esprit.usermanagementservice.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class UserActivityStatsDTO {
    // KPIs principaux
    private long totalUsers;
    private long activeUsersLastDay;
    private long activeUsersLastWeek;
    private long activeUsersLastMonth;
    private long newUsersToday;
    private long newUsersThisWeek;
    private long newUsersThisMonth;

    // Graphiques
    private Map<LocalDate, Long> loginsPerDay;      // Pour histogramme
    private Map<Integer, Long> loginsPerHour;       // Pour heures de pointe
    private Map<String, Long> usersByRole;          // Répartition rôles
    private Map<String, Long> usersByCity;          // Répartition géographique

    // Top users
    private List<UserActivityDTO> mostActiveUsers;
    private List<UserActivityDTO> recentLogins;

    // Taux de rétention
    private double retentionRate;
    private double averageLoginsPerUser;
}