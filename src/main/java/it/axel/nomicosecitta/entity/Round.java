package it.axel.nomicosecitta.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "rounds")
public class Round {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Room room;

    @Column(nullable = false, length = 1)
    private String letter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoundStatus status = RoundStatus.IN_PROGRESS;

    @Column(nullable = false)
    private Instant startedAt = Instant.now();

    private Instant endedAt;

    public Long getId() { return id; }
    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }
    public String getLetter() { return letter; }
    public void setLetter(String letter) { this.letter = letter; }
    public RoundStatus getStatus() { return status; }
    public void setStatus(RoundStatus status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
}
