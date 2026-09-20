-- Idempotent MySQL upgrade. Run serially before deploying the new backend.
-- Existing custom text and explicitly cleared hero copy are preserved.
SET @hero_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'cms_quote' AND column_name = 'headline') = 0, 'ALTER TABLE cms_quote ADD COLUMN headline VARCHAR(80) DEFAULT NULL', 'SELECT 1');
PREPARE hero_stmt FROM @hero_ddl;
EXECUTE hero_stmt;
DEALLOCATE PREPARE hero_stmt;
SET @hero_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'cms_quote' AND column_name = 'description') = 0, 'ALTER TABLE cms_quote ADD COLUMN description VARCHAR(240) DEFAULT NULL', 'SELECT 1');
PREPARE hero_stmt FROM @hero_ddl;
EXECUTE hero_stmt;
DEALLOCATE PREPARE hero_stmt;
UPDATE cms_quote SET headline = CONCAT('写下所知，', CHAR(10), '为未知留白。'), description = CONCAT('记下技术的推敲、阅读的回响，', CHAR(10), '也记下生活里，值得停笔的瞬间。') WHERE content = '把时间折进一页纸。' AND headline IS NULL AND description IS NULL AND deleted = 0;
UPDATE cms_quote SET headline = CONCAT('读得慢些，', CHAR(10), '想得更远。'), description = CONCAT('让匆忙停在纸页之外，', CHAR(10), '在字里行间，遇见新的理解。') WHERE content = '读得慢一点，世界会显出更多纹理。' AND headline IS NULL AND description IS NULL AND deleted = 0;
UPDATE cms_quote SET headline = CONCAT('把日常写下，', CHAR(10), '让灵感生长。'), description = CONCAT('从一个问题，到一次实践，', CHAR(10), '把微小的发现，写成自己的答案。') WHERE content = '写下所知，也为未知留白。' AND headline IS NULL AND description IS NULL AND deleted = 0;
UPDATE cms_quote SET headline = CONCAT('以文字为舟，', CHAR(10), '向更深处去。'), description = CONCAT('整理走过的路，也记录新的起点，', CHAR(10), '让每一次落笔，都有所回响。') WHERE content = '思想落在纸上，才开始拥有方向。' AND headline IS NULL AND description IS NULL AND deleted = 0;
UPDATE cms_quote SET headline = CONCAT('一页一世界，', CHAR(10), '一读一相逢。'), description = CONCAT('分享技术与生活的片段，', CHAR(10), '让远处的思考，在这里相遇。') WHERE content = '愿每一次阅读，都抵达更深处。' AND headline IS NULL AND description IS NULL AND deleted = 0;
