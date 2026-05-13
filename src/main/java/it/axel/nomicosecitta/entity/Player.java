package it.axel.nomicosecitta.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "players")
public class Player {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Room room;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private boolean host;

    @Column(nullable = false)
    private int totalPoints = 0;

    @Column(nullable = false)
    private Instant joinedAt = Instant.now();

    public Long getId() { return id; }
    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isHost() { return host; }
    public void setHost(boolean host) { this.host = host; }
    public int getTotalPoints() { return totalPoints; }
    public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }
    public Instant getJoinedAt() { return joinedAt; }
}
