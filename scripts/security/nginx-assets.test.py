"""Real disposable Nginx. Requires the existing local nginx:latest image; never pulls it."""
import pathlib, re, subprocess, tempfile, time, unittest, urllib.request, urllib.error, uuid

ROOT=pathlib.Path(__file__).resolve().parents[2]
class NginxAssets(unittest.TestCase):
    def test_current_previous_missing_and_spa(self):
        subprocess.run(['docker','image','inspect','nginx:latest'],check=True,stdout=subprocess.DEVNULL)
        with tempfile.TemporaryDirectory(prefix='luyu-nginx-qa-') as tmp:
            root=pathlib.Path(tmp); site=root/'site'; site.mkdir()
            values={'admin/index.html':'<html>admin entry</html>', 'admin/assets/new-123.js':'window.current=true;',
                    'admin.previous/assets/old-456.js':'window.previous=true;', 'admin/assets/new-123.css':'body{color:red}',
                    'admin/_app.config.js':'window.config={};', 'blog/index.html':'<html>blog entry</html>',
                    'blog/js/new-123.js':'window.blog=true;', 'blog.previous/js/old-456.js':'window.oldBlog=true;'}
            for name,value in values.items():
                p=site/name; p.parent.mkdir(parents=True,exist_ok=True); p.write_text(value)
            source=(ROOT/'deploy/nginx/zhengluyu-blog.conf').read_text(encoding='utf-8')
            conf=source[source.rfind('server {'):]
            conf=re.sub(r'^\s*(listen|ssl_)[^\n]+\n','',conf,flags=re.M)
            conf=conf.replace('server {','server {\n listen 8080;')
            conf=conf.replace('/home/wwwroot/zhengluyu-website','/srv/site')
            (root/'default.conf').write_text(conf)
            name='luyu-nginx-qa-'+uuid.uuid4().hex[:10]
            cmd=['docker','run','-d','--rm','--name',name,'-p','127.0.0.1::8080',
                 '--mount',f'type=bind,source={site},target=/srv/site,readonly',
                 '--mount',f'type=bind,source={root/"default.conf"},target=/etc/nginx/conf.d/default.conf,readonly','nginx:latest']
            subprocess.run(cmd,check=True,stdout=subprocess.DEVNULL)
            try:
                subprocess.run(['docker','exec',name,'nginx','-t'],check=True,stdout=subprocess.DEVNULL)
                port=subprocess.check_output(['docker','port',name,'8080/tcp'],text=True).strip().split(':')[-1]
                def get(path):
                    try:
                        with urllib.request.urlopen('http://127.0.0.1:'+port+path,timeout=3) as r: return r.status,r.headers,r.read().decode()
                    except urllib.error.HTTPError as e: return e.code,e.headers,e.read().decode()
                for _ in range(20):
                    try: get('/admin/index.html'); break
                    except OSError: time.sleep(.1)
                for path,key in [('/admin/assets/new-123.js','admin/assets/new-123.js'),('/admin/assets/old-456.js','admin.previous/assets/old-456.js'),
                                 ('/js/new-123.js','blog/js/new-123.js'),('/js/old-456.js','blog.previous/js/old-456.js')]:
                    status,h,body=get(path); self.assertEqual(status,200,path); self.assertEqual(body,values[key]);
                    self.assertRegex(h['Content-Type'],r'(application|text)/javascript'); self.assertIn('immutable',','.join(h.get_all('Cache-Control',[])))
                status,h,body=get('/admin/assets/new-123.css'); self.assertEqual(status,200); self.assertIn('text/css',h['Content-Type'])
                for path in ['/admin/assets/missing-789.js','/admin/other/missing.css','/js/missing.js']:
                    status,h,body=get(path); self.assertEqual(status,404,path); self.assertNotIn('entry',body)
                    if path.startswith('/admin/'): self.assertNotIn('immutable',h.get('Cache-Control',''))
                for path in ['/admin/index.html','/admin/articles/42','/index.html','/articles/42']:
                    status,h,body=get(path); self.assertEqual(status,200,path); self.assertIn('text/html',h['Content-Type']); self.assertIn('no-cache',h['Cache-Control'])
                status,h,body=get('/admin/_app.config.js'); self.assertEqual(status,200); self.assertIn('no-cache',h['Cache-Control'])
            finally: subprocess.run(['docker','stop',name],check=True,stdout=subprocess.DEVNULL)

if __name__=='__main__': unittest.main()
