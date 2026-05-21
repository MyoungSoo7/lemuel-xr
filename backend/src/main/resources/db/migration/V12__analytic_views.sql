-- V12: 분석 보조 뷰 + 인덱스 (대시보드·KPI)
-- DB-SCHEMA.md §11

-- ============================================================
-- v_user_30d_summary — Day 30 리포트용 (실시간 뷰, 빠름)
-- ============================================================
CREATE OR REPLACE VIEW v_user_30d_summary AS
SELECT
    u.id                                                       AS user_id,
    u.created_at::date                                         AS joined_on,
    COUNT(DISTINCT s.id) FILTER (WHERE s.started_at >= NOW() - INTERVAL '30 days')        AS sessions_30d,
    COUNT(DISTINCT e.id) FILTER (WHERE e.created_at >= NOW() - INTERVAL '30 days')        AS emotion_logs_30d,
    COUNT(DISTINCT g.id) FILTER (WHERE g.started_at >= NOW() - INTERVAL '30 days')        AS missions_started_30d,
    COUNT(DISTINCT g.id) FILTER (WHERE g.completed_at >= NOW() - INTERVAL '30 days')      AS missions_completed_30d,
    COUNT(DISTINCT d.id) FILTER (WHERE d.created_at >= NOW() - INTERVAL '30 days')        AS diary_entries_30d,
    COUNT(DISTINCT p.id) FILTER (WHERE p.created_at >= NOW() - INTERVAL '30 days')        AS user_psalms_30d,
    COUNT(DISTINCT sa.id) FILTER (WHERE sa.created_at >= NOW() - INTERVAL '30 days')      AS safety_alerts_30d
FROM users u
LEFT JOIN app_sessions          s  ON s.user_id  = u.id
LEFT JOIN emotion_logs          e  ON e.user_id  = u.id
LEFT JOIN game_sessions         g  ON g.user_id  = u.id
LEFT JOIN diary_entries         d  ON d.user_id  = u.id
LEFT JOIN user_psalms           p  ON p.user_id  = u.id
LEFT JOIN safety_alerts         sa ON sa.user_id = u.id
WHERE u.deleted_at IS NULL
GROUP BY u.id, u.created_at;

-- ============================================================
-- v_mission_funnel — 미션 시작 → Scene 진행 → 완료율
-- ============================================================
CREATE OR REPLACE VIEW v_mission_funnel AS
SELECT
    g.character                                                AS mission,
    g.device_type,
    COUNT(*)                                                   AS sessions,
    COUNT(*) FILTER (WHERE g.scene_count_completed >= 1)       AS reached_scene_1,
    COUNT(*) FILTER (WHERE g.scene_count_completed >= 3)       AS reached_scene_3,
    COUNT(*) FILTER (WHERE g.completed_at IS NOT NULL)         AS completed,
    COUNT(*) FILTER (WHERE g.abandoned_at IS NOT NULL)         AS abandoned,
    AVG(g.duration_seconds) FILTER (WHERE g.completed_at IS NOT NULL)::INTEGER AS avg_duration_seconds
FROM game_sessions g
WHERE g.started_at >= NOW() - INTERVAL '90 days'
GROUP BY g.character, g.device_type;

-- ============================================================
-- 분석용 보조 인덱스
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_emotion_logs_user_time
    ON emotion_logs(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_emotion_logs_track_recommend
    ON emotion_logs(recommended_track, created_at DESC)
    WHERE recommended_track IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_game_sessions_started_status
    ON game_sessions(started_at DESC, character)
    WHERE completed_at IS NULL AND abandoned_at IS NULL;
