-- =====================================================================
-- V3: Cross-Period Budget Carryover & Member Responsibility Tracking
-- =====================================================================

-- 预算结转规则 (版本化)
CREATE TABLE IF NOT EXISTS tb_budget_carryover_rule (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id               INT           NOT NULL,
    version               INT           NOT NULL DEFAULT 1 COMMENT '规则版本号(每账本递增)',
    carryover_type        VARCHAR(16)   NOT NULL DEFAULT 'FULL'
        COMMENT 'FULL=全额结转, PERCENTAGE=按比例, CAPPED=限额结转',
    carryover_percent     INT           DEFAULT 100 COMMENT '0-100, 仅PERCENTAGE类型使用',
    cap_amount            DECIMAL(12,2) DEFAULT NULL COMMENT '结转上限金额, 仅CAPPED类型使用',
    carry_overspend       TINYINT       NOT NULL DEFAULT 0 COMMENT '1=超支结转到下月, 0=不结转',
    pending_record_policy VARCHAR(16)   NOT NULL DEFAULT 'IGNORE'
        COMMENT 'IGNORE=忽略待审核, RESERVE=预留待审核金额',
    effective_from        VARCHAR(7)    NOT NULL COMMENT '生效起始月份 yyyy-MM',
    created_by            INT           NOT NULL,
    status                TINYINT       NOT NULL DEFAULT 0 COMMENT '0=生效, 1=已替代',
    create_time           DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time           DATETIME      DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    delete_time           DATETIME      DEFAULT NULL,
    INDEX idx_book_id (book_id),
    INDEX idx_book_effective (book_id, effective_from),
    UNIQUE KEY uk_book_version (book_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预算结转规则(版本化)';

-- 预算结转记录 (月结时生成, 不可变)
CREATE TABLE IF NOT EXISTS tb_budget_carryover (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id               INT           NOT NULL,
    source_year_month     VARCHAR(7)    NOT NULL COMMENT '结转来源月份',
    target_year_month     VARCHAR(7)    NOT NULL COMMENT '结转目标月份',
    rule_id               BIGINT        DEFAULT NULL COMMENT '使用的规则ID',
    budget_amount         DECIMAL(12,2) NOT NULL COMMENT '来源月原始预算',
    used_amount           DECIMAL(12,2) NOT NULL COMMENT '来源月已使用金额',
    pending_reserve       DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '待审核预留金额',
    raw_carryover         DECIMAL(12,2) NOT NULL COMMENT '原始结余 = 预算 - 已用 - 预留',
    applied_carryover     DECIMAL(12,2) NOT NULL COMMENT '应用规则后的结转金额',
    overspend_carryover   DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '超支结转金额(负数)',
    closing_id            BIGINT        NOT NULL COMMENT '关联月结记录ID',
    status                TINYINT       NOT NULL DEFAULT 0,
    create_time           DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time           DATETIME      DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    delete_time           DATETIME      DEFAULT NULL,
    UNIQUE KEY uk_book_source_month (book_id, source_year_month),
    INDEX idx_closing_id (closing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预算结转记录';

-- 成员责任快照 (月结时冻结)
CREATE TABLE IF NOT EXISTS tb_member_responsibility_snapshot (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id               INT           NOT NULL,
    year_month            VARCHAR(7)    NOT NULL,
    closing_id            BIGINT        NOT NULL COMMENT '关联月结记录ID',
    user_id               INT           NOT NULL COMMENT '成员ID(月结时)',
    user_nickname         VARCHAR(64)   DEFAULT NULL COMMENT '成员昵称快照',
    user_permissions      VARCHAR(64)   DEFAULT NULL COMMENT '成员权限快照',
    record_category       VARCHAR(64)   DEFAULT NULL COMMENT '分类维度',
    record_account_id     INT           DEFAULT NULL COMMENT '账户维度(null=汇总)',
    total_income          DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_expend          DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    record_count          INT           NOT NULL DEFAULT 0,
    status                TINYINT       NOT NULL DEFAULT 0,
    create_time           DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time           DATETIME      DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    delete_time           DATETIME      DEFAULT NULL,
    INDEX idx_book_month (book_id, year_month),
    INDEX idx_user (user_id),
    INDEX idx_closing (closing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成员责任快照';

-- 月结调整单 (冲正/补审核产生, 不改写历史)
CREATE TABLE IF NOT EXISTS tb_closing_adjustment (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id               INT           NOT NULL,
    year_month            VARCHAR(7)    NOT NULL COMMENT '受影响的已结月份',
    closing_id            BIGINT        NOT NULL COMMENT '关联月结记录ID',
    adjustment_type       VARCHAR(32)   NOT NULL COMMENT 'REVERSAL=冲正, SUPPLEMENTARY_AUDIT=补审核',
    source_record_id      BIGINT        NOT NULL COMMENT '触发调整的记录ID',
    counter_record_id     BIGINT        DEFAULT NULL COMMENT '冲正反向条目ID',
    user_id               INT           NOT NULL COMMENT '责任归属成员ID',
    record_category       VARCHAR(64)   DEFAULT NULL,
    record_account_id     INT           DEFAULT NULL,
    adjustment_income     DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '收入调整量',
    adjustment_expend     DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '支出调整量',
    budget_impact         DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '预算影响量',
    idempotency_key       VARCHAR(128)  NOT NULL COMMENT '幂等键',
    operator_id           INT           NOT NULL COMMENT '操作人ID',
    remark                VARCHAR(500)  DEFAULT NULL,
    status                TINYINT       NOT NULL DEFAULT 0,
    create_time           DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time           DATETIME      DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    delete_time           DATETIME      DEFAULT NULL,
    UNIQUE KEY uk_idempotency (idempotency_key),
    INDEX idx_book_month (book_id, year_month),
    INDEX idx_source_record (source_record_id),
    INDEX idx_closing (closing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='月结调整单';
