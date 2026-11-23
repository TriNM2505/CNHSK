-- Premium keeps learning freely until an administrator sets real per-feature limits on the plan.
-- Existing keys win, so a limit already set by hand is never overwritten.
UPDATE learning.plans
SET benefits = '{"VOCAB":1000,"GRAMMAR":1000,"PRONUNCIATION":1000,"HANZI":1000}'::jsonb || benefits
WHERE code = 'PREMIUM';
