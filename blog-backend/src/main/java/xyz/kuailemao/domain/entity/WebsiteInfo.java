package xyz.kuailemao.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import xyz.kuailemao.domain.BaseData;

import java.util.Date;


/**
 * (WebsiteInfo)表实体类
 *
 * @author kuailemao
 * @since 2023-12-27 14:07:33
 */
@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
@TableName("sys_website_info")
public class WebsiteInfo implements BaseData {
    private Long id;
    //站长头像
    private String webmasterAvatar;
    //站长名称
    private String webmasterName;
    //站长文案
    private String webmasterCopy;
    //站长资料卡背景图
    private String webmasterProfileBackground;
    //gitee链接
    private String giteeLink;
    //github链接
    private String githubLink;
    //网站名称
    private String websiteName;
    //首页眉题
    private String heroKicker;
    //首页主标题
    private String heroTitle;
    //首页副标题
    private String heroSubtitle;
    //首页简介
    private String heroDescription;
    //首页主按钮文字
    private String heroPrimaryText;
    //首页主按钮链接
    private String heroPrimaryUrl;
    //首页次按钮文字
    private String heroSecondaryText;
    //首页次按钮链接
    private String heroSecondaryUrl;
    //首页侧栏标签
    private String heroAsideLabel;
    //首页侧栏内容
    private String heroAsideText;
    //头部通知
    private String headerNotification;
    //侧面公告
    private String sidebarAnnouncement;
    //备案信息
    private String recordInfo;
    //开始运行时间
    private Date startTime;
    //用户创建时间
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    //用户更新时间
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
    //是否删除（0：未删除，1：已删除）
    private Integer isDeleted;
}

