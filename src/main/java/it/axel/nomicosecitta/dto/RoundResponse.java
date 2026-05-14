package it.axel.nomicosecitta.dto;

import java.util.List;

public record RoundResponse(
        Long id,
        String letter,
        String status,
        int seconds,
        List<AnswerResponse> answers,
        List<ValidationResponse> validations,
        boolean validationsComplete,
        List<Long> readyPlayerIds
) {}
