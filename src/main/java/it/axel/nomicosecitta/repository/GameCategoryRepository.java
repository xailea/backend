package it.axel.nomicosecitta.repository;

import it.axel.nomicosecitta.entity.GameCategory;
import it.axel.nomicosecitta.entity.Room;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameCategoryRepository extends JpaRepository<GameCategory, Long> {
    List<GameCategory> findByRoomOrderByIdAsc(Room room);
}
