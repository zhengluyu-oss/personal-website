package xyz.luyu.diary.ai;

import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** 新任务只能挂在既有单线程和全局租约下；不得创建额外消费者。 */
public interface DiaryAiExtension {
    long queued(long owner);
    long reports(long owner);
    Instant oldest(long owner);
    void recover(long owner,UUID lease);
    void process(long owner,UUID lease,BooleanSupplier active);
}
