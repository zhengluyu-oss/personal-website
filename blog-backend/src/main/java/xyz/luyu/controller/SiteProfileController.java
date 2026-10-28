package xyz.luyu.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletResponse;
import xyz.luyu.domain.response.ResponseResult;
import xyz.luyu.domain.vo.WebsiteInfoVO;
import xyz.luyu.service.SiteModuleAccessService;
import xyz.luyu.service.WebsiteInfoService;

@RestController
public class SiteProfileController {
    private final SiteModuleAccessService access;
    private final WebsiteInfoService website;
    public SiteProfileController(SiteModuleAccessService access, WebsiteInfoService website) { this.access = access; this.website = website; }
    public record Profile(String name, String avatar, String github, String headline, String tagline, String bio) {}
    @GetMapping("/site-modules/about")
    public ResponseResult<Profile> about(HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        access.require("about");
        WebsiteInfoVO info = website.selectWebsiteInfo();
        return ResponseResult.success(new Profile(info.getWebmasterName(), info.getWebmasterAvatar(), info.getGithubLink(),
                "后端与全栈开发 · AI 应用实践", "从业务系统到 AI 应用，持续把想法做成可用的产品",
                "你好，我是陆屿。我主要使用 Java、Spring Boot 构建后端服务，也使用 Vue、React、Python 和 PHP 开发 Web 与多端应用。项目实践涵盖内容管理、业务流程、数据处理、自动化和 AI 应用；我关注系统的可维护性、实际体验与上线后的稳定运行。这里记录技术笔记、项目复盘和持续学习的过程，欢迎通过 GitHub 了解更多代码。"));
    }
}
