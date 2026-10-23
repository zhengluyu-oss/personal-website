package xyz.luyu.controller;


import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import org.hibernate.validator.constraints.Length;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import xyz.luyu.annotation.AccessLimit;
import xyz.luyu.annotation.LogAnnotation;
import xyz.luyu.constants.LogConst;
import xyz.luyu.domain.dto.DeletePhotoOrAlbumDTO;
import xyz.luyu.domain.dto.PhotoAlbumDTO;
import xyz.luyu.domain.response.ResponseResult;
import xyz.luyu.domain.vo.PageVO;
import xyz.luyu.domain.vo.PhotoAndAlbumListVO;
import xyz.luyu.service.PhotoService;
import xyz.luyu.utils.ControllerUtils;

import java.util.List;


/**
 * (Photo)表控制层
 *
 * @author luyu
 * @since 2025-01-16 16:33:05
 */
@Validated
@RestController
@RequestMapping("photo")
public class PhotoController {
    /**
     * 服务对象
     */
    @Resource
    private PhotoService photoService;

    @PreAuthorize("hasAnyAuthority('blog:photo:list')")
    @Operation(summary = "后台相册或照片列表")
    @AccessLimit(seconds = 60, maxCount = 30)
    @LogAnnotation(module = "相册管理", operation = LogConst.GET)
    @GetMapping("/back/list")
    public ResponseResult<PageVO<List<PhotoAndAlbumListVO>>> backList(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Long pageSize,
            @RequestParam(value = "parentId", required = false) Long parentId
    ) {
        return ControllerUtils.messageHandler(() -> photoService.getBackPhotoList(pageNum, pageSize, parentId));
    }

    @Operation(summary = "前台相册或照片列表")
    @AccessLimit(seconds = 60, maxCount = 30)
    @LogAnnotation(module = "相册管理", operation = LogConst.GET)
    @GetMapping("/list")
    public ResponseResult<PageVO<List<PhotoAndAlbumListVO>>> getList(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "16") Long pageSize,
            @RequestParam(value = "parentId", required = false) Long parentId
    ) {
        return ControllerUtils.messageHandler(() -> photoService.getBackPhotoList(pageNum, pageSize, parentId));
    }

    @PreAuthorize("hasAnyAuthority('blog:album:create')")
    @Operation(summary = "后台创建相册")
    @AccessLimit(seconds = 60, maxCount = 30)
    @LogAnnotation(module = "相册管理", operation = LogConst.INSERT)
    @PostMapping("/album/create")
    public ResponseResult<Void> createAlbum(@RequestBody @Validated PhotoAlbumDTO albumDTO) {
        return photoService.createAlbum(albumDTO);
    }

    @PreAuthorize("hasAnyAuthority('blog:photo:upload')")
    @Operation(summary = "后台上传照片")
    @AccessLimit(seconds = 60, maxCount = 30)
    @LogAnnotation(module = "相册管理", operation = LogConst.UPLOAD_IMAGE)
    @PostMapping("/upload")
    public ResponseResult<Void> uploadPhoto(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") @Length(min = 1, max = 20, message = "照片名称长度为1-20个字符") String name,
            @RequestParam(value = "parentId", required = false) Long parentId) {
        return photoService.uploadPhoto(file, name, parentId);
    }

    @PreAuthorize("hasAnyAuthority('blog:album:update')")
    @Operation(summary = "后台修改相册")
    @AccessLimit(seconds = 60, maxCount = 30)
    @LogAnnotation(module = "相册管理", operation = LogConst.UPDATE)
    @PostMapping("/album/update")
    public ResponseResult<Void> updateAlbum(@RequestBody @Validated PhotoAlbumDTO albumDTO) {
        return photoService.updateAlbum(albumDTO);
    }

    @PreAuthorize("hasAnyAuthority('blog:photo:delete')")
    @Operation(summary = "后台删除相册或照片")
    @AccessLimit(seconds = 60, maxCount = 30)
    @LogAnnotation(module = "相册管理", operation = LogConst.UPDATE)
    @DeleteMapping("/delete")
    public ResponseResult<Void> deletePhotoOrAlbum(@RequestBody @Validated DeletePhotoOrAlbumDTO deletePhotoOrAlbum) {
        return photoService.deletePhotoOrAlbum(deletePhotoOrAlbum);
    }

}

