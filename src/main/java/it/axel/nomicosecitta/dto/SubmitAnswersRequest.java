package it.axel.nomicosecitta.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record SubmitAnswersRequest(@NotNull Long playerId, Map<String, String> answers) {}
