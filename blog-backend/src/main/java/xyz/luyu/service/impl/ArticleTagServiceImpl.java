package xyz.luyu.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import xyz.luyu.domain.entity.ArticleTag;
import xyz.luyu.mapper.ArticleTagMapper;
import xyz.luyu.service.ArticleTagService;

/**
 * (ArticleTag)表服务实现类
 *
 * @author luyu
 * @since 2023-10-15 02:29:13
 */
@Service("articleTagService")
public class ArticleTagServiceImpl extends ServiceImpl<ArticleTagMapper, ArticleTag> implements ArticleTagService {

}
