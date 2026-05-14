package it.axel.nomicosecitta.dto;

import java.util.List;

public record RoomResponse(
        String roomCode,
        String status,
        Long playerId,
        boolean host,
        List<String> categories,
        List<PlayerResponse> players,
        RoundResponse currentRound,
        Long winnerPlayerId,
        boolean draw
) {}
