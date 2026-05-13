package it.axel.nomicosecitta.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "categories")
public class GameCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Room room;

    @Column(nullable = false, length = 80)
    private String name;

    public Long getId() { return id; }
    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
