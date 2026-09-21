-- AgentScope Java 2.x state, isolated from legacy agentscope_sessions.
CREATE TABLE IF NOT EXISTS `agentscope_agent_state` (
    `session_id` VARCHAR(255) NOT NULL,
    `state_key` VARCHAR(255) NOT NULL,
    `item_index` INT NOT NULL DEFAULT 0,
    `state_data` LONGTEXT NOT NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`session_id`, `state_key`, `item_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @migration_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mcp_server' AND COLUMN_NAME = 'enable_tools'),
    'SELECT 1', 'ALTER TABLE `mcp_server` ADD COLUMN `enable_tools` TEXT DEFAULT NULL'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mcp_server' AND COLUMN_NAME = 'disable_tools'),
    'SELECT 1', 'ALTER TABLE `mcp_server` ADD COLUMN `disable_tools` TEXT DEFAULT NULL'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
