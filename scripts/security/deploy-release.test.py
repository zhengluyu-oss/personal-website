"""Execute the actual release script against disposable paths and command boundaries.
On Windows set BLOG_QA_BASH to Git Bash; no SSH, production files or services are used.
"""
import json, os, pathlib, shutil, subprocess, sys, tarfile, tempfile, unittest

ROOT=pathlib.Path(__file__).resolve().parents[2]
BASH=os.environ.get('BLOG_QA_BASH','bash')
def unix(p):
    s=str(p).replace('\\','/')
    return '/'+s[0].lower()+s[2:] if len(s)>1 and s[1]==':' else s

CURL=r'''
import json,os,pathlib,sys
a=sys.argv[1:]; url=a[-1]; base=pathlib.Path(os.environ['QA_ROOT'])
def value(k): return a[a.index(k)+1] if k in a else None
def write(p,s):
    if p and p not in ('/dev/null','nul','NUL'): pathlib.Path(p).write_text(s)
if 'site-modules/access' in url:
    fresh=(base/'custom-app/blog-backend.jar').exists() and (base/'custom-app/blog-backend.jar').read_text()=='new-jar'
    mode=os.environ.get('QA_FAIL','')
    d=[dict(key=k,publicAccess=False,allowed=False) for k in ('home','experience','blog','shares','photos','about','diary')]
    if not fresh and os.environ.get('QA_LEGACY_BACKEND'): d=d[:6]
    r=dict(code=200,data=d)
    if fresh and mode in ('health','recovery'): r['code']=500
    if fresh and mode=='shape': r['data']=d[:5]
    write(value('-o'),json.dumps(r)); print('200',end='')
else:
    bad=os.environ.get('QA_FAIL')=='asset'
    if url.endswith('index.html'):
        prefix='/admin/' if '/admin/' in url else '/'
        write(value('-o'),'<html><script src="'+prefix+'assets/main-123.js"></script><link href="'+prefix+'assets/main-123.css"></html>')
        write(value('-D'),'Content-Type: text/html\nCache-Control: no-cache, no-store\n')
    else:
        kind='text/html' if bad else ('text/css' if url.endswith('.css') else 'application/javascript')
        mode=os.environ.get('QA_FAIL','')
        encoding={'small_asset':'Content-Length: 64\n',
                  'uncompressed_asset':'Content-Length: 2048\n',
                  'unknown_size_asset':'',
                  'unexpected_encoding':'Content-Length: 64\nContent-Encoding: br\n'}.get(mode,'Content-Encoding: gzip\n')
        write(value('-D'),'Content-Type: '+kind+'\nCache-Control: public, immutable\n'+encoding)
'''

class Release(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='luyu-release-qa-'); self.root=pathlib.Path(self.temp.name)
        self.bin=self.root/'bin'; self.bin.mkdir(); self.stage=self.root/'stage'; self.stage.mkdir()
        self.app=self.root/'custom-app'; self.blog=self.root/'custom-blog'; self.admin=self.root/'custom-admin'; self.config=self.root/'custom-site'
        for p in (self.app,self.blog,self.admin): p.mkdir()
        (self.app/'blog-backend.jar').write_text('old-jar')
        for p in (self.blog,self.admin):
            (p/'index.html').write_text('old-'+p.name); previous=pathlib.Path(str(p)+'.previous'); previous.mkdir(); (previous/'keep.txt').write_text('older-'+p.name)
        self.config.write_text('old-config')
        (self.root/'curl.py').write_text(CURL)
        python=unix(sys.executable)
        self.stub('python3',f'#!/bin/bash\nexec "{python}" "$@"\n')
        self.stub('curl',f'#!/bin/bash\nexec "{python}" "{unix(self.root / "curl.py")}" "$@"\n')
        self.stub('systemctl',r'''#!/bin/bash
echo "systemctl $*" >> "$QA_ROOT/actions"
if [[ $1 == show ]]; then echo "java -jar $QA_APP/blog-backend.jar --spring.profiles.active=prod --spring.config.additional-location=file:/etc/qa.yml"; fi
if [[ $1 == restart && $QA_FAIL == restart && $(cat "$QA_APP/blog-backend.jar") == new-jar ]]; then exit 1; fi
''')
        self.stub('nginx',r'''#!/bin/bash
echo "nginx $*" >> "$QA_ROOT/actions"
if [[ $QA_FAIL == nginx && $(cat "$QA_CONFIG") == new-config ]]; then exit 1; fi
''')
        self.stub('cp',r'''#!/bin/bash
last=${!#}
if [[ $QA_FAIL == backup && $last == *luyu-release-backup* ]]; then exit 1; fi
if [[ $QA_FAIL == place && $last == "$QA_APP/blog-backend.jar" && $* != *luyu-release-backup* ]]; then exit 1; fi
if [[ $QA_FAIL == recovery && $last == "$QA_APP/blog-backend.jar" && $* == *luyu-release-backup* ]]; then exit 1; fi
exec /usr/bin/cp "$@"
''')
        self.env=dict(os.environ,QA_ROOT=unix(self.root),QA_APP=unix(self.app),QA_CONFIG=unix(self.config),QA_FAIL='',DEPLOY_PROBE_ATTEMPTS='2',DEPLOY_PROBE_DELAY='0',DEPLOY_BACKUP_PARENT=unix(self.root))
        self.env['PATH']=str(self.bin)+os.pathsep+self.env['PATH']
    def stub(self,name,content):
        p=self.bin/name; p.write_text(content,newline='\n'); p.chmod(0o755)
    def tearDown(self): self.temp.cleanup()
    def run_release(self,selected,fail='',first=False):
        if first: (self.app/'blog-backend.jar').unlink()
        for c in selected.split(','):
            p=self.stage/('app' if c=='backend' else c); p.mkdir()
            if c=='backend': (p/'blog-backend.jar').write_text('new-jar')
            else: (p/'index.html').write_text('new-'+c)
        if 'blog' in selected.split(','):
            (self.stage/'nginx').mkdir(); (self.stage/'nginx/zhengluyu-blog').write_text('new-config')
        archive=self.root/'release.tar.gz'
        with tarfile.open(archive,'w:gz') as t:
            for p in self.stage.iterdir(): t.add(p,arcname=p.name)
        self.env['QA_FAIL']=fail
        # Git Bash prepends its own utilities at startup; explicitly select the fake boundaries afterwards.
        args=[BASH,'-c','export PATH="$1:$PATH"; shift; source "$@"','qa',unix(self.bin),unix(ROOT/'scripts/deploy-release.sh'),unix(archive),unix(self.app),unix(self.blog),unix(self.admin),unix(self.config),'qa-unit',selected,'example.invalid']
        return subprocess.run(args,env=self.env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=40)
    def old_untouched_front(self):
        for p in (self.blog,self.admin):
            self.assertEqual((p/'index.html').read_text(),'old-'+p.name)
            self.assertEqual((pathlib.Path(str(p)+'.previous')/'keep.txt').read_text(),'older-'+p.name)
    def test_backend_only_does_not_consume_frontend_previous(self):
        r=self.run_release('backend'); self.assertEqual(r.returncode,0,r.stdout); self.old_untouched_front()
        self.assertEqual((self.app/'blog-backend.jar').read_text(),'new-jar'); self.assertEqual(self.config.read_text(),'old-config')
    def test_frontend_only_does_not_restart_backend(self):
        r=self.run_release('admin'); self.assertEqual(r.returncode,0,r.stdout)
        self.assertEqual((self.app/'blog-backend.jar').read_text(),'old-jar'); self.assertNotIn('systemctl',(self.root/'actions').read_text())
        self.assertEqual((pathlib.Path(str(self.admin)+'.previous')/'index.html').read_text(),'old-custom-admin')
    def test_rollback_can_verify_legacy_six_module_backend(self):
        self.env['QA_LEGACY_BACKEND']='1'
        r=self.run_release('backend','health')
        self.assertEqual(r.returncode,1,r.stdout)
        self.assertIn('DEPLOYMENT_RECOVERED',r.stdout)
        self.assertNotIn('RECOVERY_FAILED',r.stdout)
        self.assertEqual((self.app/'blog-backend.jar').read_text(),'old-jar')
    def test_combined_private_blog_is_healthy(self):
        r=self.run_release('backend,blog,admin'); self.assertEqual(r.returncode,0,r.stdout)
        self.assertEqual(self.config.read_text(),'new-config'); self.assertEqual((self.blog/'index.html').read_text(),'new-blog')
        self.assertFalse(list(self.root.glob('luyu-release-backup-*')))
    def test_backend_failures_restore_exact_old_jar_and_leave_other_components(self):
        for failure in ('backup','place','restart','health','shape'):
            with self.subTest(failure=failure):
                if (self.stage/'app').exists(): shutil.rmtree(self.stage/'app')
                r=self.run_release('backend',failure); self.assertEqual(r.returncode,1,r.stdout)
                self.assertIn('DEPLOYMENT_RECOVERED',r.stdout)
                self.assertEqual((self.app/'blog-backend.jar').read_text(),'old-jar'); self.old_untouched_front()
                backups=list(self.root.glob('luyu-release-backup-*')); self.assertTrue(backups)
                self.assertTrue((backups[-1]/'manifest.tsv').exists())
    def test_combined_nginx_failure_recovers_each_snapshot_and_restarts_old_backend(self):
        r=self.run_release('backend,blog,admin','nginx'); self.assertEqual(r.returncode,1,r.stdout)
        self.old_untouched_front(); self.assertEqual(self.config.read_text(),'old-config')
        self.assertEqual((self.app/'blog-backend.jar').read_text(),'old-jar')
        self.assertEqual((self.root/'actions').read_text().count('systemctl restart'),2)
    def test_html_disguised_as_asset_fails_and_restores_custom_admin(self):
        r=self.run_release('admin','asset'); self.assertEqual(r.returncode,1,r.stdout); self.old_untouched_front()
        self.assertNotIn('systemctl',(self.root/'actions').read_text())
    def test_small_identity_assets_below_nginx_threshold_are_accepted(self):
        r=self.run_release('admin','small_asset'); self.assertEqual(r.returncode,0,r.stdout)
        self.assertEqual((self.admin/'index.html').read_text(),'new-admin')
        self.assertNotIn('systemctl',(self.root/'actions').read_text())
    def test_large_or_unknown_size_identity_assets_and_unrequested_encoding_recover(self):
        for failure in ('uncompressed_asset','unknown_size_asset','unexpected_encoding'):
            with self.subTest(failure=failure):
                if (self.stage/'admin').exists(): shutil.rmtree(self.stage/'admin')
                r=self.run_release('admin',failure); self.assertEqual(r.returncode,1,r.stdout)
                self.assertIn('DEPLOYMENT_RECOVERED',r.stdout); self.old_untouched_front()
    def test_recovery_failure_is_distinct_and_keeps_manifest(self):
        r=self.run_release('backend','recovery'); self.assertEqual(r.returncode,2,r.stdout)
        self.assertIn('RECOVERY_FAILED',r.stdout); self.assertTrue(list(self.root.glob('luyu-release-backup-*/manifest.tsv')))
    def test_first_install_reports_no_old_backend(self):
        r=self.run_release('backend','health',first=True); self.assertEqual(r.returncode,1,r.stdout)
        self.assertIn('FIRST_INSTALL_NO_PREVIOUS_BACKEND',r.stdout); self.assertFalse((self.app/'blog-backend.jar').exists())

if __name__=='__main__': unittest.main()
