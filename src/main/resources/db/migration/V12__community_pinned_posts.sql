-- Bai cua kiem duyet vien: dang thang (khong qua hang cho), ghim dau bang tin toi pinned_until,
-- co the gan voi mot cuoc thi. Hai chu de moi chi kiem duyet vien duoc dung: CONTEST, ANNOUNCEMENT.
ALTER TABLE community.posts DROP CONSTRAINT posts_category_check;
ALTER TABLE community.posts ADD CONSTRAINT ck_post_category CHECK (category IN
    ('EXPERIENCE','QUESTION','STUDY_BUDDY','CULTURE','JOB','CONTEST','ANNOUNCEMENT'));

ALTER TABLE community.posts ADD COLUMN pinned_until TIMESTAMPTZ;
ALTER TABLE community.posts ADD COLUMN contest_id BIGINT REFERENCES community.contests(id);

CREATE INDEX ix_post_pinned ON community.posts(pinned_until DESC)
    WHERE status = 'APPROVED' AND pinned_until IS NOT NULL;
