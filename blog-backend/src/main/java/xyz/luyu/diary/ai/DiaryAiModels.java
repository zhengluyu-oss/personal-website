package xyz.luyu.diary.ai;

import xyz.luyu.diary.DiaryFailure;
import java.time.*;
import java.util.*;

/** 私密 AI 协议；不接收 owner、endpoint 或密钥。 */
public final class DiaryAiModels {
    private DiaryAiModels() {}
    public enum State { QUEUED, RUNNING, SUCCEEDED, FAILED, UNCERTAIN, STALE, CANCELED }
    public enum Kind { QUESTION, REVIEW, INDEX_START, INDEX_PAUSE }
    public record Scope(List<UUID> entryIds,String axis,LocalDate from,LocalDate to) {
        public void validate(boolean review) {
            boolean ids=entryIds!=null;
            if(ids) {
                if(entryIds.isEmpty() || entryIds.size()>40 || entryIds.stream().anyMatch(Objects::isNull)
                        || new HashSet<>(entryIds).size()!=entryIds.size() || axis!=null || from!=null || to!=null)
                    throw DiaryFailure.invalid("请选择互不重复的篇目，或单独选择时间范围");
            } else {
                if(axis!=null && !Set.of("created","covered").contains(axis)
                        || (from==null)!=(to==null) || from!=null && (from.isAfter(to) || from.plusYears(5).isBefore(to))
                        || (review || "covered".equals(axis)) && from==null)
                    throw DiaryFailure.invalid("请选择完整有效的日记时间范围");
            }
        }
        public String timeAxis() { return axis==null?"created":axis; }
    }
    public record Request(UUID requestId,String question,Scope scope,String snapshotDigest,boolean allowPartialIndex) {
        public void validate(Kind kind) {
            if(requestId==null) throw DiaryFailure.invalid("缺少请求标识");
            if(question!=null && points(question)>1000 || kind==Kind.REVIEW && question!=null
                    || kind==Kind.QUESTION && snapshotDigest!=null)throw DiaryFailure.invalid("请求字段不符合当前操作");
            if(kind==Kind.QUESTION && (question==null || question.isBlank() || points(question)>1000))
                throw DiaryFailure.invalid("问题需为1至1000字");
            if(kind==Kind.REVIEW && (snapshotDigest==null || !snapshotDigest.matches("[0-9a-f]{64}")))
                throw DiaryFailure.invalid("请先预览复盘范围");
            if(scope!=null) scope.validate(kind==Kind.REVIEW);
            else if(kind==Kind.REVIEW) throw DiaryFailure.invalid("请选择复盘范围");
        }
    }
    public record IndexRequest(UUID requestId,boolean retryFailed) {}
    public record Capabilities(boolean enabled,boolean available,String message) {}
    public record Entry(UUID id,String title,String body,LocalDate coveredFrom,LocalDate coveredTo,
                        long revision,Instant createdAt) {}
    public record EntrySummary(UUID id,String title,LocalDate coveredFrom,LocalDate coveredTo,long revision) {}
    public record Preview(int entries,int characters,String snapshotDigest,List<EntrySummary> records,String limitation) {}
    public record Source(String sourceId,UUID entryId,long revision,String title,LocalDate coveredFrom,
                         LocalDate coveredTo,Instant createdAt,int start,int end,String text,String sha256) {}
    public record Coverage(int total,int indexed,int failed) {}
    public record IndexStatus(boolean syncing,String profile,Coverage coverage,int remainingBlocks,String message) {}
    public record Job(UUID id,Kind kind,State state,String message,UUID reportId,boolean possiblyCharged,Instant createdAt) {}
    public record ReportSummary(UUID id,Kind kind,State state,Instant createdAt,int entries,String model) {}
    public record Page<T>(List<T> records,long total,int page,int pageSize) {}
    public record Report(UUID id,Kind kind,String result,List<Source> sources,String scopeDescription,
                         String model,Instant createdAt,boolean partial) {}
    public record Quote(String sourceId,String text) {}
    public record Statement(String text,List<String> evidenceIds,List<Quote> quotes) {}
    public record Suggestion(String action,String reason,List<String> evidenceIds,String effort,String reviewAfter,boolean general) {}
    public record Result(Statement answer,Statement summary,List<Statement> observations,List<Statement> hypotheses,
                         List<Suggestion> suggestions,List<String> limitations,boolean insufficientEvidence) {}
    public static int points(String value) { return value.codePointCount(0,value.length()); }
    public static String slice(String value,int start,int end) {
        return value.substring(value.offsetByCodePoints(0,start),value.offsetByCodePoints(0,end));
    }
}
