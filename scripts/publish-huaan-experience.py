"""One-off, guarded content update for the verified Hua'an Baitang experience.

Run on the blog server beside migrate-website-share.py, which supplies the
existing datasource reader, mysql client, and full-database backup helper.
"""

import runpy

tools = runpy.run_path('/tmp/migrate-website-share.py')
mysql = tools['mysql']
backup = tools['backup']


def quote(value):
    if value is None:
        return 'NULL'
    return "'" + value.replace('\\', '\\\\').replace("'", "''").replace('\n', '\\n') + "'"


def assignments(values):
    return ', '.join(f'{column}={quote(value)}' for column, value in values.items())


company = {
    'highlights': '参与政府供货计算机的产线组装\n为华为笔记本安装操作系统\n基于开源 Go 项目参与微信数据抓取接口实现',
    'company_introduction': '河北华安百唐信息技术有限公司位于唐山高新区，面向国产计算机硬件及相关软硬件生态开展生产与交付。公开报道介绍，其生产制造中心主要生产台式机、服务器等设备。公司业务背景与我个人的工作内容在下文分别说明。',
    'main_business': '国产台式机、服务器等计算机硬件的生产与交付；国产操作系统及相关软硬件生态。',
    'project_summary': '在产线参与政府供货计算机的组装，为华为笔记本安装操作系统；同时参与基于开源项目的 Go 语言微信数据抓取接口实现。',
    'tech_stack': '计算机硬件组装\n操作系统安装\nGo',
    'responsibilities': '参与政府供货计算机的产线整机组装\n为华为笔记本安装操作系统\n基于开源项目参与微信数据抓取接口实现',
    'metrics': None,
    'content': '''## 工作概况

这段实习同时涉及计算机交付现场与软件项目。硬件方面，我在产线上参与面向政府供货的计算机整机组装，并为华为笔记本安装操作系统。软件方面，我参与了一个以 Go 语言改造开源项目的微信数据抓取项目，承担接口实现相关工作。

两类工作分别对应不同的交付环节：前者关注设备本身的组装与系统准备，后者关注已有开源能力如何适配具体的数据获取需求。这里仅写我实际参与的范围，不把公司的全部产品和业务写成个人成果。

## 公司背景

华安百唐从事国产计算机硬件及相关软硬件生态业务。唐山劳动日报的[公开报道](https://tangshan.huanbohainews.com.cn/2024-04/13/content_50320962.html)介绍，公司在唐山高新区设有生产制造中心，主要生产台式机、服务器等设备。这些是公司背景，不代表我参与了报道中提到的全部产品研发或生产环节。

## 我参与的两项工作

### 计算机硬件组装和系统安装

在政府供货计算机的生产环节，我参与产线上的整机组装工作；在设备系统准备环节，为华为笔记本安装操作系统。项目详情分别说明任务背景与个人职责。由于未核实具体设备型号、系统版本、交付数量或质检指标，这里不填入相关数字和技术参数。

### 微信数据抓取

基于已有开源项目进行 Go 语言改造，参与微信数据抓取相关接口的实现。该项目的可公开信息目前主要来自原有项目草稿；详情页只陈述已确认的项目方向与个人工作，不推断数据规模、抓取方式、账号权限或业务成效。
''',
}

hardware = {
    'project_name': '计算机硬件组装和系统安装',
    'summary': '参与面向政府供货的计算机产线组装，并为华为笔记本安装操作系统，覆盖设备整机组装与系统准备两个实际环节。',
    'role_title': '产线组装与系统安装',
    'tech_stack': '计算机硬件组装\n操作系统安装',
    'contributions': '在产线上参与政府供货计算机的整机组装。\n为华为笔记本安装操作系统。',
    'outcomes': '完成本人参与的设备组装与系统安装工作，为后续设备交付做准备。',
    'content': '''## 项目背景

这项工作面向政府供货计算机的设备准备。对计算机交付而言，整机组装和操作系统安装是两个不同但连续的环节：前者让设备具备完整的硬件形态，后者让笔记本进入可使用的系统环境。

## 我负责的工作

我在流水线上参与计算机整机组装，按产线工作分工完成自己负责的装配任务；另为华为笔记本安装操作系统。这段经历以现场操作为主，不是计算机主板设计、服务器研发或操作系统开发。

## 工作收获

通过实际接触产线组装与系统安装，我更具体地理解了计算机交付并不止于软件开发，还包括设备准备与系统落地。项目没有可核实的交付数量、故障率或性能指标，因此不在这里虚构量化成果。
''',
    'order_num': '1',
    'status': '1',
}

wechat = {
    'project_name': '微信数据抓取',
    'summary': '在已有开源项目基础上使用 Go 语言进行改造，参与微信数据抓取相关接口的实现。',
    'role_title': '接口实现',
    'tech_stack': 'Go\n开源项目改造',
    'contributions': '阅读并改造已有开源项目中与微信数据获取相关的实现。\n参与数据抓取相关接口的实现。',
    'outcomes': '形成面向项目需求的接口实现；未公开或未核实的数据规模、稳定性指标与业务效果不作展示。',
    'content': '''## 项目背景

项目希望利用现有开源能力完成微信数据抓取，而不是从零搭建整套实现。我的工作重点是理解原项目的能力边界，再用 Go 语言对接具体需求并实现相关接口。

## 我的工作

先阅读原有项目中与数据获取有关的代码和接口，再针对项目需要参与改造与接口实现。对外只描述“基于开源项目的改造”和“接口实现”这两个已确认的职责，不把整套项目、数据来源或最终运营结果归为个人独立完成。

## 技术与边界

技术侧使用 Go，依托原有开源项目开展工作。由于当前记录中没有可靠的接口清单、抓取方式、部署方式、数据量和性能数据，这里不补写未经核实的技术细节，也不展示账号、凭据或采集到的个人数据。
''',
    'order_num': '2',
    'status': '1',
}


if __name__ == '__main__':
    precondition = """SELECT IF(
        (SELECT COUNT(*) FROM t_work_experience WHERE id=1 AND is_deleted=0 AND status=1)=1
        AND (SELECT COUNT(*) FROM t_work_experience WHERE id=2 AND is_deleted=0)=1
        AND (SELECT COUNT(*) FROM t_experience_project WHERE id=10 AND experience_id=1 AND project_name='微信数据抓取' AND is_deleted=0)=1
        AND (SELECT COUNT(*) FROM t_experience_project WHERE experience_id=1 AND is_deleted=0 AND id<>10)=0
        AND (SELECT COUNT(*) FROM t_experience_project WHERE experience_id=2 AND project_name='微信数据抓取' AND id IN (1,2,3) AND status=0 AND is_deleted=0)=3,
        1,0)"""
    if mysql(precondition) != '1':
        raise RuntimeError('Source records changed; no content written')
    backup_path = backup()
    sql = f"""
START TRANSACTION;
SELECT id FROM t_work_experience WHERE id IN (1,2) FOR UPDATE;
SELECT id FROM t_experience_project WHERE id IN (1,2,3,10) FOR UPDATE;
SET @ready = ({precondition});
UPDATE t_work_experience SET {assignments(company)}, update_time=NOW()
 WHERE id=1 AND @ready=1;
INSERT INTO t_experience_project
 (experience_id, project_name, summary, role_title, tech_stack, contributions, outcomes, content, order_num, status, create_time, update_time, is_deleted)
 SELECT 1, {quote(hardware['project_name'])}, {quote(hardware['summary'])},
 {quote(hardware['role_title'])}, {quote(hardware['tech_stack'])},
 {quote(hardware['contributions'])}, {quote(hardware['outcomes'])},
 {quote(hardware['content'])}, 1, 1, NOW(), NOW(), 0
 WHERE @ready=1;
UPDATE t_experience_project SET {assignments(wechat)}, update_time=NOW()
 WHERE id=10 AND experience_id=1 AND @ready=1;
UPDATE t_experience_project SET is_deleted=1, update_time=NOW()
 WHERE id IN (1,2,3) AND experience_id=2 AND @ready=1;
SELECT @ready;
COMMIT;
"""
    if mysql(sql).splitlines()[-1] != '1':
        raise RuntimeError('Guard failed; no content written')
    print('Updated Huaan experience 1; published projects; archived old drafts. Backup:', backup_path)
