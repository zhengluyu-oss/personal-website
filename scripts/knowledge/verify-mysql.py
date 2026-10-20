"""Use only a labeled disposable localhost MySQL container and synthetic records."""
import json, os, pathlib, subprocess, time, uuid

ROOT=pathlib.Path(__file__).resolve().parents[2]
NAME='luyu-knowledge-mysql-qa-'+uuid.uuid4().hex[:10]
LABEL='add-knowledge-library-and-connections'

def run(args, body=None, check=True):
    result=subprocess.run(args,input=body,capture_output=True,text=True,encoding='utf-8',errors='replace')
    if check and result.returncode: raise RuntimeError(result.stdout[-3000:]+result.stderr[-3000:])
    return result

receipt=None
try:
    run(['docker','run','-d','--name',NAME,'--label','codex.change='+LABEL,'--memory','768m','--cpus','1',
         '-p','127.0.0.1:15434:3306','-e','MYSQL_ROOT_PASSWORD=isolated-knowledge-mysql',
         '-e','MYSQL_DATABASE=knowledge_qa','mysql:8.4'])
    receipt=json.loads(run(['docker','inspect',NAME]).stdout)[0]
    for _ in range(120):
        if run(['docker','exec',NAME,'mysql','-uroot','-pisolated-knowledge-mysql','-e','SELECT 1'],check=False).returncode==0:break
        time.sleep(.5)
    else:raise RuntimeError('QA MySQL unavailable')
    setup='CREATE TABLE IF NOT EXISTS t_article(id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,status INT NOT NULL,is_deleted INT NOT NULL) ENGINE=InnoDB;\n'
    setup+='CREATE TABLE IF NOT EXISTS sys_site_module(module_key VARCHAR(32) PRIMARY KEY,public_access INT NOT NULL,revision INT NOT NULL) ENGINE=InnoDB;\n'
    setup+=(ROOT/'sql/knowledge-article-export.sql').read_text(encoding='utf-8')
    setup+=(ROOT/'sql/knowledge-module-access.sql').read_text(encoding='utf-8')*2
    for _ in range(120):
        setup_result=run(['docker','exec','-i',NAME,'mysql','-uroot','-pisolated-knowledge-mysql','knowledge_qa'],setup,check=False)
        if setup_result.returncode==0:break
        if not any(code in setup_result.stderr for code in ('ERROR 1045','ERROR 2002','ERROR 2003')):
            raise RuntimeError(setup_result.stderr[-3000:])
        time.sleep(.5)
    else:raise RuntimeError('QA schema unavailable: '+setup_result.stderr[-1000:])
    policy=run(['docker','exec',NAME,'mysql','-N','-uroot','-pisolated-knowledge-mysql','knowledge_qa','-e',
                "SELECT CONCAT(public_access,':',revision) FROM sys_site_module WHERE module_key='knowledge'"]).stdout.strip()
    assert policy=='0:0',policy
    env=os.environ.copy();env['BLOG_KNOWLEDGE_MYSQL_QA']='true'
    command=['mvn.cmd' if os.name=='nt' else 'mvn','-q','-Dtest=KnowledgeArticleDraftMysqlTest','test']
    result=subprocess.run(command,cwd=ROOT/'blog-backend',env=env,capture_output=True,text=True,encoding='utf-8',errors='replace')
    if result.returncode:raise RuntimeError(result.stdout[-5000:]+result.stderr[-2000:])
    print(json.dumps({'mysqlTransaction':True,'retryMapping':True,'deletedDraftGuard':True,'restrictedPolicyRepeat':True}))
finally:
    if receipt:
        current=json.loads(run(['docker','inspect',NAME]).stdout)[0]
        assert current['Id']==receipt['Id'] and current['Config']['Labels']['codex.change']==LABEL
        run(['docker','rm','-f',NAME])
