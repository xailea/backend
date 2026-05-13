package it.axel.nomicosecitta.dto;

import jakarta.validation.constraints.NotNull;

public record CancelRoomRequest(@NotNull Long playerId) {}
