package xyz.kuailemao.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletResponse;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.domain.vo.WebsiteInfoVO;
import xyz.kuailemao.service.SiteModuleAccessService;
import xyz.kuailemao.service.WebsiteInfoService;

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
                "后端 / 全栈方向 · 求职中", "用作品说话：技术笔记 · 项目复盘 · 持续学习",
                "你好，我是郑陆宇。这里记录我在后端与全栈方向的学习与实践，包括项目拆解、问题排查与面试准备相关笔记。欢迎通过 GitHub 了解更多代码与项目。"));
    }
}
