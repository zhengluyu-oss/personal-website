package xyz.kuailemao.diary.ai;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import static xyz.kuailemao.diary.ai.DiaryAiModels.*;

/** 全部偏移使用 Unicode 码点，而不是 UTF-16 char。 */
public final class DiaryAiText {
    public static final String VERSION="paragraph-cp-800-1000-overlap120-v1";
    private DiaryAiText() {}
    public record Chunk(int index,int start,int end,String text,String sha256) {}
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA256 unavailable"); }
    }
    public static List<Chunk> chunks(String body) {
        List<Chunk> result=new ArrayList<>();int length=points(body),start=0;
        while(start<length) {
            int end=Math.min(start+1000,length);
            if(end<length) {
                String window=slice(body,start,end);int boundary=window.lastIndexOf('\n',window.offsetByCodePoints(0,Math.min(800,points(window))));
                if(boundary>=0 && window.codePointCount(0,boundary)>=400) end=start+window.codePointCount(0,boundary)+1;
            }
            String text=slice(body,start,end);
            if(!text.isBlank()) result.add(new Chunk(result.size(),start,end,text,hash(text)));
            if(end==length) break;
            start=Math.max(start+1,end-120);
        }
        return List.copyOf(result);
    }
    public static String profile(DiaryAiProperties p) {
        return hash(p.getProvider()+"\n"+p.getEmbeddingEndpoint()+"\n"+p.getEmbeddingModel()+"\n"+p.getDimensions()+"\n"+VERSION);
    }
    public static String snapshot(List<Entry> entries) {
        return hash(entries.stream().sorted(Comparator.comparing(Entry::id))
                .map(e->e.id()+":"+e.revision()+":"+hash(e.title()+"\n"+e.body())+":"+e.coveredFrom()+":"+e.coveredTo())
                .reduce("",(a,b)->a+b+"\n"));
    }
}
