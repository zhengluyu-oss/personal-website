package xyz.kuailemao.domain.dto;

import lombok.Data;
import org.hibernate.validator.constraints.Length;
import xyz.kuailemao.domain.BaseData;

import java.util.Date;

/**
 * @author kuailemao
 * <p>
 * 创建时间：2024/1/3 15:05
 */
@Data
public class WebsiteInfoDTO implements BaseData {
    //网站名称
    @Length(max = 30, message = "网站名称字数不能超过30")
    private String websiteName;
    @Length(max = 40, message = "首页眉题字数不能超过40")
    private String heroKicker;
    @Length(max = 60, message = "首页主标题字数不能超过60")
    private String heroTitle;
    @Length(max = 100, message = "首页副标题字数不能超过100")
    private String heroSubtitle;
    @Length(max = 240, message = "首页简介字数不能超过240")
    private String heroDescription;
    @Length(max = 20, message = "主按钮文字不能超过20字")
    private String heroPrimaryText;
    @Length(max = 255, message = "主按钮链接不能超过255字")
    private String heroPrimaryUrl;
    @Length(max = 20, message = "次按钮文字不能超过20字")
    private String heroSecondaryText;
    @Length(max = 255, message = "次按钮链接不能超过255字")
    private String heroSecondaryUrl;
    @Length(max = 30, message = "侧栏标签不能超过30字")
    private String heroAsideLabel;
    @Length(max = 120, message = "侧栏内容不能超过120字")
    private String heroAsideText;
    //头部通知
    @Length(max = 100, message = "头部通知字数不能超过100")
    private String headerNotification;
    //侧面公告
    @Length(max = 1000, message = "侧面公告字数不能超过1000")
    private String sidebarAnnouncement;
    //备案信息
    @Length(max = 100, message = "备案信息字数不能超过100")
    private String recordInfo;
    //开始运行时间
    private Date startTime;
}
