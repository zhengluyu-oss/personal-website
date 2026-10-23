package xyz.luyu.knowledge;

import com.alibaba.fastjson2.JSON;
import com.aliyun.oss.model.OSSObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/** Bounded parser invocation; no DB or OSS credentials cross the child-process boundary. */
final class KnowledgeParseRunner {
    @FunctionalInterface interface CommandFactory { List<String> build(Path input,String mime,int maxPages); }
    record ParseFailure(String code,boolean retryable) {}
    record Outcome(List<KnowledgeFileParser.Extracted> segments,ParseFailure failure) {}
    private final KnowledgePrivateObjects objects;
    private final KnowledgeProperties properties;
    private final CommandFactory commandFactory;
    KnowledgeParseRunner(KnowledgePrivateObjects objects,KnowledgeProperties properties){this(objects,properties,KnowledgeParseRunner::command);}
    KnowledgeParseRunner(KnowledgePrivateObjects objects,KnowledgeProperties properties,CommandFactory commandFactory){
        this.objects=objects;this.properties=properties;this.commandFactory=commandFactory;
    }
    Outcome run(String objectKey,String mime,long length){
        Path directory=null;Process child=null;ExecutorService reader=null;
        try{
            Path base=properties.getProcessing().getTemporaryDirectory().isBlank()?Path.of(System.getProperty("java.io.tmpdir")):Path.of(properties.getProcessing().getTemporaryDirectory());
            directory=Files.createTempDirectory(base,"knowledge-parse-");Path input=directory.resolve("source.bin");
            try(OSSObject object=objects.open(objectKey);InputStream stream=object.getObjectContent();var output=Files.newOutputStream(input)){
                byte[] buffer=new byte[8192];long total=0;int n;
                while((n=stream.read(buffer))!=-1){total+=n;if(total>length||total>properties.getProcessing().getTemporaryBytes())return new Outcome(List.of(),new ParseFailure("INPUT_LIMIT",false));output.write(buffer,0,n);}
                if(total!=length)return new Outcome(List.of(),new ParseFailure("TRUNCATED_INPUT",true));
            }
            List<String> command=commandFactory.build(input,mime,properties.getProcessing().getMaxPages());
            ProcessBuilder builder=new ProcessBuilder(command).directory(directory.toFile());builder.environment().clear();builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            child=builder.start();Process running=child;
            reader=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"knowledge-parser-output");t.setDaemon(true);return t;});
            Future<byte[]> output=reader.submit(()->{
                try(InputStream stream=running.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[4096];int n;
                    while((n=stream.read(buffer))!=-1){if(bytes.size()+n>properties.getProcessing().getOutputBytes()+65536L)throw new IllegalStateException("OUTPUT_LIMIT");bytes.write(buffer,0,n);}
                    return bytes.toByteArray();
                }
            });
            if(!child.waitFor(properties.getProcessing().getTimeoutSeconds(),TimeUnit.SECONDS))return new Outcome(List.of(),new ParseFailure("TIMEOUT",true));
            int exit=child.exitValue();
            if(exit!=0)return new Outcome(List.of(),new ParseFailure(switch(exit){case 3->"ENCRYPTED_PDF";case 4->"NEEDS_OCR";case 5->"PAGE_LIMIT";case 6->"OUTPUT_LIMIT";case 7,134,137->"RESOURCE_LIMIT";default->"INVALID_FILE";},exit==7||exit==134||exit==137));
            byte[] json=output.get(2,TimeUnit.SECONDS);
            KnowledgeFileParser.Result parsed=JSON.parseObject(json,KnowledgeFileParser.Result.class);
            if(parsed==null||parsed.segments()==null||parsed.segments().isEmpty())return new Outcome(List.of(),new ParseFailure("NEEDS_OCR",false));
            return new Outcome(parsed.segments(),null);
        }catch(Exception failed){return new Outcome(List.of(),new ParseFailure("PARSER_UNAVAILABLE",true));}
        finally{
            if(child!=null&&child.isAlive()){child.destroyForcibly();try{child.waitFor(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
            if(reader!=null){reader.shutdownNow();try{reader.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
            if(directory!=null)try(var walk=Files.walk(directory)){walk.sorted((a,b)->b.compareTo(a)).forEach(path->{try{Files.deleteIfExists(path);}catch(Exception ignored){}});}catch(Exception ignored){}
        }
    }
    static List<String> command(Path input,String mime,int maxPages){
        String javaBinary=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        List<String> command=new ArrayList<>(List.of(javaBinary,"-Xmx256m","-XX:MaxDirectMemorySize=64m"));
        String launched=System.getProperty("sun.java.command","").split("\\s+",2)[0];
        if(launched.endsWith(".jar")){
            command.add("-Dloader.main="+KnowledgeFileParser.class.getName());command.add("-cp");command.add(Path.of(launched).toAbsolutePath().normalize().toString());
            command.add("org.springframework.boot.loader.launch.PropertiesLauncher");
        }else{
            command.add("-cp");command.add(java.util.Arrays.stream(System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator))).map(path->Path.of(path).toAbsolutePath().normalize().toString()).collect(java.util.stream.Collectors.joining(File.pathSeparator)));command.add(KnowledgeFileParser.class.getName());
        }
        command.add(input.toString());command.add(mime);command.add(Integer.toString(maxPages));return command;
    }
}
