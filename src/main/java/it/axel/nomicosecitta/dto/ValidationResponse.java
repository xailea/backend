package it.axel.nomicosecitta.dto;

public record ValidationResponse(
        Long id,
        Long roundId,
        String category,
        Long answerId,
        Long answerPlayerId,
        String answerPlayerName,
        String answer,
        Long validatorPlayerId,
        String validatorPlayerName,
        boolean valid
) {}
