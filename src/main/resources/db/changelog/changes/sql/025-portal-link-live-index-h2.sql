ALTER TABLE portal_link
ADD live_task_id varchar(64)
GENERATED ALWAYS AS (CASE WHEN status IN ('PENDING_SEND', 'ACTIVE', 'NOT_SENT') THEN task_id ELSE NULL END);
CREATE UNIQUE INDEX ux_portal_link_task_live
ON portal_link (live_task_id);
