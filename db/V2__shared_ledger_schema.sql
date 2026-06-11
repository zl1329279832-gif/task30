-- =====================================================================
-- V2: Multi-user Shared Ledger & Periodic Budget Settlement
-- =====================================================================

-- A1. Modify tb_record_book
ALTER TABLE tb_record_book
    ADD COLUMN book_type TINYINT NOT NULL DEFAULT 0 COMMENT '0=personal, 1=shared',
    ADD COLUMN invite_code VARCHAR(16) DEFAULT NULL COMMENT '8-char random invite code (shared books only)',
    ADD COLUMN owner_user_id INT DEFAULT NULL COMMENT 'Creator userId for shared books';

-- A2. Modify tb_record_detail
ALTER TABLE tb_record_detail
    ADD COLUMN review_status TINYINT NOT NULL DEFAULT 0 COMMENT '0=no_review(personal), 1=pending, 2=posted, 3=rejected, 4=reversed',
    ADD COLUMN reviewer_id INT DEFAULT NULL COMMENT 'Who reviewed/rejected/reversed',
    ADD COLUMN review_time DATETIME DEFAULT NULL,
    ADD COLUMN review_remark VARCHAR(500) DEFAULT NULL COMMENT 'Rejection reason or review note',
    ADD COLUMN original_record_id BIGINT DEFAULT NULL COMMENT 'Points to original record for reversal counter-entries',
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus optimistic lock @Version';

-- A3. Shared book members
CREATE TABLE IF NOT EXISTS tb_shared_book_member (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id      INT          NOT NULL COMMENT 'FK tb_record_book.id',
    user_id      INT          NOT NULL,
    permissions  VARCHAR(64)  NOT NULL DEFAULT 'entry,view'
        COMMENT 'Comma-separated: entry, review, view, settlement',
    nickname     VARCHAR(64)  DEFAULT NULL COMMENT 'Display name in this book',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0=normal, 1=removed',
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    delete_time  DATETIME     DEFAULT NULL,
    INDEX idx_book_id (book_id),
    INDEX idx_user_id (user_id),
    UNIQUE KEY uk_book_user (book_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Shared book members';

-- A4. Monthly budgets per book
CREATE TABLE IF NOT EXISTS tb_book_budget (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id        INT           NOT NULL,
    year_month     VARCHAR(7)    NOT NULL COMMENT 'yyyy-MM',
    budget_amount  DECIMAL(12,2) NOT NULL,
    used_amount    DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'DB-side redundancy',
    warn_threshold TINYINT       NOT NULL DEFAULT 80 COMMENT 'Warning at N%',
    status         TINYINT       NOT NULL DEFAULT 0,
    create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME      DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    delete_time    DATETIME      DEFAULT NULL,
    UNIQUE KEY uk_book_month (book_id, year_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Monthly budgets per book';

-- A5. Monthly closing locks
CREATE TABLE IF NOT EXISTS tb_monthly_closing (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id      INT           NOT NULL,
    year_month   VARCHAR(7)    NOT NULL,
    closed_by    INT           NOT NULL,
    closed_time  DATETIME      NOT NULL,
    total_income DECIMAL(12,2) DEFAULT 0.00 COMMENT 'Immutable snapshot',
    total_expend DECIMAL(12,2) DEFAULT 0.00 COMMENT 'Immutable snapshot',
    remark       VARCHAR(500)  DEFAULT NULL,
    status       TINYINT       NOT NULL DEFAULT 0,
    create_time  DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME      DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    delete_time  DATETIME      DEFAULT NULL,
    UNIQUE KEY uk_book_month (book_id, year_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Monthly closing locks';

-- A6. Reversal workflow
CREATE TABLE IF NOT EXISTS tb_reversal_request (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id            INT          NOT NULL,
    original_record_id BIGINT       NOT NULL,
    requester_id       INT          NOT NULL COMMENT 'Must be original creator',
    reviewer_id        INT          DEFAULT NULL,
    request_reason     VARCHAR(500) NOT NULL,
    review_status      TINYINT      NOT NULL DEFAULT 1
        COMMENT '1=pending, 2=approved, 3=rejected',
    review_remark      VARCHAR(500) DEFAULT NULL,
    review_time        DATETIME     DEFAULT NULL,
    reversal_record_id BIGINT       DEFAULT NULL COMMENT 'Counter-entry ID',
    status             TINYINT      NOT NULL DEFAULT 0,
    create_time        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    delete_time        DATETIME     DEFAULT NULL,
    INDEX idx_book_id (book_id),
    INDEX idx_original_record (original_record_id),
    INDEX idx_requester (requester_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Reversal workflow';

-- A7. Append-only audit trail (no update_time, no delete_time)
CREATE TABLE IF NOT EXISTS tb_shared_book_audit_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id     INT          NOT NULL,
    operator_id INT          NOT NULL,
    action_type VARCHAR(32)  NOT NULL
        COMMENT 'MEMBER_ADD,MEMBER_REMOVE,PERMISSION_CHANGE,RECORD_SUBMIT,RECORD_REVIEW,RECORD_REJECT,RECORD_REVERSE,BUDGET_SET,BUDGET_WARN,MONTHLY_CLOSE,REVERSAL_REQUEST,REVERSAL_APPROVE,REVERSAL_REJECT',
    target_type VARCHAR(32)  DEFAULT NULL COMMENT 'MEMBER,RECORD,BUDGET,CLOSING',
    target_id   BIGINT       DEFAULT NULL,
    detail      TEXT         DEFAULT NULL COMMENT 'JSON payload',
    ip_address  VARCHAR(64)  DEFAULT NULL,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_book_id (book_id),
    INDEX idx_operator (operator_id),
    INDEX idx_action_type (action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Append-only audit trail';
