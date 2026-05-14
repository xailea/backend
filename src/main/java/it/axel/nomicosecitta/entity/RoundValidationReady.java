package it.axel.nomicosecitta.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "round_validation_ready",
        uniqueConstraints = @UniqueConstraint(columnNames = {"round_id", "player_id"})
)
public class RoundValidationReady {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Round round;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Player player;

    @Column(nullable = false)
    private Instant readyAt = Instant.now();

    public Long getId() { return id; }
    public Round getRound() { return round; }
    public void setRound(Round round) { this.round = round; }
    public Player getPlayer() { return player; }
    public void setPlayer(Player player) { this.player = player; }
    public Instant getReadyAt() { return readyAt; }
}
