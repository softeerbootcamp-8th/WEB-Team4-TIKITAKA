-- 기존 대기 행은 스케줄러가 DB에서 정리할 수 있도록 보존한다.
-- 새 발급 행은 애플리케이션에서 아래 값을 모두 채운다.
ALTER TABLE pending_auction_image
    ADD COLUMN upload_id BINARY(16) NULL AFTER draft_id,
    ADD COLUMN content_type VARCHAR(100) NULL AFTER object_key,
    ADD COLUMN content_length BIGINT NULL AFTER content_type,
    ADD COLUMN checksum_sha256 CHAR(44) NULL AFTER content_length,
    ADD CONSTRAINT uk_pending_auction_image_upload_id UNIQUE (upload_id);
