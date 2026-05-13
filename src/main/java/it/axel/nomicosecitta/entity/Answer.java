package it.axel.nomicosecitta.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "answers", uniqueConstraints = @UniqueConstraint(columnNames = {"round_id", "player_id", "category_id"}))
public class Answer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Round round;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Player player;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private GameCategory category;

    @Column(nullable = false, length = 120)
    private String answer;

    @Column(nullable = false)
    private int points = 0;

    public Long getId() { return id; }
    public Round getRound() { return round; }
    public void setRound(Round round) { this.round = round; }
    public Player getPlayer() { return player; }
    public void setPlayer(Player player) { this.player = player; }
    public GameCategory getCategory() { return category; }
    public void setCategory(GameCategory category) { this.category = category; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
}
