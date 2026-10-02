CREATE OR REPLACE FUNCTION get_or_create_direct_room(
    p_user_a BIGINT,
    p_user_b BIGINT
)
RETURNS BIGINT AS $$
DECLARE
    v_room_id BIGINT;
BEGIN
    IF p_user_a = p_user_b THEN
        RAISE EXCEPTION 'A private room requires two different users';
    END IF;

    PERFORM pg_advisory_xact_lock(
        hashtextextended(
            LEAST(p_user_a, p_user_b)::TEXT || ':' || GREATEST(p_user_a, p_user_b)::TEXT,
            0
        )
    );

    SELECT cp1.conversation_id INTO v_room_id
    FROM conversation_participants cp1
    JOIN conversation_participants cp2 ON cp1.conversation_id = cp2.conversation_id
    JOIN conversations c ON c.id = cp1.conversation_id
    WHERE c.type = 'PRIVATE'
      AND cp1.user_id = p_user_a
      AND cp2.user_id = p_user_b
      AND cp1.left_at IS NULL
      AND cp2.left_at IS NULL
    ORDER BY c.id
    LIMIT 1;

    IF v_room_id IS NOT NULL THEN
        RETURN v_room_id;
    END IF;

    INSERT INTO conversations (type, created_by, created_at, updated_at)
    VALUES ('PRIVATE', p_user_a, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_room_id;

    INSERT INTO conversation_participants (conversation_id, user_id, role, joined_at)
    VALUES (v_room_id, p_user_a, 'OWNER', CURRENT_TIMESTAMP);

    INSERT INTO conversation_participants (conversation_id, user_id, role, joined_at)
    VALUES (v_room_id, p_user_b, 'MEMBER', CURRENT_TIMESTAMP);

    RETURN v_room_id;
END;
$$ LANGUAGE plpgsql;
