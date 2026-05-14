package it.axel.nomicosecitta.dto;

public record AnswerResponse(
        Long id,
        Long playerId,
        String playerName,
        String category,
        String answer,
        int points
) {}
