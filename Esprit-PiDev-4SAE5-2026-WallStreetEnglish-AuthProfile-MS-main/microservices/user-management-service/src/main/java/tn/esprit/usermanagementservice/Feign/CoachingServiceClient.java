package tn.esprit.usermanagementservice.Feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import java.util.List;

@FeignClient(name = "Coaching-service", fallback = CoachingServiceFallback.class)
public interface CoachingServiceClient {

    @GetMapping("/Coaching-service/api/reservations/seance/{seanceId}")
    List<Object> getReservationsBySeance(
            @PathVariable("seanceId") int seanceId,
            @RequestHeader("Authorization") String authorizationHeader);

    @GetMapping("/Coaching-service/api/seances/{id}")
    Object getSeanceById(
            @PathVariable("id") int id,
            @RequestHeader("Authorization") String authorizationHeader);
}