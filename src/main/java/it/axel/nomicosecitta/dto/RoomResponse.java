package it.axel.nomicosecitta.dto;

import java.util.List;
import java.util.Map;

public record RoomResponse(
        String roomCode,
        String status,
        Long playerId,
        boolean host,
        List<String> categories,
        List<PlayerResponse> players,
        RoundResponse currentRound,
        Map<Long, Map<String, String>> answers
) {}
