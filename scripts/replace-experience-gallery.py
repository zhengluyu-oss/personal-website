"""Replace the previous experience gallery with source-informed v2 visuals."""

import runpy

previous = runpy.run_path('/tmp/add-experience-gallery.py')
mysql = previous['mysql']
backup = previous['backup']
quote = previous['quote']
base = previous['BASE']
expected = previous['EXPECTED']

notice = (
    '以下是依据项目现有前端代码、页面结构和功能重新绘制的 AI 示意图，'
    '并非真实系统截图。图中的账号、内容、金额、日期与案例均为虚构示例，'
    '不代表客户数据或项目指标。'
)

galleries = {
    4: [
        ('links', '链接导入任务', '管理端按任务编号、状态和文件名查询链接导入记录，核对总数、成功数与失败数，再进入详情追查。'),
        ('tasks', '关键词点检任务', '列表提供所属项目与任务状态筛选，分别展示链接获取和标题情感分析的进度，并可查看详情或调整项目。'),
        ('media', '媒体登录助手', '独立客户端按平台、状态和关键词筛选已登录账号，支持打开浏览器、更换代理及重新登录。'),
    ],
    5: [
        ('product', '上游订单查询', '订单管理提供订单编号、外部编号、开卡号码和收件人号码等筛选，以及导出与同步订单操作。'),
        ('commission', '商品佣金配置', '商品配置页分别维护基础信息、结算相关设置和基础佣金，并可添加合伙人及保存佣金金额。'),
        ('poster', '高德海报主图配置', '海报弹窗基于产品名、运营商、通话横幅、流量和月租等商品参数生成主图；示例值不代表真实商品。'),
    ],
    6: [
        ('home', '产品与服务展示', '企业官网以产品、服务、解决方案等栏目组织内容，让访客从首页进入对应主题。'),
        ('cms', '资讯动态展示', '资讯列表展示封面、日期、标题和摘要，内容由管理端维护并在官网公开呈现。'),
    ],
    7: [
        ('generate', '单关键词文章生成', '页面以关键词输入和生成操作为核心，结果仍需经过人工核查。'),
        ('manage', '文章管理与预览', '管理页支持关键词和状态筛选、预览、评分、删除及批量操作，并处理封面与发布状态。'),
    ],
    8: [
        ('landing', '核心功能展示', '展示页的四个功能入口分别介绍多平台数据抓取、地理位置分析、实时监测预警和智能报告生成。'),
        ('sections', '关于与联系区域', '页面提供公司介绍与联系入口；这里没有把页面中的示例数字当作真实项目成果。'),
    ],
    9: [
        ('search', '账号与视频检索', '本地搜索测试页通过关键词、类型选择及翻页读取账号和视频结果。'),
        ('detail', '评论树查询', '调试工具通过内容标识查询嵌套评论与回复，不包含发表评论功能。'),
    ],
    10: [
        ('download', '视频号页面下载入口', '浏览器侧注入界面提供视频内容下载入口，并通过浏览器下载列表查看保存结果。'),
        ('search', '账号与视频检索', '本地搜索测试页支持账号、视频与综合检索，以及状态刷新和翻页。'),
        ('detail', '视频详情查询', '深色调试工具按视频标识读取详情并查看结构化返回结果；示例数据是虚构的。'),
    ],
    11: [
        ('assembly', '计算机产线组装', '这张场景图对应产线计算机组装工作，不对应具体客户设备、工位或型号。'),
        ('install', '华为笔记本系统安装', '这张场景图对应笔记本操作系统安装与使用前准备，不代表实际交付现场照片。'),
    ],
}


if __name__ == '__main__':
    rows = mysql(
        "SELECT id,experience_id,project_name,status,is_deleted,"
        "cover_image,INSTR(content,'## 功能示意图') "
        "FROM t_experience_project WHERE id IN (4,5,6,7,8,9,10,11) ORDER BY id"
    )
    found = [line.split('\t') for line in rows.splitlines()]
    if len(found) != 8:
        raise RuntimeError('Expected eight published projects; no update performed')
    for row in found:
        project_id = int(row[0])
        old_cover = base + f'project-{project_id}-cover.png'
        if (project_id not in expected or row[2] != expected[project_id]
                or row[1] != ('1' if project_id >= 10 else '2')
                or row[3:5] != ['1', '0'] or row[5] != old_cover
                or int(row[6]) <= 0):
            raise RuntimeError(f'Project {project_id} no longer matches old gallery; no update performed')

    backup_path = backup()
    statements = ['START TRANSACTION']
    for project_id, items in galleries.items():
        section = '\n\n## 功能示意图\n\n' + notice + '\n'
        for slug, title, caption in items:
            image_url = base + f'project-{project_id}-{slug}-v2.png'
            section += f'\n### {title}\n\n![{title}功能示意]({image_url})\n\n{caption}\n'
        new_cover = base + f'project-{project_id}-cover-v2.png'
        old_cover = base + f'project-{project_id}-cover.png'
        statements.append(
            'UPDATE t_experience_project SET cover_image=' + quote(new_cover)
            + ', content=CONCAT(SUBSTRING_INDEX(content,'
            + quote('## 功能示意图') + ',1),' + quote(section) + ')'
            + ', update_time=NOW() WHERE id=' + str(project_id)
            + ' AND status=1 AND is_deleted=0 AND cover_image=' + quote(old_cover)
            + ' AND INSTR(content,' + quote('## 功能示意图') + ')>0'
        )
    statements.append('COMMIT')
    mysql(';\n'.join(statements) + ';')
    print('Replaced eight project galleries. Backup:', backup_path)
