package tn.esprit.usermanagementservice.repository;

import feign.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tn.esprit.usermanagementservice.entity.Role;
import tn.esprit.usermanagementservice.entity.UserProfile;

import java.awt.print.Pageable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByEmail(String email);
    boolean existsByEmail(String email);
    List<UserProfile> findByRole(Role role);
    List<UserProfile> findByActive(boolean active);
    @Query("SELECT u FROM UserProfile u WHERE u.lastLoginAt IS NOT NULL ORDER BY u.lastLoginAt DESC")
    List<UserProfile> findTopNByOrderByLastLoginAtDesc(Pageable pageable);

    @Query("SELECT COUNT(u) FROM UserProfile u WHERE u.lastLoginAt > :date")
    long countActiveUsersSince(@Param("date") LocalDateTime date);
}