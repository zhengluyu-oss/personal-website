package xyz.luyu.knowledge;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.ObjectPermission;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ListObjectsRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import xyz.luyu.config.OssProperties;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Uses the site's existing OSS connection without exposing public media URLs. */
@Service
@ConditionalOnProperty(name="knowledge.enabled",havingValue="true")
public final class KnowledgePrivateObjects {
    private final OSS oss;
    private final OssProperties site;
    private final KnowledgeProperties knowledge;
    private HttpClient anonymous;
    private volatile long checkedAt;
    private volatile boolean safe;
    private String inventoryMarker;

    public KnowledgePrivateObjects(OSS oss,OssProperties site,KnowledgeProperties knowledge){
        this.oss=oss;this.site=site;this.knowledge=knowledge;
    }
    String prefix(){
        String p=knowledge.getStorage().getPrefix();
        if(p==null||!p.startsWith("knowledge/")||!p.endsWith("/")||p.length()>100||!p.matches("[a-zA-Z0-9/_-]+")||p.contains("//")||p.contains(".."))
            throw KnowledgeFailure.unavailable();
        return p;
    }
    private String bucket(){return site.getBucketName();}
    private void key(String key){if(key==null||!key.startsWith(prefix())||!key.matches("[a-zA-Z0-9/_-]+")||key.contains(".."))throw KnowledgeFailure.invalid("文件标识无效");}
    public String newKey(){return prefix()+"objects/"+UUID.randomUUID();}

    /** A failed or unverified probe keeps uploads closed; no bucket ACL is changed. */
    public synchronized boolean ready(){
        if(!knowledge.getStorage().isEnabled())return false;
        long now=System.currentTimeMillis();
        if(now-checkedAt<60000)return safe;
        checkedAt=now;safe=false;
        String probe=prefix()+"_probe/"+UUID.randomUUID();
        try{
            put(probe,new ByteArrayInputStream(new byte[]{1}),1,"application/octet-stream");
            if(oss.getObjectAcl(bucket(),probe).getPermission()!=ObjectPermission.Private)return false;
            String endpoint=site.getEndpoint().replaceFirst("^https?://","");
            if(!endpoint.matches("[a-zA-Z0-9.-]+"))return false;
            if(!anonymousDenied("https://"+bucket()+"."+endpoint+"/"+probe))return false;
            String media=site.getMediaDomain();
            if(media!=null&&!media.isBlank()&&!anonymousDenied(media.replaceAll("/+$","")+"/"+probe))return false;
            safe=true;return true;
        }catch(Exception failure){return false;}
        finally{try{oss.deleteObject(bucket(),probe);}catch(Exception ignored){safe=false;}}
    }
    private boolean anonymousDenied(String address)throws Exception{
        URI uri=URI.create(address);
        if(!"https".equals(uri.getScheme())||uri.getUserInfo()!=null||uri.getFragment()!=null)return false;
        if(anonymous==null)anonymous=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpRequest request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
        int status=anonymous.send(request,HttpResponse.BodyHandlers.discarding()).statusCode();
        return status==403;
    }
    public void put(String key,InputStream stream,long size,String mime){
        key(key);
        ObjectMetadata metadata=new ObjectMetadata();
        metadata.setContentLength(size);metadata.setContentType(mime);metadata.setCacheControl("private, no-store");
        metadata.setObjectAcl(CannedAccessControlList.Private);
        oss.putObject(new PutObjectRequest(bucket(),key,stream,metadata));
    }
    public void putChecked(String key,InputStream stream,long size,String mime){
        if(!ready())throw KnowledgeFailure.unavailable();
        try{
            put(key,stream,size,mime);
            if(oss.getObjectAcl(bucket(),key).getPermission()!=ObjectPermission.Private)
                throw KnowledgeFailure.unavailable();
        }catch(RuntimeException failure){try{delete(key);}catch(Exception ignored){}throw failure;}
    }
    public OSSObject open(String key){key(key);return oss.getObject(bucket(),key);}
    public void delete(String key){key(key);oss.deleteObject(bucket(),key);}
    /** Scan only the knowledge object prefix; a failed listing aborts cleanup without touching other media. */
    synchronized List<String> oldObjectKeys(Instant before){
        List<String> candidates=new ArrayList<>();String marker=inventoryMarker;
        for(int page=0;page<20;page++){
            ListObjectsRequest request=new ListObjectsRequest(bucket());
            request.setPrefix(prefix()+"objects/");request.setMaxKeys(100);request.setMarker(marker);
            var listing=oss.listObjects(request);
            for(var object:listing.getObjectSummaries()){
                String objectKey=object.getKey();
                if(object.getLastModified()!=null&&object.getLastModified().toInstant().isBefore(before)
                    &&objectKey.startsWith(prefix()+"objects/"))candidates.add(objectKey);
            }
            if(!listing.isTruncated()){inventoryMarker=null;break;}
            String next=listing.getNextMarker();
            if(next==null&&!listing.getObjectSummaries().isEmpty())
                next=listing.getObjectSummaries().get(listing.getObjectSummaries().size()-1).getKey();
            if(next==null||next.equals(marker)){inventoryMarker=null;break;}
            marker=next;inventoryMarker=marker;
        }
        return candidates;
    }
}
