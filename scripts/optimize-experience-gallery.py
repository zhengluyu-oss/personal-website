"""Switch eight published project galleries to the optimized WebP assets."""

import runpy

previous = runpy.run_path('/tmp/add-experience-gallery.py')
mysql = previous['mysql']
backup = previous['backup']
quote = previous['quote']
base = previous['BASE']
expected = previous['EXPECTED']
galleries = runpy.run_path('/tmp/replace-experience-gallery.py')['galleries']


def old_url(project_id, slug):
    return base + f'project-{project_id}-{slug}-v2.png'


def new_url(project_id, slug):
    return base + f'project-{project_id}-{slug}-v3.webp'


if __name__ == '__main__':
    checks = []
    for project_id, items in galleries.items():
        checks.append(
            f"SELECT id,experience_id,project_name,status,is_deleted,cover_image,"
            + ','.join(f"INSTR(content,{quote(old_url(project_id, slug))})" for slug, *_ in items)
            + f' FROM t_experience_project WHERE id={project_id}'
        )
    rows = [row.split('\t') for row in mysql(';\n'.join(checks) + ';').splitlines()]
    if len(rows) != len(galleries):
        raise RuntimeError('Expected eight project rows; no update performed')
    for row in rows:
        project_id = int(row[0])
        if (project_id not in expected or row[2] != expected[project_id]
                or row[1] != ('1' if project_id >= 10 else '2')
                or row[3:5] != ['1', '0']
                or row[5] != old_url(project_id, 'cover')
                or len(row[6:]) != len(galleries[project_id])
                or any(int(position) <= 0 for position in row[6:])):
            raise RuntimeError(f'Project {project_id} gallery differs from expected v2 state; no update performed')

    backup_path = backup()
    statements = ['START TRANSACTION']
    for project_id, items in galleries.items():
        content = 'content'
        for slug, *_ in items:
            content = f'REPLACE({content},{quote(old_url(project_id, slug))},{quote(new_url(project_id, slug))})'
        statements.append(
            f'UPDATE t_experience_project SET cover_image={quote(new_url(project_id, "cover"))},'
            f'content={content},update_time=NOW() WHERE id={project_id}'
            f' AND status=1 AND is_deleted=0'
            f' AND cover_image={quote(old_url(project_id, "cover"))}'
        )
    statements.append('COMMIT')
    mysql(';\n'.join(statements) + ';')
    print('Optimized eight project galleries. Backup:', backup_path)
