package it.axel.nomicosecitta.repository;

import it.axel.nomicosecitta.entity.Player;
import it.axel.nomicosecitta.entity.Round;
import it.axel.nomicosecitta.entity.RoundValidationReady;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoundValidationReadyRepository extends JpaRepository<RoundValidationReady, Long> {
    List<RoundValidationReady> findByRound(Round round);
    Optional<RoundValidationReady> findByRoundAndPlayer(Round round, Player player);
    long countByRound(Round round);
}
