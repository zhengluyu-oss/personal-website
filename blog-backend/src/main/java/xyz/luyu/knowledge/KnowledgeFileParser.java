package xyz.luyu.knowledge;

import com.alibaba.fastjson2.JSON;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Separate, credential-free process. Exit codes contain only fixed error classes. */
public final class KnowledgeFileParser {
    private static final int MAX_OUTPUT=2097152;
    private KnowledgeFileParser() {}
    public record Extracted(int ordinal,Integer page,Integer fromLine,Integer toLine,String text) {}
    public record Result(List<Extracted> segments) {}
    public static void main(String[] args){
        if(args.length!=3){System.exit(2);return;}
        try{
            Path input=Path.of(args[0]);String mime=args[1];int maxPages=Integer.parseInt(args[2]);
            if(!Files.isRegularFile(input)||maxPages<1||maxPages>300)System.exit(2);
            List<Extracted> segments=mime.equals("application/pdf")?pdf(input,maxPages):text(input);
            if(segments.isEmpty()){System.exit(4);return;}
            byte[] bytes=JSON.toJSONBytes(new Result(segments));
            if(bytes.length>MAX_OUTPUT){System.exit(6);return;}
            System.out.write(bytes);System.out.flush();
        }catch(TooManyPages e){System.exit(5);}
        catch(Encrypted e){System.exit(3);}
        catch(TooMuchText e){System.exit(6);}
        catch(CharacterCodingException e){System.exit(2);}
        catch(OutOfMemoryError e){System.exit(7);}
        catch(Throwable e){System.exit(2);}
    }
    static List<Extracted> pdf(Path input,int maxPages)throws Exception{
        List<Extracted> result=new ArrayList<>();int bytes=0;
        try(PDDocument document=Loader.loadPDF(new RandomAccessReadBufferedFile(input.toString()))){
            if(document.isEncrypted())throw new Encrypted();
            if(document.getNumberOfPages()>maxPages)throw new TooManyPages();
            PDFTextStripper stripper=new PDFTextStripper();
            for(int page=1;page<=document.getNumberOfPages();page++){
                stripper.setStartPage(page);stripper.setEndPage(page);
                String value=stripper.getText(document).strip();
                if(value.isEmpty())continue;
                bytes+=value.getBytes(StandardCharsets.UTF_8).length;
                if(bytes>MAX_OUTPUT)throw new TooMuchText();
                result.add(new Extracted(result.size(),page,null,null,value));
            }
        }catch(InvalidPasswordException password){throw new Encrypted();}
        return result;
    }
    static List<Extracted> text(Path input)throws Exception{
        byte[] raw=Files.readAllBytes(input);
        if(raw.length>5242880)throw new TooMuchText();
        String value=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString();
        List<Extracted> result=new ArrayList<>();StringBuilder chunk=new StringBuilder();int start=1,line=0,bytes=0;
        for(String row:value.split("\\R",-1)){
            line++;if(row.indexOf('\0')>=0)throw new IllegalArgumentException();
            if(chunk.length()>0&&chunk.length()+row.length()>3000){String body=chunk.toString().strip();if(!body.isEmpty()){bytes+=body.getBytes(StandardCharsets.UTF_8).length;result.add(new Extracted(result.size(),null,start,line-1,body));}chunk.setLength(0);start=line;}
            chunk.append(row).append('\n');
            if(bytes>MAX_OUTPUT)throw new TooMuchText();
        }
        String body=chunk.toString().strip();if(!body.isEmpty()){bytes+=body.getBytes(StandardCharsets.UTF_8).length;result.add(new Extracted(result.size(),null,start,line,body));}
        if(bytes>MAX_OUTPUT)throw new TooMuchText();
        return result;
    }
    private static final class Encrypted extends RuntimeException {}
    private static final class TooManyPages extends RuntimeException {}
    private static final class TooMuchText extends RuntimeException {}
}
