package xyz.luyu.knowledge;

import com.aliyun.oss.model.OSSObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import xyz.luyu.domain.response.ResponseResult;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/** Knowledge-only image transport. No public OSS URL or token-bearing image URL is returned. */
@RestController @RequestMapping("/knowledge")
public final class KnowledgeImageController {
    private static final int MAX_BYTES=5*1024*1024;
    private final KnowledgeAccess access;
    private final KnowledgeProperties properties;
    private final ObjectProvider<KnowledgePrivateObjects> objects;
    public KnowledgeImageController(KnowledgeAccess access,KnowledgeProperties properties,ObjectProvider<KnowledgePrivateObjects> objects){
        this.access=access;this.properties=properties;this.objects=objects;
    }
    private KnowledgePrivateObjects storage(){
        KnowledgePrivateObjects value=objects.getIfAvailable();
        if(value==null||!value.ready())throw KnowledgeFailure.unavailable();
        return value;
    }
    @PostMapping(value="/manage/entries/{entryId}/images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseResult<UUID>> upload(@PathVariable UUID entryId,@RequestParam UUID mutationId,
            @RequestPart("file") MultipartFile file){
        long owner=access.requireManage();KnowledgeStore store=access.store();
        if(file==null||file.isEmpty()||file.getSize()>MAX_BYTES)throw KnowledgeFailure.invalid("图片需小于5 MiB");
        byte[] bytes;
        try{bytes=file.getBytes();}catch(Exception failure){throw KnowledgeFailure.unavailable();}
        if(bytes.length<16||bytes.length>MAX_BYTES)throw KnowledgeFailure.invalid("图片大小无效");
        String mime=mime(bytes);int[] dimensions=dimensions(bytes,mime);
        if(dimensions[0]<1||dimensions[1]<1||dimensions[0]>12000||dimensions[1]>12000
            ||(long)dimensions[0]*dimensions[1]>40_000_000L)throw KnowledgeFailure.invalid("图片尺寸超过限制");
        String digest;
        try{digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(Exception failure){throw KnowledgeFailure.unavailable();}
        // Reject an invalid entry before writing an object, and recheck under the store lock.
        store.read(owner,entryId,false);
        KnowledgePrivateObjects storage=storage();String key=storage.newKey();boolean retained=false;
        try{
            storage.putChecked(key,new ByteArrayInputStream(bytes),bytes.length,mime);
            KnowledgeStore.ImageCommit commit=store.attachImage(owner,entryId,mutationId,UUID.randomUUID(),key,
                mime,bytes.length,dimensions[0],dimensions[1],digest,properties.getStorage().getQuotaBytes());
            retained=commit.accepted();
            return KnowledgeController.ok(commit.id());
        }catch(KnowledgeFailure known){throw known;}
        catch(Exception failure){throw KnowledgeFailure.unavailable();}
        finally{if(!retained)try{storage.delete(key);}catch(Exception ignored){}}
    }
    @GetMapping("/view/images/{imageId}/content")
    public ResponseEntity<StreamingResponseBody> image(@PathVariable UUID imageId){
        long owner=access.requireRead();KnowledgeStore store=access.store();
        KnowledgeStore.ImageInfo image=store.image(owner,imageId,access.canManage());
        KnowledgePrivateObjects storage=storage();
        StreamingResponseBody body=output->{
            try(OSSObject object=storage.open(image.objectKey());InputStream input=object.getObjectContent()){
                input.transferTo(output);
            }
        };
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store")
            .header("X-Robots-Tag","noindex, nofollow, noarchive")
            .header("X-Content-Type-Options","nosniff")
            .contentType(MediaType.parseMediaType(image.mime())).contentLength(image.sizeBytes()).body(body);
    }
    static String mime(byte[] bytes){
        if(bytes.length>=8&&(bytes[0]&255)==137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71&&bytes[4]==13&&bytes[5]==10&&bytes[6]==26&&bytes[7]==10)return "image/png";
        if(bytes.length>=3&&(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255)return "image/jpeg";
        if(bytes.length>=16&&ascii(bytes,0,"RIFF")&&ascii(bytes,8,"WEBP")
            &&(ascii(bytes,12,"VP8 ")||ascii(bytes,12,"VP8L")||ascii(bytes,12,"VP8X")))return "image/webp";
        throw KnowledgeFailure.invalid("仅支持有效的 JPG、PNG 或 WebP 图片");
    }
    static int[] dimensions(byte[] bytes,String mime){
        if("image/webp".equals(mime)){
            if(ascii(bytes,12,"VP8X")&&bytes.length>=30)return new int[]{1+u24(bytes,24),1+u24(bytes,27)};
            if(ascii(bytes,12,"VP8L")&&bytes.length>=25&&(bytes[20]&255)==47)
                return new int[]{1+((bytes[21]&255)|((bytes[22]&63)<<8)),1+(((bytes[22]&192)>>6)|((bytes[23]&255)<<2)|((bytes[24]&15)<<10))};
            if(ascii(bytes,12,"VP8 ")&&bytes.length>=30&&bytes[23]==(byte)0x9d&&bytes[24]==1&&bytes[25]==42)
                return new int[]{((bytes[26]&255)|((bytes[27]&63)<<8)),((bytes[28]&255)|((bytes[29]&63)<<8))};
            throw KnowledgeFailure.invalid("WebP 图片格式无效");
        }
        try(ImageInputStream stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext())throw KnowledgeFailure.invalid("图片内容无法解析");
            var reader=readers.next();
            try{reader.setInput(stream,true,true);return new int[]{reader.getWidth(0),reader.getHeight(0)};}
            finally{reader.dispose();}
        }catch(KnowledgeFailure known){throw known;}
        catch(Exception failure){throw KnowledgeFailure.invalid("图片内容无法解析");}
    }
    private static boolean ascii(byte[] value,int offset,String text){
        if(value.length<offset+text.length())return false;
        for(int i=0;i<text.length();i++)if(value[offset+i]!=(byte)text.charAt(i))return false;
        return true;
    }
    private static int u24(byte[] value,int offset){return (value[offset]&255)|((value[offset+1]&255)<<8)|((value[offset+2]&255)<<16);}
}
