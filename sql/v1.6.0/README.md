## 升级v1.6.0导入[t_photo.sql](t_photo.sql)表
**1、新建相册管理菜单：**
![img_1.png](img_1.png)

*2、*需要给超级管理员5个权限：**
> 1. blog:photo:list(后台相册或照片列表)
> 2. blog:album:create(后台创建相册)
> 3. blog:photo:upload(后台上传照片)
> 4. blog:album:update(后台修改相册)
> 5. blog:photo:delete(后台删除相册或照片)
> ![img.png](img.png)

![img_2.png](img_2.png)
![img_3.png](img_3.png)
![img_4.png](img_4.png)

v1.5.0 是完整库，v1.6.0 是增量。先导入 v1.5.0，再执行本目录升级。