package it.axel.nomicosecitta.repository;

import it.axel.nomicosecitta.entity.Answer;
import it.axel.nomicosecitta.entity.AnswerValidation;
import it.axel.nomicosecitta.entity.Player;
import it.axel.nomicosecitta.entity.Round;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerValidationRepository extends JpaRepository<AnswerValidation, Long> {
    List<AnswerValidation> findByRound(Round round);
    Optional<AnswerValidation> findByRoundAndAnswerAndValidator(Round round, Answer answer, Player validator);
    long countByRound(Round round);
}
