"""One-off, guarded gallery update for eight published experience projects."""

import runpy

helpers = runpy.run_path('/tmp/migrate-website-share.py')
mysql = helpers['mysql']
backup = helpers['backup']


def quote(value):
    return "'" + value.replace('\\', '\\\\').replace("'", "''").replace('\n', '\\n') + "'"


BASE = 'https://www.zhengluyu.com/experience-gallery/'
NOTICE = ('以下为依据项目现有功能重新绘制的 AI 示意图，并非真实系统截图。'
          '图中的账号、内容、金额、日期与案例均是脱敏后的虚构示例，不代表客户数据或项目指标。')
GALLERIES = {
    4: [('links', '链接导入与错误明细', '管理端需要让导入结果可追查：平台链接进入任务后，业务人员能区分成功记录与失败原因，并继续核对原始输入。'),
        ('tasks', '关键词点检与任务进度', '点检任务将链接获取和标题情感分析作为不同执行阶段，页面呈现筛选条件、任务状态、结果查看与导出入口，便于定位尚未完成的环节。'),
        ('media', '媒体登录助手', '账号准备属于同一验收链路：登录助手以账号列表、平台和状态筛选、重新登录或重新绑定等操作支持后续任务执行。')],
    5: [('product', '商品配置与上游订单', '商品属性及推广信息由管理端维护，并与上游订单状态相衔接；展示时应让商品、订单和实际结算口径保持一致。'),
        ('commission', '佣金明细与提现校验', '佣金页面按日期及结算状态查询，区分待结算记录；提现环节需要短信验证码校验，不能把订单金额直接等同于可提现金额。'),
        ('poster', '动态商品海报', '推广海报依据商品配置生成展示素材，供分享使用；图片中的产品和价格只是示例，不是实际运营数据。')],
    6: [('home', '企业官网首页与内容入口', '官网按产品、服务、解决方案、案例、新闻及关于/联系等栏目组织内容，使访客能从入口进入对应主题。'),
        ('cms', '新闻内容维护', '内容管理端围绕标题、摘要、正文、封面和发布状态维护资讯；页面更新需要与官网公开展示保持一致。')],
    7: [('generate', '关键词文章生成', '从关键词与提示词配置进入生成流程，观察任务进度与结果，再对正文和素材做人工检查，避免只看任务结束状态。'),
        ('manage', '文章管理与素材处理', '文章列表承担状态筛选、批量操作、封面和配图素材准备及发布管理；失败反馈帮助定位需要重新处理的文章。')],
    8: [('landing', '产品首页与功能介绍', '展示页以产品介绍和访问路径为核心，介绍多平台数据抓取、地理位置分析、监测预警与报告生成等产品功能；这不是核心 AI 算法或业务后台的截图。'),
        ('sections', '优势、示例案例与联系区域', '下方区块延续产品优势、案例展示和联系入口。图中的案例为视觉占位，不表示真实客户交付或量化成果。')],
    9: [('search', '账号、视频及综合查询', '本地调试页使用关键词与查询类型切换，分别查看账号和视频结果，并用下一页继续读取；它不是视频播放或社区互动产品。'),
        ('detail', '分享链接详情与评论树', '从分享链接或内容标识进入详情查询，读取账号、内容及嵌套评论，按页继续获取回复；只读查询不包含发表评论功能。')],
    10: [('download', '内容下载任务', '下载链路围绕来源链接、任务状态和文件保存组织。图像只是对项目能力的视觉重绘，具体界面与代码版本可能不同。'),
         ('search', '账号与视频检索', 'Go 服务的本地调试入口支持账号、视频与综合检索以及翻页；查询参数和返回结果分别按场景组织。'),
         ('detail', '内容详情和关联评论', '通过分享链接或内容标识进入详情及评论读取，包含回复层级和分页限制。代码库存在任职期后的持续迭代，此处不把后续功能归为任职期已交付成果。')],
    11: [('assembly', '计算机产线组装', '参与面向政府供货的计算机装配，按产线分工完成本人负责的组装工序，与前后环节衔接；图片不对应具体客户设备或型号。'),
         ('install', '华为笔记本系统安装', '为华为笔记本安装操作系统，完成设备后续配置和使用前的系统准备；不将系统研发或整批交付管理写成个人职责。')],
}
EXPECTED = {4: '内容验收与多平台任务平台', 5: '号卡业务平台', 6: '企业官网与内容管理平台',
            7: '内容生成与发布工具', 8: 'AI 搜索产品展示页', 9: '视频内容查询服务',
            10: '微信数据抓取', 11: '计算机硬件组装和系统安装'}


if __name__ == '__main__':
    rows = mysql('SELECT id,experience_id,project_name,status,is_deleted,INSTR(content,\'## 功能示意图\') '
                 'FROM t_experience_project WHERE id IN (4,5,6,7,8,9,10,11) ORDER BY id')
    found = [line.split('\t') for line in rows.splitlines()]
    if len(found) != 8 or any(int(row[0]) not in EXPECTED or row[2] != EXPECTED[int(row[0])]
                              or row[3:] != ['1', '0', '0']
                              or int(row[1]) != (1 if int(row[0]) >= 10 else 2) for row in found):
        raise RuntimeError('Published records or gallery state changed; no update performed')
    backup_path = backup()
    statements = ['START TRANSACTION']
    for project_id, items in GALLERIES.items():
        section = '\n\n## 功能示意图\n\n' + NOTICE + '\n'
        for slug, title, caption in items:
            section += f'\n### {title}\n\n![{title}功能示意]({BASE}project-{project_id}-{slug}.png)\n\n{caption}\n'
        cover = BASE + f'project-{project_id}-cover.png'
        statements.append('UPDATE t_experience_project SET cover_image=' + quote(cover) +
                          ', content=CONCAT(content,' + quote(section) +
                          '), update_time=NOW() WHERE id=' + str(project_id) +
                          " AND status=1 AND is_deleted=0 AND INSTR(content,'## 功能示意图')=0")
    statements.append('COMMIT')
    mysql(';\n'.join(statements) + ';')
    print('Updated eight published project galleries. Backup:', backup_path)
