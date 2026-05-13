package it.axel.nomicosecitta.repository;

import it.axel.nomicosecitta.entity.Player;
import it.axel.nomicosecitta.entity.Room;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {
    List<Player> findByRoomOrderByJoinedAtAsc(Room room);
}
