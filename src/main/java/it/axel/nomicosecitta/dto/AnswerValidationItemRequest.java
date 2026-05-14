package it.axel.nomicosecitta.dto;

import jakarta.validation.constraints.NotNull;

public record AnswerValidationItemRequest(
        @NotNull Long answerId,
        @NotNull Boolean valid
) {}
