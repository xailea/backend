package it.axel.nomicosecitta.dto;

import jakarta.validation.constraints.NotNull;

public record FinishRoomRequest(@NotNull Long playerId) {}
