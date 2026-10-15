package xyz.kuailemao.diary;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DiaryModels {
    private DiaryModels() {}
    public record Content(String title, String body, LocalDate coveredFrom, LocalDate coveredTo) {
        public void validate() {
            if (title == null || body == null || title.codePointCount(0,title.length()) > 120
                    || body.codePointCount(0,body.length()) > 100000)
                throw DiaryFailure.invalid("标题最多120字，正文最多100000字");
            if ((coveredFrom == null) != (coveredTo == null)
                    || coveredFrom != null && coveredFrom.isAfter(coveredTo))
                throw DiaryFailure.invalid("记录范围请同时填写起止日期，且开始不晚于结束");
        }
    }
    public record Write(UUID entryId, UUID mutationId, long expectedRevision, String title,
                        String body, LocalDate coveredFrom, LocalDate coveredTo) {
        public Content content() { return new Content(title,body,coveredFrom,coveredTo); }
    }
    public record Change(UUID mutationId, long expectedRevision, Long sourceRevision) {}
    public record Entry(UUID id, String title, String body, LocalDate coveredFrom, LocalDate coveredTo,
                        long revision, Instant createdAt, Instant updatedAt, Instant deletedAt) {
        public Content content() { return new Content(title,body,coveredFrom,coveredTo); }
    }
    public record Ack(UUID entryId, UUID mutationId, long revision, Instant savedAt) {}
    public record History(long revision, String title, String body, LocalDate coveredFrom,
                          LocalDate coveredTo, String action, Instant savedAt, Long sourceRevision, boolean deleted) {}
    public record Search(String axis, LocalDate from, LocalDate to, String keyword,
                         Integer page, Integer pageSize, Boolean deleted) {}
    public record Page<T>(List<T> records, long total, int page, int pageSize) {}
    public record CalendarDay(LocalDate date, long count) {}
    public record Capabilities(boolean allowed, boolean available) {}
    public record Export(Search filter, String format) {}
    public record Download(byte[] bytes, String filename, String contentType) {}
}
