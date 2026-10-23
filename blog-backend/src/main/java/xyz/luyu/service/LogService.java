package xyz.luyu.service;

import com.baomidou.mybatisplus.spring.service.IService;
import xyz.luyu.domain.dto.LogDTO;
import xyz.luyu.domain.dto.LogDeleteDTO;
import xyz.luyu.domain.entity.Log;
import xyz.luyu.domain.response.ResponseResult;
import xyz.luyu.domain.vo.PageVO;


/**
 * (Log)表服务接口
 *
 * @author luyu
 * @since 2023-12-12 09:12:32
 */
public interface LogService extends IService<Log> {
    /**
     * 搜索/显示操作日志
     *
     * @param LogDTO  查询条件
     * @param aLong
     * @param current
     * @return 数据列表
     */
    PageVO searchLog(LogDTO LogDTO, Long aLong, Long current);

    /**
     *  删除/清空操作日志
     * @param logDeleteDTO id集合
     * @return  响应结果
     */
    ResponseResult<Void> deleteLog(LogDeleteDTO logDeleteDTO);

}
