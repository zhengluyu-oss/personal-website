package xyz.luyu.knowledge;

import com.aliyun.oss.model.OSSObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import xyz.luyu.domain.response.ResponseResult;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import static xyz.luyu.knowledge.KnowledgeModels.*;

@RestController @RequestMapping("/knowledge")
public class KnowledgeFileController {
    private final KnowledgeAccess access;
    private final KnowledgeProperties properties;
    private final ObjectProvider<KnowledgePrivateObjects> objects;
    private final Semaphore uploadSlot=new Semaphore(1);
    public KnowledgeFileController(KnowledgeAccess access,KnowledgeProperties properties,ObjectProvider<KnowledgePrivateObjects> objects){
        this.access=access;this.properties=properties;this.objects=objects;
    }
    private KnowledgePrivateObjects storage(){
        KnowledgePrivateObjects value=objects.getIfAvailable();
        if(!properties.getProcessing().isEnabled()||value==null||!value.ready())throw KnowledgeFailure.unavailable();
        return value;
    }
    @GetMapping("/view/files/{entryId}/{version}")
    public ResponseEntity<ResponseResult<FileInfo>> info(@PathVariable UUID entryId,@PathVariable UUID version){
        long owner=access.requireRead();return KnowledgeController.ok(access.store().file(owner,entryId,version));
    }
    @GetMapping("/view/files/{entryId}/{version}/segments")
    public ResponseEntity<ResponseResult<Page<Segment>>> segments(@PathVariable UUID entryId,@PathVariable UUID version,
            @RequestParam(defaultValue="1") int page){
        long owner=access.requireRead();return KnowledgeController.ok(access.store().segments(owner,entryId,version,page));
    }
    @PostMapping("/manage/entries/{entryId}/files/{version}/retry")
    public ResponseEntity<ResponseResult<FileInfo>> retry(@PathVariable UUID entryId,@PathVariable UUID version){
        long owner=access.requireManage();storage();return KnowledgeController.ok(access.store().changeFileJob(owner,entryId,version,true));
    }
    @PostMapping("/manage/entries/{entryId}/files/{version}/cancel")
    public ResponseEntity<ResponseResult<FileInfo>> cancel(@PathVariable UUID entryId,@PathVariable UUID version){
        long owner=access.requireManage();return KnowledgeController.ok(access.store().changeFileJob(owner,entryId,version,false));
    }
    @PostMapping(value="/manage/entries/{entryId}/files",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseResult<FileCommit>> upload(@PathVariable UUID entryId,@RequestParam long expectedRevision,
            @RequestParam UUID mutationId,@RequestPart("file") MultipartFile file){
        long owner=access.requireManage();
        if(file==null||file.isEmpty())throw KnowledgeFailure.invalid("请选择非空文件");
        String name=filename(file.getOriginalFilename());
        String mime=mime(name);
        long limit=mime.equals("application/pdf")?properties.getProcessing().getPdfBytes():properties.getProcessing().getTextBytes();
        if(file.getSize()<1||file.getSize()>limit)throw KnowledgeFailure.invalid("文件超过允许大小");
        Entry before=access.store().read(owner,entryId,false);
        if(before.content().kind()!=Kind.DOCUMENT)throw KnowledgeFailure.invalid("请先创建文件资料条目");
        KnowledgePrivateObjects storage=storage();
        if(!uploadSlot.tryAcquire())throw new KnowledgeFailure(429,"已有文件正在上传，请稍后重试");
        Path temp=null;String key=null;boolean retained=false;
        try{
            temp=Files.createTempFile("knowledge-upload-", ".part");
            MessageDigest sha=MessageDigest.getInstance("SHA-256");
            long bytes=0;
            try(InputStream source=file.getInputStream();var output=Files.newOutputStream(temp)){
                byte[] chunk=new byte[8192];int count;
                while((count=source.read(chunk))!=-1){
                    bytes+=count;if(bytes>limit)throw KnowledgeFailure.invalid("文件超过允许大小");
                    sha.update(chunk,0,count);output.write(chunk,0,count);
                }
            }
            if(bytes==0)throw KnowledgeFailure.invalid("文件不能为空");
            validateFile(temp,mime);
            key=storage.newKey();
            try(InputStream source=Files.newInputStream(temp)){storage.putChecked(key,source,bytes,mime);}
            FileCommit saved=access.store().attachFile(owner,entryId,expectedRevision,mutationId,UUID.randomUUID(),key,name,mime,bytes,
                HexFormat.of().formatHex(sha.digest()),properties.getStorage().getQuotaBytes());
            retained=saved.accepted();
            return KnowledgeController.ok(saved);
        }catch(KnowledgeFailure known){throw known;}
        catch(Exception failure){throw KnowledgeFailure.unavailable();}
        finally{
            if(key!=null&&!retained)try{storage.delete(key);}catch(Exception ignored){}
            if(temp!=null)try{Files.deleteIfExists(temp);}catch(Exception ignored){}
            uploadSlot.release();
        }
    }
    @GetMapping("/view/files/{entryId}/{version}/content")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable UUID entryId,@PathVariable UUID version){
        long owner=access.requireRead();KnowledgeStore store=access.store();
        FileInfo file=store.file(owner,entryId,version);
        String key=store.fileKey(owner,entryId,version);
        KnowledgePrivateObjects storage=storage();
        StreamingResponseBody body=output->{
            try(OSSObject object=storage.open(key);InputStream input=object.getObjectContent()){
                input.transferTo(output);
            }
        };
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store")
            .header("X-Robots-Tag","noindex, nofollow, noarchive")
            .header("X-Content-Type-Options","nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.filename(),StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(file.sizeBytes()).body(body);
    }
    private static String filename(String input){
        if(input==null||input.isBlank()||input.length()>200||input.chars().anyMatch(c->c<32||c==127||c=='/'||c=='\\'))
            throw KnowledgeFailure.invalid("文件名无效");
        return input;
    }
    private static String mime(String name){
        String lowered=name.toLowerCase(Locale.ROOT);
        if(lowered.endsWith(".pdf"))return "application/pdf";
        if(lowered.endsWith(".md")||lowered.endsWith(".markdown"))return "text/markdown";
        if(lowered.endsWith(".txt"))return "text/plain";
        throw KnowledgeFailure.invalid("只支持PDF、Markdown和TXT");
    }
    private static void validateFile(Path path,String mime)throws Exception{
        if(mime.equals("application/pdf")){
            byte[] header=new byte[5];try(InputStream in=Files.newInputStream(path)){if(in.read(header)!=5||!"%PDF-".equals(new String(header,StandardCharsets.US_ASCII)))throw KnowledgeFailure.invalid("PDF文件格式无效");}
        }else{
            byte[] bytes=Files.readAllBytes(path);
            try{StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes));}
            catch(CharacterCodingException bad){throw KnowledgeFailure.invalid("文本文件必须使用UTF-8编码");}
            for(byte value:bytes)if(value==0)throw KnowledgeFailure.invalid("文本文件含无效字符");
            String leading=new String(bytes,0,Math.min(128,bytes.length),StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
            if(leading.startsWith("<!doctype html")||leading.startsWith("<html")||leading.startsWith("pk\u0003\u0004"))throw KnowledgeFailure.invalid("文件内容与类型不符");
        }
    }
}
