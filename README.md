# NextChat Backend

Spring Boot REST backend for JWT authentication, user discovery, chat rooms, and paginated message history. PostgreSQL schema changes are managed with Flyway.

## Run locally

1. Copy `nextchat/.env.example` to `nextchat/.env` and replace the placeholder values. The `.env` file is intentionally ignored by Git.
2. Start PostgreSQL with `docker compose --env-file .env up -d` from `nextchat`.
3. Export the same values for Spring Boot with `set -a && . ./.env && set +a`.
4. Run `./mvnw spring-boot:run` from `nextchat`.

Flyway applies the versioned migrations on an empty database. Existing databases are baselined at version 1, receive later migrations, and are then validated by Hibernate.

## API contract

All task endpoints are under `/api/v1` and return this envelope:

```json
{
  "success": true,
  "message": "...",
  "data": {},
  "timestamp": "2026-10-01T03:00:00Z"
}
```

Protected routes require `Authorization: Bearer <accessToken>`.

| Method | Endpoint | Request body | Result |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/register` | `username`, `email`, `password`, `fullName` | `UserDTO` |
| POST | `/api/v1/auth/login` | `username`, `password` | access token, rotated refresh token, user |
| POST | `/api/v1/auth/refresh` | `refreshToken` | new access and refresh tokens |
| GET | `/api/v1/users/me` | - | current `UserProfileDTO` |
| GET | `/api/v1/users/search?q=...` | - | matching `UserDTO` values |
| GET | `/api/v1/rooms` | - | current user's rooms |
| POST | `/api/v1/rooms/direct` | `targetUserId` | existing or new private room |
| POST | `/api/v1/rooms/group` | `groupName`, `memberIds` | new group room |
| GET | `/api/v1/rooms/{roomId}/messages?page=0&size=20` | - | paginated message history |

`size` must be from 1 to 100. Group creation rejects any nonexistent or inactive member rather than silently creating a partial group. Refresh tokens are single-use and only a SHA-256 hash is persisted.

## Tests

Run `./mvnw clean verify` from `nextchat`. The integration suite starts a temporary PostgreSQL 16 container, applies Flyway migrations, and does not use the local development database.
