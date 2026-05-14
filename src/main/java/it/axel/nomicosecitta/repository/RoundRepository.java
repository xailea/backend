package it.axel.nomicosecitta.repository;

import it.axel.nomicosecitta.entity.Round;
import it.axel.nomicosecitta.entity.Room;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoundRepository extends JpaRepository<Round, Long> {
    Optional<Round> findFirstByRoomOrderByStartedAtDesc(Room room);
    List<Round> findByRoom(Room room);
}
