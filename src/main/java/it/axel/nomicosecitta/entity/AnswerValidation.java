package it.axel.nomicosecitta.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "answer_validations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"round_id", "answer_id", "validator_id"})
)
public class AnswerValidation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Round round;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private GameCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Answer answer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Player validator;

    @Column(nullable = false)
    private boolean valid;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Round getRound() { return round; }
    public void setRound(Round round) { this.round = round; }
    public GameCategory getCategory() { return category; }
    public void setCategory(GameCategory category) { this.category = category; }
    public Answer getAnswer() { return answer; }
    public void setAnswer(Answer answer) { this.answer = answer; }
    public Player getValidator() { return validator; }
    public void setValidator(Player validator) { this.validator = validator; }
    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
    public Instant getCreatedAt() { return createdAt; }
}
