package tn.esprit.usermanagementservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.usermanagementservice.entity.LoginHistory;
import tn.esprit.usermanagementservice.entity.UserProfile;
import tn.esprit.usermanagementservice.repository.LoginHistoryRepository;
import tn.esprit.usermanagementservice.repository.UserProfileRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LoginHistoryService {

    private final LoginHistoryRepository loginHistoryRepository;
    private final UserProfileRepository userProfileRepository;

    public List<Map<String, Object>> getRecentLogins() {
        List<LoginHistory> recentLogins = loginHistoryRepository.findTop20ByOrderByLoginTimeDesc();

        List<Map<String, Object>> result = new ArrayList<>();
        for (LoginHistory history : recentLogins) {
            Map<String, Object> loginData = new HashMap<>();
            loginData.put("email", history.getEmail());
            loginData.put("message", history.getMessage());
            loginData.put("loginTime", history.getLoginTime() != null ? history.getLoginTime().toString() : "");
            loginData.put("suspicious", history.isSuspicious());
            loginData.put("suspiciousReason", history.getSuspiciousReason());
            loginData.put("browser", history.getBrowser());
            loginData.put("os", history.getOs());
            loginData.put("device", history.getDeviceType());

            UserProfile user = userProfileRepository.findByEmail(history.getEmail()).orElse(null);
            loginData.put("role", user != null ? user.getRole().toString() : "STUDENT");
            loginData.put("firstName", user != null ? user.getFirstName() : "");
            loginData.put("lastName", user != null ? user.getLastName() : "");

            result.add(loginData);
        }

        return result;
    }
}