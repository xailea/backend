package it.axel.nomicosecitta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateRoomRequest(
        @NotBlank String playerName,
        @Size(min = 2, message = "Inserisci almeno due categorie") List<@NotBlank String> categories
) {}
