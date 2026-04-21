package tn.esprit.usermanagementservice.repository;

import feign.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tn.esprit.usermanagementservice.entity.ProfileChangeHistory;

import java.time.LocalDateTime;
import java.util.List;

public interface ProfileChangeHistoryRepository extends JpaRepository<ProfileChangeHistory, Long> {

    @Query("SELECT COUNT(p) FROM ProfileChangeHistory p " +
            "WHERE p.email = :email " +
            "AND p.fieldChanged = 'country' " +
            "AND p.changedAt >= :startOfMonth")
    int countCountryChangesThisMonth(String email, LocalDateTime startOfMonth);

    List<ProfileChangeHistory> findByEmailOrderByChangedAtDesc(String email);


    @Query("SELECT COUNT(p) FROM ProfileChangeHistory p " +
            "WHERE p.email = :email " +
            "AND p.fieldChanged = 'country' " +
            "AND p.changedAt >= :startOfDay")
    int countCountryChangesToday(@Param("email") String email, @Param("startOfDay") LocalDateTime startOfDay);
}