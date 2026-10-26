package xyz.luyu.diary.ai;

/** Fixed public messages only. No provider text, endpoint, payload, or exception is interpolated. */
public final class DiaryAiFeedback {
    private DiaryAiFeedback() {}
    public static String message(String state,String code) {
        if("STALE".equals(state))return "来源资料已变化，请刷新范围后重新生成";
        if("CANCELED".equals(state))return "本次任务已取消；取消不能撤回已经发出的模型请求";
        if(!"FAILED".equals(state) && !"UNCERTAIN".equals(state))return switch(state) {
            case "QUEUED"->"任务已受理，正在等待处理";case "RUNNING"->"正在处理，尚未生成可查看的结果";case "SUCCEEDED"->"已完成";default->"处理状态已更新";
        };
        if("OUTPUT_EVIDENCE_INVALID".equals(code))return "结果校验未通过：模型的结构或原文引用不符合要求，因此没有发布回答。可核对资料后手动重新提交。";
        if("GENERATION_FORMAT".equals(code))return "历史生成响应未通过检查，原记录不足以区分截断、空回答或结构异常。没有发布结果，可手动重新提交。";
        if("NETWORK_UNKNOWN".equals(code) || "RESTART_UNKNOWN".equals(code) || "INTERRUPTED".equals(code))return "请求结果未知，原记录不足以确定具体阶段或网络原因；可能已产生费用，不会自动重发。";
        if("DAILY_LIMIT".equals(code))return "今日 AI 调用额度已用完，请稍后再试；失败不会自动退还已发出的调用尝试。";
        if("CONTEXT_LIMIT".equals(code))return "生成资料超过输入预算，请缩小范围后重新预览。";
        if(code!=null && (code.startsWith("EMBEDDING_") || code.startsWith("GENERATION_"))) {
            boolean embedding=code.startsWith("EMBEDDING_");String stage=embedding?"检索准备（问题向量）":"内容生成";
            String suffix=code.substring(embedding?10:11);
            return stage+"阶段："+switch(suffix) {
                case "TRUNCATED"->"模型输出达到上限而被截断，没有展示不完整结果。请聚焦问题或缩小复盘范围后手动提交。";
                case "EMPTY"->"模型没有返回有效正文。可稍后手动重新提交。";
                case "RESPONSE_INVALID","FORMAT","STOP_INVALID"->"服务响应结构或结束状态不符合要求，没有发布结果。可稍后手动再试；持续失败需检查服务兼容性。";
                case "TOOL_CALL"->"模型尝试返回工具调用，系统已拒绝执行且没有发布结果。";
                case "REFUSED"->"供应商未正常提供本次内容，没有发布结果。请调整问题或资料范围。";
                case "AUTH_REJECTED"->"供应商拒绝认证，请检查后端模型密钥、权限及服务地域配置。";
                case "RATE_LIMIT"->"供应商限流或额度不足，请检查服务额度并稍后手动再试。";
                case "CONNECT_TIMEOUT","REQUEST_TIMEOUT"->"连接或请求超时，无法确认供应商处理结果，不会自动重发。可稍后手动再试。";
                case "CONNECTION_FAILED","NETWORK_UNKNOWN","INTERRUPTED"->"网络连接异常，无法确认供应商处理结果，不会自动重发。可稍后手动再试。";
                case "CONTEXT_LIMIT","INPUT"->"输入不符合调用预算，请聚焦问题或缩小资料范围。";
                case "ENDPOINT_REJECTED","CONFIGURATION_INVALID"->"服务地址或配置未通过安全检查，请检查后端模型配置。";
                case "RESPONSE_TOO_LARGE"->"服务响应超过大小上限，已停止接收并且没有发布结果。";
                default->"供应商请求未完成，没有发布结果；请检查服务状态或稍后手动再试。";
            };
        }
        return "本次处理未完成，具体原因暂不可确认，没有发布结果。请核对任务状态后决定是否手动再提交。";
    }
}
