package xyz.luyu.service;

import com.baomidou.mybatisplus.spring.service.IService;
import xyz.luyu.domain.dto.SearchTreeHoleDTO;
import xyz.luyu.domain.dto.TreeHoleIsCheckDTO;
import xyz.luyu.domain.entity.TreeHole;
import xyz.luyu.domain.response.ResponseResult;
import xyz.luyu.domain.vo.TreeHoleListVO;
import xyz.luyu.domain.vo.TreeHoleVO;

import java.util.List;


/**
 * (TreeHole)表服务接口
 *
 * @author luyu
 * @since 2023-10-30 11:14:14
 */
public interface TreeHoleService extends IService<TreeHole> {
    /**
     * 新增树洞
     * @param content 树洞内容
     * @return 是否成功
     */
    ResponseResult<Void> addTreeHole(String content);

    /**
     * 查看树洞
     * @return 树洞列表
     */
    List<TreeHoleVO> getTreeHole();

    /**
     * 后台树洞列表
     * @return 结果
     */
    List<TreeHoleListVO> getBackTreeHoleList(SearchTreeHoleDTO searchDTO);

    /**
     * 是否通过树洞
     * @param isCheckDTO 是否通过
     * @return 是否成功
     */
    ResponseResult<Void> isCheckTreeHole(TreeHoleIsCheckDTO isCheckDTO);

    /**
     * 删除树洞
     * @param ids id 列表
     * @return 是否成功
     */
    ResponseResult<Void> deleteTreeHole(List<Long> ids);
}
