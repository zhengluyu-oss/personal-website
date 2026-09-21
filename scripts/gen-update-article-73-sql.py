import pathlib

content = pathlib.Path(__file__).with_name('article-73-content.md').read_text(encoding='utf-8')
fields = {
    'article_title': 'TypeScript 基础入门：类型系统与工程化实践',
    'seo_title': 'TypeScript 基础入门：类型系统、泛型与工程化实践 | 郑陆宇',
    'seo_keywords': 'TypeScript,类型系统,接口,泛型,前端工程化,JavaScript,Vue3,学习笔记,面试复习',
    'seo_description': (
        '面向就业展示与个人备忘的 TypeScript 入门笔记：涵盖基础类型、interface/type、'
        '函数与泛型、类型收窄与 tsconfig 工程化要点，便于面试复习与日常查阅。'
    ),
    'article_content': content,
}
parts = []
for key, value in fields.items():
    esc = value.replace('\\', '\\\\').replace("'", "''")
    parts.append(f"{key}='{esc}'")
sql = 'UPDATE t_article SET ' + ', '.join(parts) + ', update_time=NOW() WHERE id=73;'
out = pathlib.Path(__file__).with_name('update-article-73.sql')
out.write_text(sql, encoding='utf-8')
print(f'Wrote {out} ({len(sql)} bytes)')
