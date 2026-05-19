package it.axel.nomicosecitta.service;

import it.axel.nomicosecitta.dto.*;
import it.axel.nomicosecitta.entity.*;
import it.axel.nomicosecitta.repository.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.*;

@Service
public class RoomService {
    private static final String LETTERS = "ABCDEFGHILMNOPQRSTUVZ";
    private static final int ROUND_SECONDS = 120;

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final GameCategoryRepository categoryRepository;
    private final RoundRepository roundRepository;
    private final AnswerRepository answerRepository;
    private final AnswerValidationRepository validationRepository;
    private final RoundValidationReadyRepository validationReadyRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public RoomService(
            RoomRepository roomRepository,
            PlayerRepository playerRepository,
            GameCategoryRepository categoryRepository,
            RoundRepository roundRepository,
            AnswerRepository answerRepository,
            AnswerValidationRepository validationRepository,
            RoundValidationReadyRepository validationReadyRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.categoryRepository = categoryRepository;
        this.roundRepository = roundRepository;
        this.answerRepository = answerRepository;
        this.validationRepository = validationRepository;
        this.validationReadyRepository = validationReadyRepository;
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

        if (room.getStatus() == RoomStatus.CLOSED || room.getStatus() == RoomStatus.FINISHED) {
            throw new ResponseStatusException(BAD_REQUEST, "La stanza è chiusa");
        }
        if (room.getStatus() == RoomStatus.IN_PROGRESS || room.getStatus() == RoomStatus.VALIDATING) {
            throw new ResponseStatusException(BAD_REQUEST, "Non puoi entrare mentre una manche è in corso");
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

        ensureCanStartNewRound(room);

        Round round = new Round();
        round.setRoom(room);
        round.setLetter(randomLetter(room));
        round.setStatus(RoundStatus.IN_PROGRESS);
        roundRepository.save(round);

        room.setStatus(RoomStatus.IN_PROGRESS);
        roomRepository.save(room);

        RoundResponse response = toRoundResponse(round);
        publish(room.getRoomCode(), "ROUND_STARTED", response);
        return response;
    }

    @Transactional
    public void submitAnswers(Long roundId, SubmitAnswersRequest request) {
        Round round = findRound(roundId);
        ensureRoundInProgress(round);

        Player player = playerRepository.findById(request.playerId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Giocatore non trovato"));

        if (!Objects.equals(player.getRoom().getId(), round.getRoom().getId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Il giocatore non appartiene alla stanza della manche");
        }

        List<GameCategory> categories = categoryRepository.findByRoomOrderByIdAsc(round.getRoom());
        Map<String, String> submitted = request.answers() == null ? Map.of() : request.answers();

        for (GameCategory category : categories) {
            String value = submitted.getOrDefault(category.getName(), "");
            Answer answer = answerRepository.findByRoundAndPlayerAndCategory(round, player, category)
                    .orElseGet(() -> {
                        Answer created = new Answer();
                        created.setRound(round);
                        created.setPlayer(player);
                        created.setCategory(category);
                        return created;
                    });
            answer.setAnswer(clean(value));
            answer.setPoints(0);
            answerRepository.save(answer);
        }

        publish(round.getRoom().getRoomCode(), "ANSWERS_SUBMITTED", Map.of(
                "roundId", round.getId(),
                "playerId", player.getId(),
                "playerName", player.getName()
        ));
    }

    @Transactional
    public RoundResponse submitValidations(Long roundId, SubmitValidationsRequest request) {
        Round round = findRound(roundId);
        ensureRoundInValidation(round);

        Player validator = playerRepository.findById(request.validatorPlayerId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Validatore non trovato"));

        if (!Objects.equals(validator.getRoom().getId(), round.getRoom().getId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Il validatore non appartiene alla stanza della manche");
        }

        for (AnswerValidationItemRequest item : request.validations()) {
            Answer answer = answerRepository.findById(item.answerId())
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Risposta non trovata: " + item.answerId()));

            if (!Objects.equals(answer.getRound().getId(), round.getId())) {
                throw new ResponseStatusException(BAD_REQUEST, "La risposta non appartiene alla manche");
            }
            if (Objects.equals(answer.getPlayer().getId(), validator.getId())) {
                throw new ResponseStatusException(BAD_REQUEST, "Non puoi validare una tua risposta");
            }

            AnswerValidation validation = validationRepository.findByRoundAndAnswerAndValidator(round, answer, validator)
                    .orElseGet(() -> {
                        AnswerValidation created = new AnswerValidation();
                        created.setRound(round);
                        created.setAnswer(answer);
                        created.setCategory(answer.getCategory());
                        created.setValidator(validator);
                        return created;
                    });
            validation.setValid(item.valid());
            validationRepository.save(validation);
        }

        recalculatePoints(round);

        if (areValidationsComplete(round)) {
            closeRound(round);
        }

        RoundResponse response = toRoundResponse(round);
        publish(round.getRoom().getRoomCode(), "VALIDATIONS_SUBMITTED", response);
        return response;
    }

    @Transactional
    public RoundResponse markValidationReady(Long roundId, ValidationReadyRequest request) {
        Round round = findRound(roundId);

        if (round.getStatus() != RoundStatus.VALIDATING && round.getStatus() != RoundStatus.ENDED) {
            throw new ResponseStatusException(BAD_REQUEST, "Puoi segnarti pronto solo durante la fase di validazione");
        }

        Player player = playerRepository.findById(request.playerId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Giocatore non trovato"));

        if (!Objects.equals(player.getRoom().getId(), round.getRoom().getId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Il giocatore non appartiene alla stanza della manche");
        }

        if (!hasPlayerCompletedRequiredValidations(round, player)) {
            throw new ResponseStatusException(CONFLICT, "Prima devi validare tutte le risposte dell'avversario");
        }

        validationReadyRepository.findByRoundAndPlayer(round, player)
                .orElseGet(() -> {
                    RoundValidationReady ready = new RoundValidationReady();
                    ready.setRound(round);
                    ready.setPlayer(player);
                    return validationReadyRepository.save(ready);
                });

        recalculatePoints(round);

        if (areValidationReadyComplete(round)) {
            closeRound(round);
        }

        RoundResponse response = toRoundResponse(round);
        publish(round.getRoom().getRoomCode(), "VALIDATION_READY", response);
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
        publish(room.getRoomCode(), "ROOM_CLOSED", response);
        return response;
    }


    @Transactional
    public RoomResponse finishRoom(String roomCode, FinishRoomRequest request) {
        Room room = findRoom(roomCode);
        Player player = playerRepository.findById(request.playerId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Giocatore non trovato"));

        if (!Objects.equals(player.getRoom().getId(), room.getId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Il giocatore non appartiene alla stanza");
        }

        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new ResponseStatusException(CONFLICT, "La stanza è stata chiusa");
        }

        if (room.getStatus() == RoomStatus.FINISHED) {
            return toRoomResponse(room, player.getId(), player.isHost());
        }

        Round latestRound = roundRepository.findFirstByRoomOrderByStartedAtDesc(room)
                .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Non puoi terminare una partita senza manche giocate"));

        if (!areValidationReadyComplete(latestRound)) {
            throw new ResponseStatusException(CONFLICT, "La partita può terminare solo quando entrambi i giocatori hanno validato e premuto Pronto");
        }

        recalculatePoints(latestRound);
        finishRoomWithWinner(room);

        RoomResponse response = toRoomResponse(room, player.getId(), player.isHost());
        publish(room.getRoomCode(), "ROOM_FINISHED", response);
        return response;
    }

    @Transactional
    public RoundResponse endRound(Long roundId) {
        Round round = findRound(roundId);

        if (round.getStatus() == RoundStatus.IN_PROGRESS) {
            if (!areSubmissionsComplete(round)) {
                throw new ResponseStatusException(CONFLICT, "La fase di scrittura può essere chiusa solo quando tutti i giocatori hanno inviato le risposte");
            }

            startValidationPhase(round);
            RoundResponse response = toRoundResponse(round);
            publish(round.getRoom().getRoomCode(), "VALIDATION_STARTED", response);
            return response;
        }

        if (round.getStatus() == RoundStatus.VALIDATING) {
            if (!areValidationsComplete(round)) {
                throw new ResponseStatusException(CONFLICT, "La manche può essere chiusa solo quando tutti hanno validato le risposte degli altri giocatori");
            }

            recalculatePoints(round);
            closeRound(round);

            RoundResponse response = toRoundResponse(round);
            publish(round.getRoom().getRoomCode(), "ROUND_ENDED", response);
            return response;
        }

        return toRoundResponse(round);
    }

    private Round findRound(Long roundId) {
        return roundRepository.findById(roundId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Manche non trovata"));
    }

    private Room findRoom(String roomCode) {
        return roomRepository.findByRoomCode(roomCode.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Stanza non trovata"));
    }

    private void ensureRoundInProgress(Round round) {
        if (round.getStatus() != RoundStatus.IN_PROGRESS) {
            throw new ResponseStatusException(BAD_REQUEST, "La fase di scrittura delle risposte non è attiva");
        }
    }

    private void ensureRoundInValidation(Round round) {
        if (round.getStatus() != RoundStatus.VALIDATING) {
            throw new ResponseStatusException(BAD_REQUEST, "Puoi validare solo dopo la chiusura della fase di scrittura");
        }
    }

    private void startValidationPhase(Round round) {
        round.setStatus(RoundStatus.VALIDATING);
        roundRepository.save(round);

        Room room = round.getRoom();
        room.setStatus(RoomStatus.VALIDATING);
        roomRepository.save(room);
    }

    private void ensureCanStartNewRound(Room room) {
        if (room.getStatus() == RoomStatus.CLOSED || room.getStatus() == RoomStatus.FINISHED) {
            throw new ResponseStatusException(CONFLICT, "La stanza è chiusa");
        }

        List<Player> players = playerRepository.findByRoomOrderByJoinedAtAsc(room);
        if (players.size() < 2) {
            throw new ResponseStatusException(CONFLICT, "Servono almeno due giocatori per iniziare una manche");
        }

        Optional<Round> latestRound = roundRepository.findFirstByRoomOrderByStartedAtDesc(room);
        if (latestRound.isEmpty()) {
            return;
        }

        Round round = latestRound.get();
        if (round.getStatus() == RoundStatus.IN_PROGRESS) {
            throw new ResponseStatusException(CONFLICT, "Esiste già una manche in corso");
        }
        if (round.getStatus() == RoundStatus.VALIDATING) {
            throw new ResponseStatusException(CONFLICT, "Prima entrambi i giocatori devono validare e premere Pronto");
        }
        if (!areValidationReadyComplete(round)) {
            throw new ResponseStatusException(CONFLICT, "La nuova manche può partire solo quando entrambi i giocatori hanno premuto Pronto");
        }
    }

    private void finishRoomWithWinner(Room room) {
        List<Player> players = playerRepository.findByRoomOrderByJoinedAtAsc(room);
        if (players.size() < 2) {
            throw new ResponseStatusException(CONFLICT, "Servono almeno due giocatori per terminare la partita");
        }

        int highestScore = players.stream()
                .mapToInt(Player::getTotalPoints)
                .max()
                .orElse(0);

        List<Player> winners = players.stream()
                .filter(candidate -> candidate.getTotalPoints() == highestScore)
                .toList();

        room.setStatus(RoomStatus.FINISHED);
        if (winners.size() == 1) {
            room.setWinner(winners.get(0));
            room.setDraw(false);
        } else {
            room.setWinner(null);
            room.setDraw(true);
        }
        roomRepository.save(room);
    }

    private void closeRound(Round round) {
        if (round.getStatus() != RoundStatus.ENDED) {
            round.setStatus(RoundStatus.ENDED);
            round.setEndedAt(Instant.now());
            roundRepository.save(round);
        }

        Room room = round.getRoom();
        room.setStatus(RoomStatus.ROUND_ENDED);
        roomRepository.save(room);
    }

    private boolean areSubmissionsComplete(Round round) {
        List<Player> players = playerRepository.findByRoomOrderByJoinedAtAsc(round.getRoom());
        List<GameCategory> categories = categoryRepository.findByRoomOrderByIdAsc(round.getRoom());
        List<Answer> answers = answerRepository.findByRound(round);

        if (players.size() < 2) {
            return false;
        }

        long expectedAnswers = (long) players.size() * categories.size();
        return answers.size() >= expectedAnswers;
    }

    private boolean areValidationsComplete(Round round) {
        List<Player> players = playerRepository.findByRoomOrderByJoinedAtAsc(round.getRoom());
        List<GameCategory> categories = categoryRepository.findByRoomOrderByIdAsc(round.getRoom());
        List<Answer> answers = answerRepository.findByRound(round);

        if (players.size() < 2) {
            return false;
        }

        long expectedAnswers = (long) players.size() * categories.size();
        if (answers.size() < expectedAnswers) {
            return false;
        }

        long expectedValidations = expectedAnswers * (players.size() - 1L);
        long actualValidations = validationRepository.countByRound(round);
        return actualValidations >= expectedValidations;
    }

    private boolean hasPlayerCompletedRequiredValidations(Round round, Player validator) {
        List<Answer> answersToValidate = answerRepository.findByRound(round).stream()
                .filter(answer -> !Objects.equals(answer.getPlayer().getId(), validator.getId()))
                .toList();

        if (answersToValidate.isEmpty()) {
            return false;
        }

        Set<Long> validatedAnswerIds = validationRepository.findByRound(round).stream()
                .filter(validation -> Objects.equals(validation.getValidator().getId(), validator.getId()))
                .map(validation -> validation.getAnswer().getId())
                .collect(Collectors.toSet());

        return answersToValidate.stream()
                .allMatch(answer -> validatedAnswerIds.contains(answer.getId()));
    }

    private boolean areValidationReadyComplete(Round round) {
        List<Player> players = playerRepository.findByRoomOrderByJoinedAtAsc(round.getRoom());
        if (players.size() < 2) {
            return false;
        }

        Set<Long> readyPlayerIds = validationReadyRepository.findByRound(round).stream()
                .map(ready -> ready.getPlayer().getId())
                .collect(Collectors.toSet());

        return players.stream().allMatch(player -> readyPlayerIds.contains(player.getId()));
    }

    private List<Long> getReadyPlayerIds(Round round) {
        return validationReadyRepository.findByRound(round).stream()
                .map(ready -> ready.getPlayer().getId())
                .toList();
    }

    private void recalculatePoints(Round round) {
        List<Answer> answers = answerRepository.findByRound(round);
        List<AnswerValidation> validations = validationRepository.findByRound(round);

        Map<Long, List<AnswerValidation>> validationsByAnswerId = validations.stream()
                .collect(Collectors.groupingBy(v -> v.getAnswer().getId()));

        // Raggruppa SOLO le risposte accettate per categoria+valore
        Map<String, List<Answer>> validAnswersByCategoryAndValue = answers.stream()
                .filter(answer -> isAccepted(answer, validationsByAnswerId.getOrDefault(answer.getId(), List.of())))
                .filter(answer -> !clean(answer.getAnswer()).isBlank())
                .collect(Collectors.groupingBy(answer ->
                        answer.getCategory().getId() + "::" + normalize(answer.getAnswer())));

        for (Answer answer : answers) {
            List<AnswerValidation> answerValidations = validationsByAnswerId.getOrDefault(answer.getId(), List.of());

            // Questa risposta è accettata?
            if (!isAccepted(answer, answerValidations) || clean(answer.getAnswer()).isBlank()) {
                answer.setPoints(0);
            } else {
                String key = answer.getCategory().getId() + "::" + normalize(answer.getAnswer());
                List<Answer> sameAnswers = validAnswersByCategoryAndValue.getOrDefault(key, List.of());

                // Conta quante risposte uguali appartengono ad ALTRI giocatori (non a se stesso)
                long othersWithSameAnswer = sameAnswers.stream()
                        .filter(a -> !Objects.equals(a.getPlayer().getId(), answer.getPlayer().getId()))
                        .count();

                answer.setPoints(othersWithSameAnswer > 0 ? 5 : 10);
            }
            answerRepository.save(answer);
        }

        recalculateRoomTotals(round.getRoom());
    }

    private boolean isAccepted(Answer answer, List<AnswerValidation> validations) {
        int expectedValidators = playerRepository.findByRoomOrderByJoinedAtAsc(answer.getPlayer().getRoom()).size() - 1;
        return expectedValidators > 0
                && validations.size() >= expectedValidators
                && validations.stream().allMatch(AnswerValidation::isValid);
    }

    private void recalculateRoomTotals(Room room) {
        List<Player> players = playerRepository.findByRoomOrderByJoinedAtAsc(room);
        Map<Long, Player> playerById = players.stream()
                .collect(Collectors.toMap(Player::getId, Function.identity()));

        Map<Long, Integer> totals = new HashMap<>();
        for (Round roomRound : roundRepository.findByRoom(room)) {
            for (Answer answer : answerRepository.findByRound(roomRound)) {
                totals.merge(answer.getPlayer().getId(), answer.getPoints(), Integer::sum);
            }
        }

        for (Player player : players) {
            Player managed = playerById.get(player.getId());
            managed.setTotalPoints(totals.getOrDefault(player.getId(), 0));
            playerRepository.save(managed);
        }
    }

    private RoomResponse toRoomResponse(Room room, Long currentPlayerId, boolean currentPlayerHost) {
        List<String> categories = categoryRepository.findByRoomOrderByIdAsc(room).stream()
                .map(GameCategory::getName)
                .toList();

        List<PlayerResponse> players = playerRepository.findByRoomOrderByJoinedAtAsc(room).stream()
                .map(player -> new PlayerResponse(player.getId(), player.getName(), player.isHost(), player.getTotalPoints()))
                .toList();

        RoundResponse currentRound = roundRepository.findFirstByRoomOrderByStartedAtDesc(room)
                .map(this::toRoundResponse)
                .orElse(null);

        return new RoomResponse(
                room.getRoomCode(),
                room.getStatus().name(),
                currentPlayerId,
                currentPlayerHost,
                categories,
                players,
                currentRound,
                room.getWinner() == null ? null : room.getWinner().getId(),
                room.isDraw()
        );
    }

    private RoundResponse toRoundResponse(Round round) {
        List<AnswerResponse> answers = answerRepository.findByRound(round).stream()
                .map(answer -> new AnswerResponse(
                        answer.getId(),
                        answer.getPlayer().getId(),
                        answer.getPlayer().getName(),
                        answer.getCategory().getName(),
                        answer.getAnswer(),
                        answer.getPoints()
                ))
                .toList();

        List<ValidationResponse> validations = validationRepository.findByRound(round).stream()
                .map(validation -> new ValidationResponse(
                        validation.getId(),
                        validation.getRound().getId(),
                        validation.getCategory().getName(),
                        validation.getAnswer().getId(),
                        validation.getAnswer().getPlayer().getId(),
                        validation.getAnswer().getPlayer().getName(),
                        validation.getAnswer().getAnswer(),
                        validation.getValidator().getId(),
                        validation.getValidator().getName(),
                        validation.isValid()
                ))
                .toList();

        return new RoundResponse(
                round.getId(),
                round.getLetter(),
                round.getStatus().name(),
                round.getStatus() == RoundStatus.IN_PROGRESS ? ROUND_SECONDS : 0,
                answers,
                validations,
                areValidationReadyComplete(round),
                getReadyPlayerIds(round)
        );
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

    private String randomLetter(Room room) {
        Set<String> usedLetters = roundRepository.findByRoom(room).stream()
                .map(Round::getLetter)
                .collect(Collectors.toSet());

        List<String> availableLetters = LETTERS.chars()
                .mapToObj(letter -> String.valueOf((char) letter))
                .filter(letter -> !usedLetters.contains(letter))
                .toList();

        if (availableLetters.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Tutte le lettere sono già state estratte");
        }

        int index = new Random().nextInt(availableLetters.size());
        return availableLetters.get(index);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }
}
