package it.axel.nomicosecitta.dto;

import jakarta.validation.constraints.NotNull;

public record StartRoundRequest(@NotNull Long playerId) {}
