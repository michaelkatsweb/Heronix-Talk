-- ============================================
-- Heronix Talk - PostgreSQL Performance Indexes
-- Run after initial schema creation
-- ============================================

-- ============================================
-- MESSAGE INDEXES (Critical for chat performance)
-- ============================================

-- Composite index for message queries by channel + timestamp (most common query)
CREATE INDEX IF NOT EXISTS idx_message_channel_timestamp
ON messages(channel_id, timestamp DESC)
WHERE deleted = false;

-- Index for searching message content (with pg_trgm for fuzzy search)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_message_content_trgm
ON messages USING gin(content gin_trgm_ops);

-- Index for pinned messages lookup
CREATE INDEX IF NOT EXISTS idx_message_pinned
ON messages(channel_id)
WHERE pinned = true AND deleted = false;

-- Index for message sync status (offline-first sync)
CREATE INDEX IF NOT EXISTS idx_message_sync_status
ON messages(sync_status)
WHERE sync_status IN ('PENDING', 'LOCAL_ONLY');

-- Index for client ID deduplication
CREATE INDEX IF NOT EXISTS idx_message_client_id
ON messages(client_id)
WHERE client_id IS NOT NULL;

-- ============================================
-- CHANNEL MEMBERSHIP INDEXES (Critical for broadcasts)
-- ============================================

-- Composite index for active memberships by channel (used in every broadcast)
CREATE INDEX IF NOT EXISTS idx_membership_channel_active
ON channel_memberships(channel_id, user_id)
WHERE active = true;

-- Index for user's channels (user's channel list)
CREATE INDEX IF NOT EXISTS idx_membership_user_active
ON channel_memberships(user_id)
WHERE active = true;

-- Index for unread message counts
CREATE INDEX IF NOT EXISTS idx_membership_unread
ON channel_memberships(user_id, unread_count)
WHERE active = true AND unread_count > 0;

-- ============================================
-- CHANNEL INDEXES
-- ============================================

-- Index for active channels ordered by last message
CREATE INDEX IF NOT EXISTS idx_channel_active_lastmsg
ON channels(last_message_time DESC)
WHERE active = true AND archived = false;

-- Index for direct message lookup
CREATE INDEX IF NOT EXISTS idx_channel_dm_key
ON channels(direct_message_key)
WHERE direct_message_key IS NOT NULL;

-- Index for public channels
CREATE INDEX IF NOT EXISTS idx_channel_public
ON channels(channel_type)
WHERE channel_type = 'PUBLIC' AND active = true;

-- ============================================
-- USER SESSION INDEXES
-- ============================================

-- Index for active sessions lookup
CREATE INDEX IF NOT EXISTS idx_session_active_user
ON user_sessions(user_id)
WHERE active = true;

-- Index for session cleanup (expired sessions)
CREATE INDEX IF NOT EXISTS idx_session_expires
ON user_sessions(expires_at)
WHERE active = true;

-- Index for last activity (presence detection)
CREATE INDEX IF NOT EXISTS idx_session_last_activity
ON user_sessions(last_activity_at)
WHERE active = true;

-- ============================================
-- USER INDEXES
-- ============================================

-- Index for username lookup (login)
CREATE INDEX IF NOT EXISTS idx_user_username_active
ON users(username)
WHERE active = true;

-- Index for user status (presence)
CREATE INDEX IF NOT EXISTS idx_user_status
ON users(status)
WHERE active = true;

-- ============================================
-- NEWS ITEM INDEXES
-- ============================================

-- Index for active news ordered by date
CREATE INDEX IF NOT EXISTS idx_news_active_date
ON news_items(publish_date DESC)
WHERE active = true;

-- Index for news by category
CREATE INDEX IF NOT EXISTS idx_news_category
ON news_items(category)
WHERE active = true;

-- ============================================
-- ANALYZE TABLES (Update statistics)
-- ============================================

ANALYZE messages;
ANALYZE channel_memberships;
ANALYZE channels;
ANALYZE user_sessions;
ANALYZE users;
ANALYZE news_items;

-- ============================================
-- VACUUM (Reclaim space, update visibility map)
-- ============================================

-- Run during low-traffic periods:
-- VACUUM ANALYZE messages;
-- VACUUM ANALYZE channel_memberships;
