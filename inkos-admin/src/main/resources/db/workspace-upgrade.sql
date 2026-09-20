-- Run once per existing database, then sign in again to refresh permission snapshots.
-- Re-running is safe. Serialize schema/permission upgrades in your deployment.
START TRANSACTION;
INSERT INTO sys_menu (parent_id, menu_name, perms, menu_type, path, sort_order)
SELECT 0, '标签管理', 'content:tag:list', 'C', '/console/tags', 4
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'content:tag:list');
INSERT INTO sys_menu (parent_id, menu_name, perms, menu_type)
SELECT m.id, x.label, x.perms, 'F' FROM sys_menu m
JOIN (SELECT '新增标签' label, 'content:tag:add' perms UNION ALL SELECT '编辑标签','content:tag:edit' UNION ALL SELECT '删除标签','content:tag:remove') x
WHERE m.perms = 'content:tag:list' AND NOT EXISTS (SELECT 1 FROM sys_menu e WHERE e.perms = x.perms);
INSERT INTO sys_menu (parent_id, menu_name, perms, menu_type)
SELECT m.id, '恢复文章', 'content:article:restore', 'F' FROM sys_menu m
WHERE m.perms = 'content:article:list' AND NOT EXISTS (SELECT 1 FROM sys_menu e WHERE e.perms = 'content:article:restore');
INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT DISTINCT rm.role_id, target.id FROM sys_role_menu rm JOIN sys_menu source ON source.id=rm.menu_id
JOIN sys_menu target ON target.perms = REPLACE(source.perms, 'content:category:', 'content:tag:')
WHERE source.perms LIKE 'content:category:%';
INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT DISTINCT rm.role_id, target.id FROM sys_role_menu rm JOIN sys_menu source ON source.id=rm.menu_id
JOIN sys_menu target ON target.perms='content:article:restore'
WHERE source.perms='content:article:remove';
COMMIT;
