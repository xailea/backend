package it.axel.nomicosecitta.controller;

import it.axel.nomicosecitta.dto.*;
import it.axel.nomicosecitta.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RoomController {
    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse createRoom(@Valid @RequestBody CreateRoomRequest request) {
        return roomService.createRoom(request);
    }

    @PostMapping("/rooms/{roomCode}/join")
    public RoomResponse joinRoom(@PathVariable String roomCode, @Valid @RequestBody JoinRoomRequest request) {
        return roomService.joinRoom(roomCode, request);
    }

    @GetMapping("/rooms/{roomCode}")
    public RoomResponse getRoom(@PathVariable String roomCode) {
        return roomService.getRoom(roomCode);
    }

    @PostMapping("/rooms/{roomCode}/rounds")
    @ResponseStatus(HttpStatus.CREATED)
    public RoundResponse startRound(@PathVariable String roomCode, @Valid @RequestBody StartRoundRequest request) {
        return roomService.startRound(roomCode, request);
    }

    @PostMapping("/rounds/{roundId}/answers")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submitAnswers(@PathVariable Long roundId, @Valid @RequestBody SubmitAnswersRequest request) {
        roomService.submitAnswers(roundId, request);
    }

    @PostMapping("/rounds/{roundId}/validations")
    public RoundResponse submitValidations(@PathVariable Long roundId, @Valid @RequestBody SubmitValidationsRequest request) {
        return roomService.submitValidations(roundId, request);
    }

    @PostMapping("/rounds/{roundId}/validations/ready")
    public RoundResponse markValidationReady(@PathVariable Long roundId, @Valid @RequestBody ValidationReadyRequest request) {
        return roomService.markValidationReady(roundId, request);
    }

    @PostMapping("/rooms/{roomCode}/cancel")
    public RoomResponse cancelRoom(@PathVariable String roomCode, @Valid @RequestBody CancelRoomRequest request) {
        return roomService.cancelRoom(roomCode, request);
    }

    @PostMapping("/rooms/{roomCode}/finish")
    public RoomResponse finishRoom(@PathVariable String roomCode, @Valid @RequestBody FinishRoomRequest request) {
        return roomService.finishRoom(roomCode, request);
    }

    @PostMapping("/rounds/{roundId}/end")
    public RoundResponse endRound(@PathVariable Long roundId) {
        return roomService.endRound(roundId);
    }
}
