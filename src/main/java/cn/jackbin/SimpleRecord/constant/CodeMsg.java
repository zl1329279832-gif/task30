package cn.jackbin.SimpleRecord.constant;

/**
 * 全局错误码
 **/
public enum CodeMsg {
    
    // 按照模块定义CodeMsg
    // 成功
    SUCCESS(0,"success"),
    // 通用异常
    ERROR(-1,"接口调用异常"),
    FAILED(100,"接口调用失败"),
    SERVER_EXCEPTION(101,"服务端异常"),
    JWT_EXCEPTION(102,"JWT校验异常"),
    WITHOUT_PERMISSION(103, "权限不足，请联系管理员"),
    TOKEN_EXPIRED(104, "token失效"),



    // 通用业务 格式500 xxx
    BUSINESS_ERROR(500000,"业务异常"),
    ONLINE_USER_OVER(500003,"在线用户数超出允许登录的最大用户限制。"),
    NOT_FIND_DATA(500005,"查找不到对应数据"),
    VERIFY_CODE_ERROR(500006,"验证码错误"),
    PARAMETER_ISNULL(500007,"参数为空"),
    PARAMETER_ILLEGAL(500008,"参数不合法"),
    UPLOAD_IMAGE_ILLEGAL(500009,"上传的图片不能为空"),
    EMPTY_PAGE_SIZE_OR_PAGE_INDEX(500010,"分页大小或页码为空"),
    ADD_DATA_ERROR(500011,"添加数据失败"),
    EDIT_DATA_ERROR(500012,"编辑数据失败"),
    OPERATE_FAILED(500013,"本次操作失败"),
    CANT_OPERATE_SYS_DATA(500014,"系统内置数据不可操作"),


    // 系统相关 格式600 xxx
    LOGIN_ERROR(600001,"用户名或密码错误，请重新登录"),
    USERNAME_EXIST(600002,"用户名重复"),
    PSW_FORMAT_ERROR(600003,"密码是由8至16位的数字和字母组成，请重新输入"),
    SEX_FORMAT_ERROR(600003,"性别未识别"),
    ROLE_NAME_EXIST(600004, "角色名重复"),
    ROLE_EDIT_NOT_ALLOWED(600005, "系统内置角色请勿删除或改名"),
    DICT_CODE_EXIST(600006, "字典编码重复"),

    // 记账相关 格式700 xxx
    INSERT_RECORD_ERROR(700001,"新增记账记录失败"),
    UPDATE_RECORD_ERROR(700002,"更新记账记录失败"),
    OPERATE_RECORD_FORBIDDEN(700003,"禁止操作他人记账记录"),
    DEL_RECORD_ERROR(700004,"删除记账记录失败"),
    RECORD_TYPE_CODE_ERROR(700005,"记账类型编码错误"),
    RECORD_ACCOUNT_SIZE_TOO_MUCH(700006,"记账账户请勿过多"),
    DEL_RECORD_BOOK_ERROR(700007,"删除账单失败"),
    ONE_DEFAULT_RECORD_BOOK(700008,"默认账单有且仅有一个"),
    OPERATE_RECORD_ACCOUNT_FORBIDDEN(700009,"禁止操作他人记账账户"),
    SOURCE_ACCOUNT_NOT_NULL(700010,"源账户不能为空"),
    SOURCE_CANT_EQUAL_TARGET_ACCOUNT(700011,"转出与转入账户不能相同"),
    OPERATE_RECORD_BOOK_FORBIDDEN(7000012,"禁止操作他人记账账单"),
    TARGET_RECORD_ACCOUNT_NOT_PAYMENT(700013, "目标账户不能为应收应付类型"),
    SOURCE_RECORD_ACCOUNT_PAYMENT_ONLY(700014, "源账户只能为应收应付类型"),
    SOURCE_RECORD_ACCOUNT_NOT_PAYMENT(700015, "源账户不能为应收应付类型"),
    TARGET_RECORD_ACCOUNT_PAYMENT_ONLY(700016, "目标账户只能为应收应付类型"),
    RECORD_CATEGORY_ERROR(700017, "记账类别错误"),
    RECORD_BOOK_NAME_REPEAT(700018, "记账账本名称不可重复"),
    RECORD_BOOK_RELATED(700019, "账户仍有记账记录关联"),
    RECORD_ACCOUNT_RELATED(700019, "账户仍有记账记录关联"),
    RECORD_ACCOUNT_NAME_REPEAT(700020, "记账账户名称不可重复"),

    // 共享账本相关 格式7001xx
    SHARED_BOOK_PERMISSION_DENIED(700100, "共享账本权限不足"),
    NOT_BOOK_MEMBER(700101, "您不是该账本的成员"),
    NOT_BOOK_OWNER(700102, "仅账本所有者可执行此操作"),
    INVALID_INVITE_CODE(700103, "邀请码无效或已过期"),
    ALREADY_MEMBER(700104, "您已是该账本成员"),
    INVITE_CODE_REGENERATE_FORBIDDEN(700105, "仅所有者可刷新邀请码"),

    // 审核流程相关 格式7002xx
    RECORD_NOT_PENDING(700200, "该记录不在待审核状态"),
    RECORD_STATUS_CONFLICT(700201, "记录状态已变更，请刷新后重试"),
    CANNOT_DELETE_POSTED_RECORD(700202, "已入账记录不可删除，请申请冲正"),
    CANNOT_EDIT_POSTED_RECORD(700203, "已入账记录不可编辑"),

    // 预算相关 格式7003xx
    BUDGET_EXCEEDED(700300, "本月支出已超出预算"),
    BUDGET_WARNING(700301, "本月支出已接近预算上限"),

    // 月结相关 格式7004xx
    MONTH_ALREADY_CLOSED(700400, "该月份已结算"),
    MONTH_CLOSED_CANNOT_MODIFY(700401, "已结算月份不可修改或新增记录"),
    PENDING_RECORDS_EXIST(700402, "该月仍有待审核记录，请先完成审核"),

    // 冲正相关 格式7005xx
    REVERSAL_NOT_BY_CREATOR(700500, "仅原始记录创建者可申请冲正"),
    REVERSAL_MONTH_NOT_CLOSED(700501, "冲正仅在月度结算后允许"),
    REVERSAL_ALREADY_PENDING(700502, "该记录已有待审核的冲正申请"),
    REVERSAL_RECORD_NOT_POSTED(700503, "仅已入账的记录可申请冲正"),
    OPERATION_IN_PROGRESS(700504, "操作正在处理中，请勿重复提交"),
    REVERSAL_RECORD_ALREADY_REVERSED(700505, "该记录已被冲正，不可重复审批"),
    MONTH_CLOSED_CANNOT_APPROVE(700506, "该月已结算，不可审核入账，请通过冲正流程处理"),

    // 预算结转相关 格式7006xx
    CARRYFORWARD_DISABLED(700600, "该账本未启用预算结转功能"),
    CARRYFORWARD_RULE_NOT_FOUND(700601, "未找到生效的结转规则"),
    CARRYFORWARD_ALREADY_EXECUTED(700602, "该期间结转已执行，请先回滚"),
    CARRYFORWARD_ROLLBACK_NOT_ALLOWED(700603, "结转回滚失败：目标期间已有记录"),
    CARRYFORWARD_PERIOD_NOT_CLOSED(700604, "源期间尚未月结，无法执行结转"),
    CARRYFORWARD_TARGET_PERIOD_CLOSED(700605, "目标期间已月结，无法写入结转"),
    CARRYFORWARD_RATE_INVALID(700606, "结转比例无效，需在0~1之间"),
    CARRYFORWARD_RULE_VERSION_CONFLICT(700607, "结转规则版本冲突，请重试"),
    CARRYFORWARD_LOCK_FAILED(700608, "结转锁获取失败，请稍后重试"),

    // 差额调整相关 格式7007xx
    ADJUSTMENT_DUPLICATE(700700, "重复的调整请求（幂等键已存在）"),
    ADJUSTMENT_SOURCE_NOT_CLOSED(700701, "源期间未月结，应使用普通冲销"),
    ADJUSTMENT_TARGET_CLOSED(700702, "目标期间已月结，无法计入调整"),
    ADJUSTMENT_AMOUNT_ZERO(700703, "调整金额不能为零"),
    ADJUSTMENT_NOT_FOUND(700704, "调整记录不存在"),
    ADJUSTMENT_ALREADY_REVERSED(700705, "调整记录已被冲销"),
    ADJUSTMENT_ORIGINAL_NOT_FOUND(700706, "关联的原始记录不存在"),
    ADJUSTMENT_LOCK_FAILED(700707, "调整锁获取失败，请稍后重试"),

    // 成员快照相关 格式7008xx
    SETTLEMENT_MEMBER_NOT_FOUND(700800, "成员不存在或从未参与该账本"),
    SETTLEMENT_SNAPSHOT_NOT_FOUND(700801, "该期间成员快照不存在"),
    SETTLEMENT_PERIOD_NOT_CLOSED(700802, "期间未月结，无快照数据"),

    // 重算相关 格式7009xx
    RECALC_NO_ADJUSTMENT(700900, "无需重算：无关联调整记录"),
    RECALC_VERSION_CONFLICT(700901, "快照版本冲突，请刷新后重试"),
    RECALC_LOCK_FAILED(700902, "重算锁获取失败，请稍后重试"),
    IDEMPOTENCY_CONFLICT(700903, "操作重复，请检查幂等键"),
    ;

    private final int retCode;
    private String message;    
    
    CodeMsg(int retCode, String message) {
        this.retCode = retCode;
        this.message = message;
    }
    public int getRetCode() {
        return retCode;
    }
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
}
