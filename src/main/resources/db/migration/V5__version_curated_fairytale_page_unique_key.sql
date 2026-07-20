alter table curated_fairytale_pages
    drop constraint if exists curated_fairytale_pages_fairytale_id_page_index_key;

alter table curated_fairytale_pages
    add constraint uq_curated_fairytale_pages_version_page
    unique (fairytale_id, content_version, page_index);
