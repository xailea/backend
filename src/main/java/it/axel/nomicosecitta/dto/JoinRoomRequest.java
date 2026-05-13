package it.axel.nomicosecitta.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinRoomRequest(@NotBlank String playerName) {}
