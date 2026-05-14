package it.axel.nomicosecitta.repository;

import it.axel.nomicosecitta.entity.Answer;
import it.axel.nomicosecitta.entity.Round;
import it.axel.nomicosecitta.entity.GameCategory;
import it.axel.nomicosecitta.entity.Player;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
    List<Answer> findByRound(Round round);
    Optional<Answer> findByRoundAndPlayerAndCategory(Round round, Player player, GameCategory category);
}
