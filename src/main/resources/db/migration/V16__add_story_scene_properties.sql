ALTER TABLE story_scenes
    ADD COLUMN properties_json jsonb NOT NULL DEFAULT '{}'::jsonb;
