package xyz.luyu.knowledge;

/** Child-process fixture, never loaded by the application. */
public final class KnowledgeParserCrashFixture {
    private KnowledgeParserCrashFixture() {}
    public static void main(String[] args) throws Exception {
        if("sleep".equals(args[0]))Thread.sleep(10000);
        if("oom".equals(args[0])){
            try{byte[] large=new byte[128*1024*1024];System.out.write(large[0]);}
            catch(OutOfMemoryError expected){System.exit(7);}
        }
    }
}
