package it.axel.nomicosecitta.dto;

import jakarta.validation.constraints.NotNull;

public record ValidationReadyRequest(@NotNull Long playerId) {}
