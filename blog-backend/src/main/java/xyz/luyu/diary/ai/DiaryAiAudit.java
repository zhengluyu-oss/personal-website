package xyz.luyu.diary.ai;

import java.util.UUID;
import java.util.Set;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Metadata only. Keep this logger outside the muted diary package hierarchy. */
public final class DiaryAiAudit {
    private static final Logger LOG=LoggerFactory.getLogger("diary.ai.audit");
    private static final Set<String> KINDS=Set.of("QUESTION","REVIEW","PERIOD");
    private static final Set<String> STAGES=Set.of("COMPLETE","VALIDATION","FAILURE");
    private static final Set<String> CODES=Set.of("OK","INSUFFICIENT","DAILY_LIMIT","TASK_REJECTED","ENDPOINT_REJECTED");
    private static final Set<String> CLOUD_SUFFIXES=Set.of("AUTH_REJECTED","CONFIGURATION_INVALID","CONNECT_TIMEOUT",
        "CONNECTION_FAILED","CONTEXT_LIMIT","EMPTY","FORMAT","INPUT","INTERRUPTED","NETWORK_UNKNOWN",
        "PROVIDER_REJECTED","RATE_LIMIT","REQUEST_TIMEOUT","RESPONSE_INVALID","RESPONSE_TOO_LARGE",
        "STOP_INVALID","TOOL_CALL","TRUNCATED");
    private DiaryAiAudit() {}
    public static void event(UUID task,String kind,String stage,String code,long elapsedMs,Long inputTokens,Long outputTokens,boolean possiblyCharged) {
        LOG.info("task={} kind={} stage={} code={} elapsed_ms={} input_tokens={} output_tokens={} possibly_charged={}",
            task,KINDS.contains(kind)?kind:"UNKNOWN",STAGES.contains(stage)?stage:"UNKNOWN",safeCode(code),Math.max(0,elapsedMs),inputTokens,outputTokens,possiblyCharged);
    }
    private static String safeCode(String value) {
        if(value!=null)for(DiaryValidation.Reason reason:DiaryValidation.Reason.values())if(reason.name().equals(value))return value;
        if(value!=null)for(String stage:List.of("EMBEDDING","GENERATION"))if(value.startsWith(stage+"_") && CLOUD_SUFFIXES.contains(value.substring(stage.length()+1)))return value;
        return value!=null&&CODES.contains(value)?value:"UNKNOWN";
    }
}
