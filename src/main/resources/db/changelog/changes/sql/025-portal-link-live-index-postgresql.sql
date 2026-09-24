CREATE UNIQUE INDEX ux_portal_link_task_live
ON portal_link (task_id)
WHERE status IN ('PENDING_SEND', 'ACTIVE', 'NOT_SENT');
