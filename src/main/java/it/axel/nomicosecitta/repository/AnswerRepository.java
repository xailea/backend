package it.axel.nomicosecitta.repository;

import it.axel.nomicosecitta.entity.Answer;
import it.axel.nomicosecitta.entity.Player;
import it.axel.nomicosecitta.entity.Round;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
    List<Answer> findByRound(Round round);
    List<Answer> findByRoundAndPlayer(Round round, Player player);
}
