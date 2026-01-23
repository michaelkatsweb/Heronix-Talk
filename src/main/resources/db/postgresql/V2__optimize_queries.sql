-- ============================================
-- Heronix Talk - PostgreSQL Query Optimization
-- Optimized functions for high-traffic queries
-- ============================================

-- ============================================
-- FUNCTION: Get channel member IDs efficiently
-- Used by WebSocket broadcast - called on every message
-- ============================================

CREATE OR REPLACE FUNCTION get_channel_member_ids(p_channel_id BIGINT)
RETURNS TABLE(user_id BIGINT) AS $$
BEGIN
    RETURN QUERY
    SELECT cm.user_id
    FROM channel_memberships cm
    WHERE cm.channel_id = p_channel_id
      AND cm.active = true;
END;
$$ LANGUAGE plpgsql STABLE;

-- ============================================
-- FUNCTION: Get channel messages with pagination
-- Optimized for infinite scroll
-- ============================================

CREATE OR REPLACE FUNCTION get_channel_messages(
    p_channel_id BIGINT,
    p_limit INT DEFAULT 50,
    p_before_id BIGINT DEFAULT NULL
)
RETURNS TABLE(
    id BIGINT,
    message_uuid VARCHAR,
    sender_id BIGINT,
    sender_name VARCHAR,
    content TEXT,
    message_type VARCHAR,
    timestamp TIMESTAMP,
    edited BOOLEAN,
    pinned BOOLEAN,
    reply_to_id BIGINT,
    attachment_path VARCHAR,
    attachment_name VARCHAR
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        m.id,
        m.message_uuid,
        m.sender_id,
        u.full_name as sender_name,
        m.content,
        m.message_type::VARCHAR,
        m.timestamp,
        m.edited,
        m.pinned,
        m.reply_to_id,
        m.attachment_path,
        m.attachment_name
    FROM messages m
    JOIN users u ON u.id = m.sender_id
    WHERE m.channel_id = p_channel_id
      AND m.deleted = false
      AND (p_before_id IS NULL OR m.id < p_before_id)
    ORDER BY m.timestamp DESC
    LIMIT p_limit;
END;
$$ LANGUAGE plpgsql STABLE;

-- ============================================
-- FUNCTION: Get user's channels with unread counts
-- ============================================

CREATE OR REPLACE FUNCTION get_user_channels(p_user_id BIGINT)
RETURNS TABLE(
    channel_id BIGINT,
    channel_name VARCHAR,
    channel_type VARCHAR,
    icon VARCHAR,
    member_count INT,
    unread_count INT,
    last_message_time TIMESTAMP,
    muted BOOLEAN,
    pinned BOOLEAN
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        c.id,
        c.name,
        c.channel_type::VARCHAR,
        c.icon,
        c.member_count,
        cm.unread_count,
        c.last_message_time,
        cm.muted,
        cm.pinned
    FROM channel_memberships cm
    JOIN channels c ON c.id = cm.channel_id
    WHERE cm.user_id = p_user_id
      AND cm.active = true
      AND c.active = true
    ORDER BY cm.pinned DESC, c.last_message_time DESC NULLS LAST;
END;
$$ LANGUAGE plpgsql STABLE;

-- ============================================
-- FUNCTION: Increment unread count for channel members
-- Called when a message is sent
-- ============================================

CREATE OR REPLACE FUNCTION increment_unread_counts(
    p_channel_id BIGINT,
    p_sender_id BIGINT
)
RETURNS INT AS $$
DECLARE
    updated_count INT;
BEGIN
    UPDATE channel_memberships
    SET unread_count = unread_count + 1
    WHERE channel_id = p_channel_id
      AND user_id != p_sender_id
      AND active = true;

    GET DIAGNOSTICS updated_count = ROW_COUNT;
    RETURN updated_count;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- FUNCTION: Clean up expired sessions
-- Run periodically via scheduled job
-- ============================================

CREATE OR REPLACE FUNCTION cleanup_expired_sessions()
RETURNS INT AS $$
DECLARE
    deleted_count INT;
BEGIN
    UPDATE user_sessions
    SET active = false,
        disconnected_at = NOW()
    WHERE active = true
      AND (
          expires_at < NOW()
          OR last_activity_at < NOW() - INTERVAL '30 minutes'
      );

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- MATERIALIZED VIEW: Online users count per channel
-- Refresh periodically for dashboard stats
-- ============================================

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_channel_online_stats AS
SELECT
    cm.channel_id,
    COUNT(DISTINCT us.user_id) as online_count,
    c.member_count as total_members
FROM channel_memberships cm
JOIN channels c ON c.id = cm.channel_id
LEFT JOIN user_sessions us ON us.user_id = cm.user_id AND us.active = true
WHERE cm.active = true
GROUP BY cm.channel_id, c.member_count;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_channel_online ON mv_channel_online_stats(channel_id);

-- Refresh command (run periodically):
-- REFRESH MATERIALIZED VIEW CONCURRENTLY mv_channel_online_stats;

-- ============================================
-- CONFIGURATION: PostgreSQL tuning recommendations
-- Add to postgresql.conf for 200+ users
-- ============================================

-- Recommended settings (uncomment in postgresql.conf):
--
-- # Memory
-- shared_buffers = 256MB                 # 25% of RAM for dedicated server
-- effective_cache_size = 768MB           # 75% of RAM
-- work_mem = 16MB                        # Per-operation memory
-- maintenance_work_mem = 128MB           # For VACUUM, CREATE INDEX
--
-- # Connections (with PgBouncer, keep this lower)
-- max_connections = 100                  # PgBouncer handles more
--
-- # Write performance
-- wal_buffers = 16MB
-- checkpoint_completion_target = 0.9
--
-- # Query planner
-- random_page_cost = 1.1                 # SSD optimization
-- effective_io_concurrency = 200         # SSD optimization
--
-- # Logging (for debugging)
-- log_min_duration_statement = 100       # Log queries > 100ms
