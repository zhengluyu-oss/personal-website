package xyz.luyu.diary;

/** 固定、安全的客户端信息，禁止附加正文、SQL参数或基础设施异常信息。 */
public final class DiaryFailure extends RuntimeException {
    private final int status;
    public DiaryFailure(int status, String message) { super(message); this.status=status; }
    public int status() { return status; }
    public static DiaryFailure invalid(String message) { return new DiaryFailure(400,message); }
    public static DiaryFailure conflict() { return new DiaryFailure(409,"日记版本已变化，请比较并处理冲突"); }
    public static DiaryFailure unavailable() { return new DiaryFailure(503,"日记暂时不可用，未确认内容仍需保留"); }
}
