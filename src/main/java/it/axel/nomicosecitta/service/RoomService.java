package it.axel.nomicosecitta.service;

import it.axel.nomicosecitta.dto.*;
import it.axel.nomicosecitta.entity.*;
import it.axel.nomicosecitta.repository.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.*;

@Service
public class RoomService {
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int ROUND_SECONDS = 120;

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final GameCategoryRepository categoryRepository;
    private final RoundRepository roundRepository;
    private final AnswerRepository answerRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public RoomService(
            RoomRepository roomRepository,
            PlayerRepository playerRepository,
            GameCategoryRepository categoryRepository,
            RoundRepository roundRepository,
            AnswerRepository answerRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.categoryRepository = categoryRepository;
        this.roundRepository = roundRepository;
        this.answerRepository = answerRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {
        Room room = new Room();
        room.setRoomCode(generateUniqueRoomCode());
        room.setStatus(RoomStatus.WAITING);
        roomRepository.save(room);

        Player host = new Player();
        host.setRoom(room);
        host.setName(clean(request.playerName()));
        host.setHost(true);
        playerRepository.save(host);

        List<String> cleanCategories = request.categories().stream()
                .map(this::clean)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();

        if (cleanCategories.size() < 2) {
            throw new ResponseStatusException(BAD_REQUEST, "Inserisci almeno due categorie valide");
        }

        for (String categoryName : cleanCategories) {
            GameCategory category = new GameCategory();
            category.setRoom(room);
            category.setName(categoryName);
            categoryRepository.save(category);
        }

        RoomResponse response = toRoomResponse(room, host.getId(), true);
        publish(room.getRoomCode(), "ROOM_CREATED", response);
        return response;
    }

    @Transactional
    public RoomResponse joinRoom(String roomCode, JoinRoomRequest request) {
        Room room = findRoom(roomCode);

        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new ResponseStatusException(BAD_REQUEST, "La stanza è chiusa");
        }

        if (playerRepository.findByRoomOrderByJoinedAtAsc(room).size() >= 2) {
            throw new ResponseStatusException(BAD_REQUEST, "Questa stanza ha gia due giocatori");
        }

        Player player = new Player();
        player.setRoom(room);
        player.setName(clean(request.playerName()));
        player.setHost(false);
        playerRepository.save(player);

        RoomResponse response = toRoomResponse(room, player.getId(), false);
        publish(room.getRoomCode(), "PLAYER_JOINED", response);
        return response;
    }

    @Transactional(readOnly = true)
    public RoomResponse getRoom(String roomCode) {
        Room room = findRoom(roomCode);
        return toRoomResponse(room, null, false);
    }

    @Transactional
    public RoundResponse startRound(String roomCode, StartRoundRequest request) {
        Room room = findRoom(roomCode);
        Player player = playerRepository.findById(request.playerId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Giocatore non trovato"));

        if (!Objects.equals(player.getRoom().getId(), room.getId()) || !player.isHost()) {
            throw new ResponseStatusException(FORBIDDEN, "Solo il creatore della stanza può iniziare la manche");
        }

        if (playerRepository.findByRoomOrderByJoinedAtAsc(room).size() < 2) {
            throw new ResponseStatusException(BAD_REQUEST, "Attendi il secondo giocatore");
        }

        Round round = new Round();
        round.setRoom(room);
        round.setLetter(randomLetter());
        round.setStatus(RoundStatus.IN_PROGRESS);
        roundRepository.save(round);

        room.setStatus(RoomStatus.IN_PROGRESS);
        roomRepository.save(room);

        RoundResponse response = new RoundResponse(round.getId(), round.getLetter(), round.getStatus().name(), ROUND_SECONDS);
        publish(room.getRoomCode(), "ROUND_STARTED", response);
        return response;
    }

    @Transactional
    public RoomResponse cancelRoom(String roomCode, CancelRoomRequest request) {
        Room room = findRoom(roomCode);
        Player player = playerRepository.findById(request.playerId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Giocatore non trovato"));

        if (!Objects.equals(player.getRoom().getId(), room.getId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Il giocatore non appartiene alla stanza");
        }

        room.setStatus(RoomStatus.CLOSED);
        roomRepository.save(room);

        RoomResponse response = toRoomResponse(room, player.getId(), player.isHost());
        publish(room.getRoomCode(), "ROOM_CANCELLED", response);
        return response;
    }

    @Transactional
    public void submitAnswers(Long roundId, SubmitAnswersRequest request) {
        Round round = roundRepository.findById(roundId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Manche non trovata"));

        Player player = playerRepository.findById(request.playerId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Giocatore non trovato"));

        if (!Objects.equals(player.getRoom().getId(), round.getRoom().getId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Il giocatore non appartiene alla stanza della manche");
        }

        List<GameCategory> categories = categoryRepository.findByRoomOrderByIdAsc(round.getRoom());
        Map<String, GameCategory> categoryByName = categories.stream()
                .collect(Collectors.toMap(c -> c.getName().toLowerCase(Locale.ROOT), c -> c));

        if (request.answers() != null) {
            answerRepository.deleteAll(answerRepository.findByRoundAndPlayer(round, player));
            answerRepository.flush();

            request.answers().forEach((categoryName, answerValue) -> {
                GameCategory category = categoryByName.get(categoryName.toLowerCase(Locale.ROOT));
                if (category == null) return;

                Answer answer = new Answer();
                answer.setRound(round);
                answer.setPlayer(player);
                answer.setCategory(category);
                answer.setAnswer(clean(answerValue));
                answer.setPoints(0);
                answerRepository.save(answer);
            });
        }

        publish(round.getRoom().getRoomCode(), "ANSWERS_SUBMITTED", Map.of(
                "roundId", round.getId(),
                "playerId", player.getId(),
                "playerName", player.getName()
        ));
    }

    @Transactional
    public RoundResponse endRound(Long roundId) {
        Round round = roundRepository.findById(roundId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Manche non trovata"));

        round.setStatus(RoundStatus.ENDED);
        round.setEndedAt(Instant.now());
        roundRepository.save(round);

        Room room = round.getRoom();
        room.setStatus(RoomStatus.ROUND_ENDED);
        roomRepository.save(room);

        RoundResponse response = new RoundResponse(round.getId(), round.getLetter(), round.getStatus().name(), 0);
        publish(room.getRoomCode(), "ROUND_ENDED", response);
        return response;
    }

    private Room findRoom(String roomCode) {
        return roomRepository.findByRoomCode(roomCode.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Stanza non trovata"));
    }

    private RoomResponse toRoomResponse(Room room, Long currentPlayerId, boolean currentPlayerHost) {
        List<String> categories = categoryRepository.findByRoomOrderByIdAsc(room).stream()
                .map(GameCategory::getName)
                .toList();

        List<PlayerResponse> players = playerRepository.findByRoomOrderByJoinedAtAsc(room).stream()
                .map(player -> new PlayerResponse(player.getId(), player.getName(), player.isHost(), player.getTotalPoints()))
                .toList();

        RoundResponse currentRound = roundRepository.findFirstByRoomOrderByStartedAtDesc(room)
                .map(round -> new RoundResponse(round.getId(), round.getLetter(), round.getStatus().name(), ROUND_SECONDS))
                .orElse(null);

        return new RoomResponse(
                room.getRoomCode(),
                room.getStatus().name(),
                currentPlayerId,
                currentPlayerHost,
                categories,
                players,
                currentRound,
                getAnswers(currentRound == null ? null : currentRound.id())
        );
    }

    private Map<Long, Map<String, String>> getAnswers(Long roundId) {
        if (roundId == null) {
            return Map.of();
        }

        Round round = roundRepository.findById(roundId).orElse(null);
        if (round == null) {
            return Map.of();
        }

        Map<Long, Map<String, String>> answersByPlayer = new LinkedHashMap<>();

        for (Answer answer : answerRepository.findByRound(round)) {
            answersByPlayer
                    .computeIfAbsent(answer.getPlayer().getId(), ignored -> new LinkedHashMap<>())
                    .put(answer.getCategory().getName(), answer.getAnswer());
        }

        return answersByPlayer;
    }

    private void publish(String roomCode, String type, Object payload) {
        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode, new WsEvent(type, payload));
    }

    private String generateUniqueRoomCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        } while (roomRepository.existsByRoomCode(code));
        return code;
    }

    private String randomLetter() {
        int index = new Random().nextInt(LETTERS.length());
        return String.valueOf(LETTERS.charAt(index));
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
