"""Guarded server-side text update. Uses the existing private migration connection helper."""
import argparse
import json
from pathlib import Path
import runpy

FIELDS = {'projectSummary': 'project_summary', 'responsibilities': 'responsibilities',
          'techStack': 'tech_stack', 'content': 'content', 'summary': 'summary',
          'contributions': 'contributions', 'outcomes': 'outcomes'}


def literal(value):
    if value is None:
        return 'NULL'
    return "CONVERT(0x" + value.encode('utf-8').hex() + " USING utf8mb4)" if value else "''"


def statements(plan, rollback=False):
    changes = plan['changes']
    if [(c['kind'], c['id']) for c in changes] != [('experience', 2)] + [('project', i) for i in range(4, 10)]:
        raise ValueError('Unexpected record scope')
    predicates, updates = [], []
    for c in changes:
        company = c['kind'] == 'experience'
        table = 't_work_experience' if company else 't_experience_project'
        allowed = {'projectSummary', 'responsibilities', 'techStack', 'content'} if company else {'summary', 'techStack', 'contributions', 'outcomes', 'content'}
        if set(c['before']) != allowed or set(c['after']) != allowed:
            raise ValueError('Unexpected fields')
        before, after = (c['after'], c['before']) if rollback else (c['before'], c['after'])
        identity = f"id={c['id']} AND status=1 AND is_deleted=0"
        identity += (' AND company=' if company else ' AND experience_id=2 AND project_name=') + literal(c['name'])
        guards = [f'BINARY {FIELDS[k]} <=> BINARY {literal(v)}' for k, v in before.items()]
        predicates.append(f'(SELECT COUNT(*) FROM {table} WHERE {identity} AND ' + ' AND '.join(guards) + ')=1')
        assignments = ','.join(FIELDS[k] + '=' + literal(v) for k, v in after.items())
        updates.append(f'UPDATE {table} SET {assignments},update_time=NOW() WHERE id={c["id"]} AND @ready=1')
    guard = ' AND '.join(predicates)
    check = 'SELECT ' + guard + ';'
    sql = ['START TRANSACTION', 'SELECT id FROM t_work_experience WHERE id=2 FOR UPDATE',
           'SELECT id FROM t_experience_project WHERE id IN (4,5,6,7,8,9) ORDER BY id FOR UPDATE',
           'SET @ready=(' + guard + ')', 'SELECT @ready'] + updates + ['COMMIT']
    return check, ';\n'.join(sql) + ';'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('plan', type=Path)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--rollback', action='store_true')
    args = parser.parse_args()
    plan = json.loads(args.plan.read_text(encoding='utf-8-sig'))
    helper = runpy.run_path('/tmp/migrate-website-share.py')
    mysql = helper['mysql']
    check, sql = statements(plan, args.rollback)
    if mysql(check) != '1':
        raise RuntimeError('Authoritative records changed; nothing updated')
    if not args.apply:
        print('CHECK_OK: seven records match; no writes')
        return
    helper['backup']()
    output = mysql(sql).splitlines()
    if output != ['2', '4', '5', '6', '7', '8', '9', '1']:
        raise RuntimeError('Concurrent change detected; guarded updates skipped')
    verify, _ = statements(plan, not args.rollback)
    if mysql(verify) != '1':
        raise RuntimeError('Post-update verification failed')
    print('VERIFIED: seven records updated; identity, dates, covers and order untouched')


if __name__ == '__main__':
    main()
