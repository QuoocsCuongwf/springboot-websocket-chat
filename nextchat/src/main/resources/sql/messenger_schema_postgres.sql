-- =====================================================================
-- MESSENGER DATABASE SCHEMA - PostgreSQL version
-- =====================================================================
-- BƯỚC 1: Tạo database (chạy tách riêng, KHÔNG nằm trong transaction)
--   - Nếu dùng psql:   psql -U postgres -c "CREATE DATABASE messenger_db;"
--   - Nếu dùng DBeaver/pgAdmin: tạo database qua GUI, rồi mở kết nối mới
--     trỏ tới database đó trước khi chạy phần bên dưới.
--
-- BƯỚC 2: Kết nối vào messenger_db rồi chạy toàn bộ phần còn lại của file.
--   Nếu chạy bằng psql, có thể gộp 2 bước bằng meta-command:
--     CREATE DATABASE messenger_db;
--     \c messenger_db
-- =====================================================================

-- ---------------------------------------------------------------------
-- Trigger dùng chung để tự động cập nhật cột updated_at (thay cho
-- "ON UPDATE CURRENT_TIMESTAMP" của MySQL, Postgres không có sẵn)
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =====================================================================
-- 1. NGƯỜI DÙNG & XÁC THỰC
-- =====================================================================

CREATE TABLE users (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    email           VARCHAR(150) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(150) NOT NULL,
    avatar_url      VARCHAR(500),
    bio             VARCHAR(255),
    phone_number    VARCHAR(20),
    status          VARCHAR(10) NOT NULL DEFAULT 'OFFLINE'
                        CHECK (status IN ('ONLINE','OFFLINE','AWAY','BUSY')),
    last_seen_at    TIMESTAMP,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE roles (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name    VARCHAR(30) NOT NULL UNIQUE          -- ROLE_USER, ROLE_ADMIN
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(255) NOT NULL UNIQUE,
    expiry_date TIMESTAMP NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_settings (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                 BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    theme                   VARCHAR(10) NOT NULL DEFAULT 'SYSTEM'
                                CHECK (theme IN ('LIGHT','DARK','SYSTEM')),
    language                VARCHAR(10) NOT NULL DEFAULT 'vi',
    notification_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    sound_enabled           BOOLEAN NOT NULL DEFAULT TRUE,
    show_last_seen          BOOLEAN NOT NULL DEFAULT TRUE,
    show_read_receipt       BOOLEAN NOT NULL DEFAULT TRUE
);

-- =====================================================================
-- 2. BẠN BÈ / DANH BẠ / CHẶN
-- =====================================================================

CREATE TABLE friendships (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,   -- người gửi lời mời
    friend_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,   -- người nhận lời mời
    status          VARCHAR(10) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','ACCEPTED','DECLINED')),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_friendship UNIQUE (user_id, friend_id)
);
CREATE TRIGGER trg_friendships_updated_at BEFORE UPDATE ON friendships
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE user_blocks (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,   -- người chặn
    blocked_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,   -- người bị chặn
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_block UNIQUE (user_id, blocked_id)
);

-- =====================================================================
-- 3. HỘI THOẠI (1-1 & NHÓM)
-- =====================================================================

CREATE TABLE conversations (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type            VARCHAR(10) NOT NULL CHECK (type IN ('PRIVATE','GROUP')),
    name            VARCHAR(150),                  -- NULL nếu là PRIVATE
    avatar_url      VARCHAR(500),
    created_by      BIGINT NOT NULL REFERENCES users(id),
    last_message_id BIGINT,   -- FK gắn sau khi tạo bảng messages, tránh phụ thuộc vòng
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER trg_conversations_updated_at BEFORE UPDATE ON conversations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE conversation_participants (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id         BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id                 BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role                    VARCHAR(10) NOT NULL DEFAULT 'MEMBER'
                                CHECK (role IN ('OWNER','ADMIN','MEMBER')),
    nickname                VARCHAR(100),
    is_muted                BOOLEAN NOT NULL DEFAULT FALSE,
    mute_until              TIMESTAMP,
    is_pinned               BOOLEAN NOT NULL DEFAULT FALSE,
    last_read_message_id    BIGINT,
    joined_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at                 TIMESTAMP,
    CONSTRAINT uq_conversation_user UNIQUE (conversation_id, user_id)
);
CREATE INDEX idx_participants_user ON conversation_participants(user_id);

CREATE TABLE group_invite_links (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    invite_code     VARCHAR(50) NOT NULL UNIQUE,
    created_by      BIGINT NOT NULL REFERENCES users(id),
    max_uses        INT,
    use_count       INT NOT NULL DEFAULT 0,
    expires_at      TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================================
-- 4. TIN NHẮN
-- =====================================================================

CREATE TABLE messages (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id         BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id               BIGINT NOT NULL REFERENCES users(id),
    message_type            VARCHAR(10) NOT NULL DEFAULT 'TEXT'
                                CHECK (message_type IN ('TEXT','IMAGE','VIDEO','AUDIO','FILE','STICKER','SYSTEM')),
    content                 TEXT,
    reply_to_message_id     BIGINT REFERENCES messages(id) ON DELETE SET NULL,
    forwarded_from_id       BIGINT REFERENCES messages(id) ON DELETE SET NULL,
    is_edited               BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted              BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_messages_conversation_created ON messages(conversation_id, created_at);
CREATE INDEX idx_messages_sender ON messages(sender_id);
CREATE TRIGGER trg_messages_updated_at BEFORE UPDATE ON messages
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE conversations
    ADD CONSTRAINT fk_conversations_last_message
    FOREIGN KEY (last_message_id) REFERENCES messages(id) ON DELETE SET NULL;

CREATE TABLE message_attachments (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message_id      BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    file_url        VARCHAR(500) NOT NULL,
    file_name       VARCHAR(255),
    file_type       VARCHAR(50),
    file_size       BIGINT,
    thumbnail_url   VARCHAR(500),
    width           INT,
    height          INT,
    duration_sec    INT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE message_status (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message_id      BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status          VARCHAR(10) NOT NULL DEFAULT 'SENT'
                        CHECK (status IN ('SENT','DELIVERED','READ')),
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_message_user_status UNIQUE (message_id, user_id)
);
CREATE INDEX idx_message_status_user ON message_status(user_id, status);
CREATE TRIGGER trg_message_status_updated_at BEFORE UPDATE ON message_status
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE message_deletions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message_id      BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    deleted_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_message_deletion UNIQUE (message_id, user_id)
);

CREATE TABLE message_reactions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message_id      BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reaction_type   VARCHAR(20) NOT NULL,     -- LIKE, LOVE, HAHA, WOW, SAD, ANGRY hoặc emoji unicode
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_message_reaction UNIQUE (message_id, user_id)
);

CREATE TABLE message_mentions (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message_id          BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    mentioned_user_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE pinned_messages (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    message_id      BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    pinned_by       BIGINT NOT NULL REFERENCES users(id),
    pinned_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_pinned UNIQUE (conversation_id, message_id)
);

-- =====================================================================
-- 5. THÔNG BÁO
-- =====================================================================

CREATE TABLE notifications (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type            VARCHAR(20) NOT NULL
                        CHECK (type IN ('NEW_MESSAGE','FRIEND_REQUEST','FRIEND_ACCEPTED','GROUP_INVITE','MENTION','REACTION')),
    reference_id    BIGINT,
    content         VARCHAR(255),
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_notifications_user_read ON notifications(user_id, is_read);

-- =====================================================================
-- 6. GỌI THOẠI / VIDEO (mở rộng, không bắt buộc cho bản MVP)
-- =====================================================================

CREATE TABLE calls (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    caller_id       BIGINT NOT NULL REFERENCES users(id),
    call_type       VARCHAR(10) NOT NULL CHECK (call_type IN ('AUDIO','VIDEO')),
    status          VARCHAR(10) NOT NULL DEFAULT 'MISSED'
                        CHECK (status IN ('MISSED','ANSWERED','DECLINED','ENDED')),
    started_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at        TIMESTAMP,
    duration_sec    INT
);

CREATE TABLE call_participants (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    call_id         BIGINT NOT NULL REFERENCES calls(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status          VARCHAR(10) NOT NULL DEFAULT 'INVITED'
                        CHECK (status IN ('INVITED','JOINED','DECLINED','LEFT')),
    joined_at       TIMESTAMP,
    left_at         TIMESTAMP
);

-- =====================================================================
-- GHI CHÚ
-- =====================================================================
-- - Enum được đổi thành VARCHAR + CHECK để map thẳng sang JPA bằng
--   @Enumerated(EnumType.STRING), không cần thư viện phụ trợ nào.
-- - Nếu muốn dùng kiểu ENUM gốc của Postgres (CREATE TYPE ... AS ENUM),
--   vẫn được, nhưng Hibernate cần thêm cấu hình (ví dụ thư viện
--   hypersistence-utils) mới ghi/đọc đúng kiểu này -> không khuyến nghị
--   khi mới học.
-- =====================================================================
