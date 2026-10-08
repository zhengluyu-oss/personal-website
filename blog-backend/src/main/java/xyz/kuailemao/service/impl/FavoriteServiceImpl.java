package xyz.kuailemao.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.kuailemao.constants.RedisConst;
import xyz.kuailemao.domain.dto.FavoriteIsCheckDTO;
import xyz.kuailemao.domain.dto.SearchFavoriteDTO;
import xyz.kuailemao.domain.entity.Favorite;
import xyz.kuailemao.domain.entity.User;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.domain.vo.FavoriteListVO;
import xyz.kuailemao.mapper.ArticleMapper;
import xyz.kuailemao.mapper.FavoriteMapper;
import xyz.kuailemao.mapper.LeaveWordMapper;
import xyz.kuailemao.mapper.UserMapper;
import xyz.kuailemao.service.FavoriteService;
import xyz.kuailemao.service.ContentVisibilityService;
import xyz.kuailemao.utils.RedisCache;
import xyz.kuailemao.utils.SecurityUtils;
import xyz.kuailemao.utils.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * (Favorite)表服务实现类
 *
 * @author kuailemao
 * @since 2023-10-18 14:12:25
 */
@Service("favoriteService")
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements FavoriteService {

    @Resource
    private FavoriteMapper favoriteMapper;

    @Resource
    private RedisCache redisCache;

    @Resource
    private UserMapper userMapper;

    @Resource
    private ArticleMapper articleMapper;

    @Resource
    private LeaveWordMapper leaveWordMapper;

    @Resource
    private ContentVisibilityService contentVisibilityService;

    @Override
    public ResponseResult<Void> userFavorite(Integer type, Long typeId) {
        if (!SecurityUtils.isLogin() || !contentVisibilityService.isPublic(type, typeId)) {
            return ResponseResult.failure("收藏目标不可用");
        }
        // 查询是否已经收藏
        Favorite favorite = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, SecurityUtils.getUserId())
                .eq(Favorite::getType, type)
                .eq(Favorite::getTypeId, typeId));
        if (StringUtils.isNotNull(favorite)) return ResponseResult.failure();
        Favorite Savefavorite = Favorite.builder()
                .id(null)
                .userId(SecurityUtils.getUserId())
                .type(type)
                .typeId(typeId).build();
        if (this.save(Savefavorite)) {
            if (Integer.valueOf(1).equals(type)) {
                redisCache.incrementCacheMapValue(RedisConst.ARTICLE_FAVORITE_COUNT, typeId.toString(), 1);
            }
            return ResponseResult.success();
        }
        return ResponseResult.failure();
    }

    @Override
    public ResponseResult<Void> cancelFavorite(Integer type, Integer typeId) {
        // 查询是否已经收藏
        Favorite isFavorite = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, SecurityUtils.getUserId())
                .eq(Favorite::getType, type)
                .eq(Favorite::getTypeId, typeId));
        if (Objects.isNull(isFavorite)) return ResponseResult.failure();
        boolean cancelFavorite = this.remove(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, SecurityUtils.getUserId())
                .eq(Favorite::getType, type)
                .eq(Favorite::getTypeId, typeId));
        redisCache.incrementCacheMapValue(RedisConst.ARTICLE_FAVORITE_COUNT, typeId.toString(), -1);
        if (cancelFavorite) return ResponseResult.success();
        return ResponseResult.failure();
    }

    @Override
    public Boolean isFavorite(Integer type, Integer typeId) {
        if (SecurityUtils.isLogin()) {
            // 是否收藏
            Favorite favorite = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                    .eq(Favorite::getUserId, SecurityUtils.getUserId())
                    .eq(Favorite::getType, type)
                    .eq(Favorite::getTypeId, typeId));
            return favorite != null;
        }
        return false;
    }

    @Override
    public List<FavoriteListVO> getBackFavoriteList(SearchFavoriteDTO searchDTO) {
        LambdaQueryWrapper<Favorite> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotNull(searchDTO)) {
            // 搜索
            List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().like(User::getUsername, searchDTO.getUserName()));
            if (!users.isEmpty())
                wrapper.in(StringUtils.isNotEmpty(searchDTO.getUserName()), Favorite::getUserId, users.stream().map(User::getId).collect(Collectors.toList()));
            else
                wrapper.eq(StringUtils.isNotNull(searchDTO.getUserName()), Favorite::getUserId, null);

            wrapper.eq(StringUtils.isNotNull(searchDTO.getIsCheck()), Favorite::getIsCheck, searchDTO.getIsCheck())
                    .eq(StringUtils.isNotNull(searchDTO.getType()), Favorite::getType, searchDTO.getType());
            if (StringUtils.isNotNull(searchDTO.getStartTime()) && StringUtils.isNotNull(searchDTO.getEndTime()))
                wrapper.between(Favorite::getCreateTime, searchDTO.getStartTime(), searchDTO.getEndTime());
        }
        List<Favorite> favorites = favoriteMapper.selectList(wrapper);
        if (!favorites.isEmpty()) {
            return favorites.stream().map(favorite -> favorite.asViewObject(FavoriteListVO.class,
                    v -> {
                        User user = userMapper.selectById(favorite.getUserId());
                        v.setUserName(user == null ? "用户已不可用" : user.getUsername());
                        String content = contentVisibilityService.publicContent(favorite.getType(), favorite.getTypeId());
                        v.setContent(content == null ? "收藏内容已不可用" : content);
                        if (content == null) v.setIsCheck(0);
                    })).toList();
        }
        return List.of();
    }

    @Override
    public ResponseResult<Void> isCheckFavorite(FavoriteIsCheckDTO isCheckDTO) {
        if (favoriteMapper.updateById(Favorite.builder().id(isCheckDTO.getId()).isCheck(isCheckDTO.getIsCheck()).build()) > 0)
            return ResponseResult.success();
        return ResponseResult.failure();
    }

    @Override
    public ResponseResult<Void> deleteFavorite(List<Long> ids) {
        if (favoriteMapper.deleteBatchIds(ids) > 0) return ResponseResult.success();
        return ResponseResult.failure();
    }
}
