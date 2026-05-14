# Nomi Cose Città Backend

Backend Spring Boot per il gioco multiplayer "Nomi, Cose, Città".

## Stack

- Java 21
- Spring Boot 3.5.14
- Spring Web
- Spring WebSocket STOMP
- Spring Data JPA
- H2 in locale
- PostgreSQL per deploy/Supabase

## Avvio locale

```bash
./mvnw spring-boot:run
```

Oppure, se usi Maven installato:

```bash
mvn spring-boot:run
```

Backend attivo su:

```text
http://localhost:8080
```

Health check:

```text
http://localhost:8080/health
```

Console H2:

```text
http://localhost:8080/h2-console
```

Credenziali H2:

```text
JDBC URL: jdbc:h2:mem:nomi_cose_citta
Username: sa
Password: lascia vuoto
```

## API principali

### Crea stanza

```http
POST /api/rooms
Content-Type: application/json
```

```json
{
  "playerName": "Marco",
  "categories": ["Nome", "Cosa", "Città", "Animale", "Anime"]
}
```

### Entra in stanza

```http
POST /api/rooms/{roomCode}/join
Content-Type: application/json
```

```json
{
  "playerName": "Luca"
}
```

### Recupera stanza

```http
GET /api/rooms/{roomCode}
```

### Avvia manche

```http
POST /api/rooms/{roomCode}/rounds
Content-Type: application/json
```

```json
{
  "playerId": 1
}
```

### Invia risposte

```http
POST /api/rounds/{roundId}/answers
Content-Type: application/json
```

```json
{
  "playerId": 1,
  "answers": {
    "Nome": "Marco",
    "Cosa": "Mela",
    "Città": "Milano",
    "Animale": "Mucca"
  }
}
```

### Termina manche

```http
POST /api/rounds/{roundId}/end
```

## WebSocket

Endpoint:

```text
ws://localhost:8080/ws
```

Topic stanza:

```text
/topic/rooms/{roomCode}
```

Eventi emessi:

- ROOM_CREATED
- PLAYER_JOINED
- ROUND_STARTED
- ANSWERS_SUBMITTED
- ROUND_ENDED

## Deploy con Supabase PostgreSQL

Profilo prod:

```bash
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=jdbc:postgresql://HOST:PORT/postgres?sslmode=require
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=PASSWORD_SUPABASE
CORS_ALLOWED_ORIGINS=https://tuo-frontend.netlify.app
```
