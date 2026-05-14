package it.axel.nomicosecitta.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SubmitValidationsRequest(
        @NotNull Long playerId,
        Long targetPlayerId,
        @NotEmpty List<@Valid AnswerValidationItemRequest> validations
) {
    public Long validatorPlayerId() {
        return playerId;
    }
}
