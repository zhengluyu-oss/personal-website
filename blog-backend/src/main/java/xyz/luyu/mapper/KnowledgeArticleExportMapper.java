package xyz.luyu.mapper;

import org.apache.ibatis.annotations.*;

public interface KnowledgeArticleExportMapper {
    record Mapping(long ownerId, String exportId, String requestHash, long articleId) {}

    @ConstructorArgs({@Arg(column="owner_id",javaType=long.class),@Arg(column="export_id",javaType=String.class),
            @Arg(column="request_hash",javaType=String.class),@Arg(column="article_id",javaType=long.class)})
    @Select("SELECT owner_id,export_id,request_hash,article_id FROM t_knowledge_article_export WHERE owner_id=#{owner} AND export_id=#{exportId} FOR UPDATE")
    Mapping find(@Param("owner") long owner, @Param("exportId") String exportId);

    @Insert("INSERT INTO t_knowledge_article_export(owner_id,export_id,request_hash,article_id) VALUES(#{owner},#{exportId},#{digest},#{articleId})")
    int insert(@Param("owner") long owner, @Param("exportId") String exportId,
            @Param("digest") String digest, @Param("articleId") long articleId);
}
