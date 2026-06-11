-- ========== 共享账本功能迁移脚本 ==========

-- 修改现有表
ALTER TABLE tb_record_book ADD COLUMN book_type INT NOT NULL DEFAULT 1 COMMENT '账本类型：1=个人, 2=共享';
ALTER TABLE tb_record_detail ADD COLUMN audit_status INT DEFAULT NULL COMMENT '审核状态：1=待审核, 2=已入账, 3=已驳回, 4=已冲正';
ALTER TABLE tb_record_detail ADD COLUMN auditor_id INT DEFAULT NULL COMMENT '审核人ID';
ALTER TABLE tb_record_detail ADD COLUMN audit_time DATETIME DEFAULT NULL COMMENT '审核时间';
ALTER TABLE tb_record_detail ADD COLUMN payer_user_id INT DEFAULT NULL COMMENT '实际垫付人ID';

-- 共享账本成员表
CREATE TABLE tb_shared_book_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_book_id INT NOT NULL COMMENT '共享账本ID',
    user_id INT NOT NULL COMMENT '成员用户ID',
    permission VARCHAR(100) NOT NULL COMMENT '权限：record,audit,view,settle 逗号分隔',
    nickname VARCHAR(50) DEFAULT NULL COMMENT '在该账本中的昵称',
    join_time DATETIME DEFAULT NULL COMMENT '加入时间',
    status INT NOT NULL DEFAULT 0 COMMENT '状态：0=正常, 1=已退出',
    create_time DATETIME DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    delete_time DATETIME DEFAULT NULL,
    UNIQUE KEY uk_book_user (record_book_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='共享账本成员表';

-- 共享账本邀请表
CREATE TABLE tb_shared_book_invitation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_book_id INT NOT NULL COMMENT '共享账本ID',
    inviter_user_id INT NOT NULL COMMENT '邀请人ID',
    invitee_user_id INT DEFAULT NULL COMMENT '被邀请人ID',
    invite_code VARCHAR(32) NOT NULL COMMENT '邀请码',
    permission VARCHAR(100) NOT NULL COMMENT '邀请附带的权限',
    expire_time DATETIME NOT NULL COMMENT '过期时间',
    status INT NOT NULL DEFAULT 0 COMMENT '0=待接受, 1=已接受, 2=已过期, 3=已取消',
    create_time DATETIME DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    delete_time DATETIME DEFAULT NULL,
    UNIQUE KEY uk_invite_code (invite_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='共享账本邀请表';

-- 预算表
CREATE TABLE tb_budget (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_book_id INT NOT NULL COMMENT '账本ID',
    budget_type INT NOT NULL DEFAULT 1 COMMENT '预算类型：1=总预算, 2=分类预算, 3=成员预算',
    category_name VARCHAR(50) DEFAULT NULL COMMENT '预算类别名称',
    member_user_id INT DEFAULT NULL COMMENT '成员用户ID',
    year_month VARCHAR(7) NOT NULL COMMENT '预算月份 yyyy-MM',
    budget_amount DOUBLE NOT NULL COMMENT '预算金额',
    warn_percent INT NOT NULL DEFAULT 80 COMMENT '预警百分比阈值',
    status INT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    delete_time DATETIME DEFAULT NULL,
    INDEX idx_book_month (record_book_id, year_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预算表';

-- 月结锁定表
CREATE TABLE tb_month_lock (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_book_id INT NOT NULL COMMENT '账本ID',
    year_month VARCHAR(7) NOT NULL COMMENT '锁定月份 yyyy-MM',
    locked_by INT NOT NULL COMMENT '锁定操作人ID',
    lock_time DATETIME NOT NULL COMMENT '锁定时间',
    status INT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    delete_time DATETIME DEFAULT NULL,
    UNIQUE KEY uk_book_month (record_book_id, year_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='月结锁定表';

-- 冲正申请表
CREATE TABLE tb_reversal_application (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_book_id INT NOT NULL COMMENT '账本ID',
    original_record_id BIGINT NOT NULL COMMENT '原始记录ID',
    reversal_record_id BIGINT DEFAULT NULL COMMENT '冲正记录ID',
    applicant_user_id INT NOT NULL COMMENT '申请人ID',
    reason VARCHAR(500) NOT NULL COMMENT '冲正原因',
    audit_status INT NOT NULL DEFAULT 1 COMMENT '1=待审核, 2=已通过, 3=已驳回',
    auditor_id INT DEFAULT NULL COMMENT '审核人ID',
    audit_time DATETIME DEFAULT NULL,
    audit_remark VARCHAR(500) DEFAULT NULL COMMENT '审核备注',
    status INT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    delete_time DATETIME DEFAULT NULL,
    INDEX idx_book_id (record_book_id),
    INDEX idx_original_record (original_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='冲正申请表';

-- 成员结算表
CREATE TABLE tb_settlement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_book_id INT NOT NULL COMMENT '账本ID',
    year_month VARCHAR(7) NOT NULL COMMENT '结算月份',
    from_user_id INT NOT NULL COMMENT '应付款人ID',
    to_user_id INT NOT NULL COMMENT '应收款人ID',
    amount DOUBLE NOT NULL COMMENT '结算金额',
    settle_status INT NOT NULL DEFAULT 1 COMMENT '1=待结算, 2=已结算',
    settled_by INT DEFAULT NULL COMMENT '确认结算操作人',
    settle_time DATETIME DEFAULT NULL COMMENT '结算确认时间',
    status INT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    delete_time DATETIME DEFAULT NULL,
    INDEX idx_book_month (record_book_id, year_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成员结算表';

-- 共享账本操作日志表
CREATE TABLE tb_shared_book_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_book_id INT NOT NULL COMMENT '共享账本ID',
    oper_user_id INT NOT NULL COMMENT '操作人ID',
    oper_type VARCHAR(30) NOT NULL COMMENT '操作类型',
    target_id BIGINT DEFAULT NULL COMMENT '目标对象ID',
    content VARCHAR(500) DEFAULT NULL COMMENT '操作描述',
    extra_data VARCHAR(2000) DEFAULT NULL COMMENT '扩展JSON数据',
    status INT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    delete_time DATETIME DEFAULT NULL,
    INDEX idx_book_id (record_book_id),
    INDEX idx_oper_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='共享账本操作日志';
