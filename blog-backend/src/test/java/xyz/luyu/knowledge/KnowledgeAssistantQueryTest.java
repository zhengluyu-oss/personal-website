package xyz.luyu.knowledge;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeAssistantQueryTest {
    @Test void chineseQuestionKeepsUsefulShortPhrases(){
        List<String> terms=KnowledgeAssistantQuery.terms("Redis 有哪些数据落盘方式？",null);
        assertTrue(terms.contains("redis"));
        assertTrue(terms.stream().anyMatch(term->term.contains("数据")||term.contains("落盘")));
        assertTrue(terms.contains("持久化"));
        assertTrue(terms.size()<=6);
    }
    @Test void referentialFollowupCanUseEarlierSubject(){
        List<String> terms=KnowledgeAssistantQuery.terms("第二种方式有什么限制？","Redis 有哪些持久化方式？");
        assertTrue(terms.contains("redis"));
        assertTrue(terms.contains("持久化"));
    }
    @Test void claimNeedsExactQuoteAndRelevantTerms(){
        assertTrue(KnowledgeAssistantQuery.supports("AOF 记录写命令","AOF 记录写命令","AOF 记录写命令，恢复时重放。"));
        assertFalse(KnowledgeAssistantQuery.supports("MySQL 默认端口是 3306","AOF 记录写命令","AOF 记录写命令，恢复时重放。"));
        assertFalse(KnowledgeAssistantQuery.supports("AOF 记录写命令","虚构原文","AOF 记录写命令，恢复时重放。"));
        assertFalse(KnowledgeAssistantQuery.supports("AOF 记录写命令，但保证永不丢数据","AOF 记录写命令","AOF 记录写命令，恢复时重放。"));
        assertTrue(KnowledgeAssistantQuery.supports("记录写命令","AOF 记录写命令","AOF 记录写命令，恢复时重放。"));
    }
    @Test void longAsciiIdentifierDoesNotExceedSearchLimit(){
        assertTrue(KnowledgeAssistantQuery.terms("x".repeat(101)+" Redis",null).stream().allMatch(term->term.length()<=100));
    }
}
