-- =====================================================================
-- V3: Budget Carryforward & Monthly Settlement Enhancements
-- =====================================================================

-- A1. Budget carryforward rules (versioned per period)
CREATE TABLE IF NOT EXISTS tb_budget_carryforward_rule (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id                INT          NOT NULL,
    year_month             VARCHAR(7)   NOT NULL COMMENT 'Effective period yyyy-MM / 生效期间 yyyy-MM',
    rule_version           INT          NOT NULL DEFAULT 1 COMMENT 'Rule version number / 规则版本号',
    rule_type              VARCHAR(16)  NOT NULL COMMENT 'FULL/PARTIAL/NONE/CUSTOM',
    carryforward_rate      DECIMAL(5,4) NOT NULL DEFAULT 1.0000 COMMENT 'Carryforward ratio 0.0000~1.0000 / 结转比例 0.0000~1.0000',
    max_carryforward_amount BIGINT      DEFAULT NULL COMMENT 'Max carryforward amount in cents, NULL=unlimited / 最大结转金额(分) NULL=无上限',
    expire_months          INT          DEFAULT NULL COMMENT 'Months until carryforward expires, NULL=never / 结转金额过期月数 NULL=永不过期',
    include_pending        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT 'Include pending records in carryforward calc / 是否将待审核记录预算影响纳入结转',
    overspent_mode         VARCHAR(16)  NOT NULL DEFAULT 'CARRY_DEBT' COMMENT 'CARRY_DEBT/WRITE_OFF/CAP_AT_ZERO',
    category_filter        JSON         DEFAULT NULL COMMENT 'Category whitelist for carryforward, NULL=all / 允许结转的分类白名单 NULL=全部',
    created_by             INT          NOT NULL,
    created_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_book_period_ver (book_id, year_month, rule_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Budget carryforward rules / 预算结转规则版本表';

-- A2. Budget carryforward execution log
CREATE TABLE IF NOT EXISTS tb_budget_carryforward_log (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id               INT         NOT NULL,
    source_year_month     VARCHAR(7)  NOT NULL COMMENT 'Source period / 源期间',
    target_year_month     VARCHAR(7)  NOT NULL COMMENT 'Target period / 目标期间',
    source_budget_id      BIGINT      DEFAULT NULL COMMENT 'Source budget line ID, NULL=summary / 源预算行ID NULL=汇总行',
    category_id           BIGINT      DEFAULT NULL COMMENT 'Category ID / 分类ID',
    account_id            BIGINT      DEFAULT NULL COMMENT 'Account ID / 账户ID',
    member_user_id        INT         DEFAULT NULL COMMENT 'Responsible member ID, NULL=shared / 责任成员ID NULL=公共',
    original_amount       BIGINT      NOT NULL COMMENT 'Original budget in cents / 原始预算(分)',
    used_amount           BIGINT      NOT NULL COMMENT 'Used amount in cents / 已用金额(分)',
    carryforward_amount   BIGINT      NOT NULL COMMENT 'Carryforward amount in cents, positive=surplus, negative=overspent / 结转金额(分) 正=结余 负=超支',
    pending_impact_amount BIGINT      NOT NULL DEFAULT 0 COMMENT 'Pending review impact in cents / 待审核影响(分)',
    rule_version          INT         NOT NULL,
    rule_type             VARCHAR(16) NOT NULL,
    status                TINYINT     NOT NULL DEFAULT 1 COMMENT '1=active, 2=rolled_back / 1=生效 2=已回滚',
    created_time          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_book_src (book_id, source_year_month),
    INDEX idx_book_tgt (book_id, target_year_month),
    INDEX idx_member (book_id, member_user_id, source_year_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Budget carryforward execution log / 预算结转执行明细日志';

-- A3. Difference adjustment records
CREATE TABLE IF NOT EXISTS tb_difference_adjustment_record (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id                INT          NOT NULL,
    source_year_month      VARCHAR(7)   NOT NULL COMMENT 'Closed source period / 已关闭源期间',
    target_year_month      VARCHAR(7)   NOT NULL COMMENT 'Period adjustment applies to / 调整计入期间',
    original_record_id     BIGINT       DEFAULT NULL COMMENT 'Related original record ID / 关联原始记录ID',
    reversal_request_id    BIGINT       DEFAULT NULL COMMENT 'Related reversal request ID / 关联冲销请求ID',
    adjustment_type        VARCHAR(24)  NOT NULL COMMENT 'REVERSAL_DIFF/SUPPLEMENT/AUDIT_DIFF/CORRECTION',
    adjustment_amount      BIGINT       NOT NULL COMMENT 'Adjustment amount in cents, positive=add_expense, negative=reduce_expense / 调整金额(分) 正=增支 负=减支',
    category_id            BIGINT       DEFAULT NULL,
    account_id             BIGINT       DEFAULT NULL,
    member_user_id         INT          DEFAULT NULL COMMENT 'Responsible member / 责任成员',
    reason                 VARCHAR(512) NOT NULL,
    related_closing_id     BIGINT       NOT NULL COMMENT 'Related monthly closing ID / 关联的月结ID',
    review_status          TINYINT      NOT NULL DEFAULT 2 COMMENT '2=posted, 4=reversed / 2=已过账 4=已冲销',
    adjustment_record_id   BIGINT       DEFAULT NULL COMMENT 'Generated adjustment entry ID in record_detail / 在record_detail中生成的调整分录ID',
    idempotency_key        VARCHAR(64)  NOT NULL COMMENT 'Idempotency key / 幂等键',
    created_by             INT          NOT NULL,
    created_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_idempotency (idempotency_key),
    INDEX idx_book_src (book_id, source_year_month),
    INDEX idx_book_tgt (book_id, target_year_month),
    INDEX idx_original (original_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Difference adjustment records / 差额调整记录';

-- A4. Member settlement snapshots
CREATE TABLE IF NOT EXISTS tb_member_settlement_snapshot (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id           INT         NOT NULL,
    year_month        VARCHAR(7)  NOT NULL,
    member_user_id    INT         NOT NULL,
    total_income      BIGINT      NOT NULL DEFAULT 0 COMMENT 'Member period income in cents / 成员当期收入(分)',
    total_expend      BIGINT      NOT NULL DEFAULT 0 COMMENT 'Member period expenditure in cents / 成员当期支出(分)',
    net_responsibility BIGINT     NOT NULL DEFAULT 0 COMMENT 'Net responsibility = income - expend in cents / 净责任=income-expend(分)',
    pending_count     INT         NOT NULL DEFAULT 0,
    pending_amount    BIGINT      NOT NULL DEFAULT 0,
    budget_used       BIGINT      NOT NULL DEFAULT 0 COMMENT 'Member period budget usage in cents / 成员当期预算使用(分)',
    budget_overspent  BIGINT      NOT NULL DEFAULT 0 COMMENT 'Member period overspent amount in cents / 成员当期超支(分)',
    snapshot_version  INT         NOT NULL DEFAULT 1,
    recalc_flag       TINYINT(1)  NOT NULL DEFAULT 0 COMMENT 'Whether recalculated / 是否经重算',
    created_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_book_period_member_ver (book_id, year_month, member_user_id, snapshot_version),
    INDEX idx_member_history (member_user_id, book_id, year_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Member settlement snapshots / 成员责任快照';

-- A5. Monthly closing recalculation records
CREATE TABLE IF NOT EXISTS tb_monthly_closing_recalculation (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id                 INT          NOT NULL,
    year_month              VARCHAR(7)   NOT NULL,
    recalc_version          INT          NOT NULL DEFAULT 2 COMMENT 'Version after recalculation / 重算后版本号',
    triggered_by            INT          NOT NULL,
    trigger_reason          VARCHAR(256) NOT NULL,
    previous_total_income   BIGINT       NOT NULL,
    previous_total_expend   BIGINT       NOT NULL,
    new_total_income        BIGINT       NOT NULL,
    new_total_expend        BIGINT       NOT NULL,
    delta_income            BIGINT       NOT NULL DEFAULT 0,
    delta_expend            BIGINT       NOT NULL DEFAULT 0,
    related_adjustment_ids  JSON         DEFAULT NULL COMMENT 'Triggered adjustment record ID list / 触发的调整记录ID列表',
    created_time            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_book_period_ver (book_id, year_month, recalc_version),
    INDEX idx_book_period (book_id, year_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Monthly closing recalculation records / 月结快照重算记录';

-- =====================================================================
-- Alterations to existing tables
-- =====================================================================

-- M1. Alter tb_book_budget: add carryforward columns
ALTER TABLE tb_book_budget
    ADD COLUMN carryforward_amount  BIGINT      NOT NULL DEFAULT 0 COMMENT 'Carryforward from previous period in cents / 来自上期的结转(分)',
    ADD COLUMN source_year_month    VARCHAR(7)  DEFAULT NULL COMMENT 'Carryforward source period / 结转来源期间',
    ADD COLUMN rule_version         INT         DEFAULT NULL COMMENT 'Rule version that produced the carryforward / 产生结转的规则版本';

-- M2. Alter tb_monthly_closing: add snapshot version and carryforward tracking
ALTER TABLE tb_monthly_closing
    ADD COLUMN snapshot_version       INT       NOT NULL DEFAULT 1,
    ADD COLUMN carryforward_executed  TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN carryforward_total     BIGINT    NOT NULL DEFAULT 0,
    ADD COLUMN overspent_total        BIGINT    NOT NULL DEFAULT 0,
    ADD COLUMN pending_impact_total   BIGINT    NOT NULL DEFAULT 0,
    ADD COLUMN last_recalc_time       DATETIME  DEFAULT NULL;

-- M3. Alter tb_reversal_request: add cross-period and adjustment tracking
ALTER TABLE tb_reversal_request
    ADD COLUMN cross_period           TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN adjustment_record_id   BIGINT     DEFAULT NULL,
    ADD COLUMN source_year_month      VARCHAR(7) DEFAULT NULL;
