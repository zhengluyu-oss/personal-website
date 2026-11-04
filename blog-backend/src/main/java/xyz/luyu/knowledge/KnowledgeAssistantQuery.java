package xyz.luyu.knowledge;

import java.util.*;
import java.util.regex.*;

/** Bounded lexical queries for the assistant; ordinary knowledge search is unchanged. */
final class KnowledgeAssistantQuery {
    private static final Pattern RUN=Pattern.compile("[A-Za-z0-9_.+-]{2,}|[\\p{IsHan}]+",Pattern.UNICODE_CASE);
    private static final Pattern IDENTIFIER=Pattern.compile("[A-Za-z][A-Za-z0-9_.+-]{1,99}");
    private static final Pattern HEADING=Pattern.compile("(?m)^[ \\t]{0,3}(?:#{1,6}[ \\t]+([^\\r\\n]+)|((?:[一二三四五六七八九十百零〇]+|[0-9]+)[、.．][ \\t]*[^\\r\\n]+))[ \\t]*$");
    private static final Set<String> NOISE=Set.of("请问","如何","怎么","什么","为什么","可以","是否","哪些","哪种","这种","那个","这个","方式","区别","分别","一下","有没有","是什么","有什么","关于","限制","第二","第一","第三");
    private static final List<String> BREAKS=List.of("有哪些","有什么","是什么","为什么","怎么样","如何","怎么","请问","分别","可以","是否","哪些","哪种","方式","限制","的","和","与","有","是","吗","呢","？","?");
    private KnowledgeAssistantQuery() {}

    static List<String> terms(String question,String previousQuestion){
        LinkedHashSet<String> terms=new LinkedHashSet<>();
        collect(question,terms);
        if(terms.size()<6&&previousQuestion!=null)collect(previousQuestion,terms);
        if(terms.size()<6&&(question.contains("落盘")||previousQuestion!=null&&previousQuestion.contains("落盘")))terms.add("持久化");
        return List.copyOf(terms);
    }
    private static void collect(String input,LinkedHashSet<String> terms){
        if(input==null)return;
        Matcher matcher=RUN.matcher(input.toLowerCase(Locale.ROOT));
        while(matcher.find()&&terms.size()<6){
            String run=matcher.group();
            if(run.codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN)){
                for(String separator:BREAKS)run=run.replace(separator," ");
                for(String part:run.split("\\s+")){
                    if(part.length()<2||NOISE.contains(part))continue;
                    if(part.length()<=6)terms.add(part);
                    else {
                        for(int i=0;i+2<=part.length()&&terms.size()<6;i+=2)terms.add(part.substring(i,Math.min(i+3,part.length())));
                    }
                    if(terms.size()>=6)break;
                }
            } else if(run.length()<=100&&!NOISE.contains(run))terms.add(run);
        }
    }

    static int overlap(String text,List<String> terms){
        String lower=text.toLowerCase(Locale.ROOT);int count=0;
        for(String term:terms)if(lower.contains(term.toLowerCase(Locale.ROOT)))count++;
        return count;
    }
    static boolean asksForLocation(String question){
        return question.contains("哪个标签")||question.contains("什么标签")||question.contains("哪个分类")
                ||question.contains("哪个部分")||question.contains("哪一部分")||question.contains("哪一节")
                ||question.contains("哪个章节")||question.contains("哪一章")
                ||question.contains("知识库")&&(question.contains("哪里")||question.contains("在哪"));
    }
    static String locationTerm(String question){
        Matcher identifier=IDENTIFIER.matcher(question);
        if(identifier.find())return identifier.group().toLowerCase(Locale.ROOT);
        return terms(question,null).stream().filter(term->!Set.of("知识库","标签","分类","部分","介绍","帮我","查找").contains(term)).findFirst().orElse("");
    }
    static String section(String body,String term){
        int match=body.toLowerCase(Locale.ROOT).indexOf(term.toLowerCase(Locale.ROOT));
        if(match<0)return null;
        int lineEnd=body.indexOf('\n',match);
        Matcher headings=HEADING.matcher(body.substring(0,lineEnd<0?body.length():lineEnd));String section=null;
        while(headings.find())section=Objects.requireNonNullElse(headings.group(1),headings.group(2)).strip();
        return section;
    }
    static boolean supports(String claim,String quote,String excerpt){
        if(claim==null||claim.isBlank()||claim.length()>240||quote==null||quote.length()<4||quote.length()>240||!excerpt.contains(quote))return false;
        String text=claim.strip();
        return text.length()>=4&&quote.contains(text);
    }
}
