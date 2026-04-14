package tn.esprit.usermanagementservice.Feign;

import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.List;

@Component
public class CoachingServiceFallback implements CoachingServiceClient {

    @Override
    public List<Object> getReservationsBySeance(int seanceId, String authorizationHeader) {
        return Collections.emptyList();
    }

    @Override
    public Object getSeanceById(int id, String authorizationHeader) {
        return null;
    }
}