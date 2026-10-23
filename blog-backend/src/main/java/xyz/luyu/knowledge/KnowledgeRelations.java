package xyz.luyu.knowledge;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import xyz.luyu.domain.entity.Article;
import xyz.luyu.mapper.ArticleMapper;
import xyz.luyu.service.SiteModuleAccessService;
import xyz.luyu.utils.SecurityUtils;

import java.util.*;
import static xyz.luyu.knowledge.KnowledgeModels.*;

/** Manual links are stored in PostgreSQL; article visibility is resolved live in MySQL. */
@Service
@ConditionalOnProperty(name = "knowledge.enabled", havingValue = "true")
public final class KnowledgeRelations {
    private final KnowledgeStore store;
    private final ArticleMapper articles;
    private final SiteModuleAccessService modules;
    private final KnowledgeAccess access;

    public KnowledgeRelations(KnowledgeStore store, ArticleMapper articles, SiteModuleAccessService modules, KnowledgeAccess access) {
        this.store = store;
        this.articles = articles;
        this.modules = modules;
        this.access = access;
    }

    private Endpoint normalize(Endpoint endpoint) {
        if (endpoint == null) throw KnowledgeFailure.invalid("关系端点不能为空");
        endpoint.validate();
        return endpoint.kind().equals("ENTRY")
                ? new Endpoint("ENTRY", UUID.fromString(endpoint.id()).toString())
                : new Endpoint("ARTICLE", Long.toString(Long.parseLong(endpoint.id())));
    }

    private boolean visible(long owner, Endpoint endpoint, boolean manage) {
        if (endpoint.kind().equals("ENTRY")) {
            return !store.jdbc.query("SELECT 1 FROM knowledge.entry WHERE owner_id=:owner AND id=:id AND deleted_at IS NULL",
                    KnowledgeStore.params(owner, UUID.fromString(endpoint.id())), (r, n) -> 1).isEmpty();
        }
        if (!modules.canView("blog")) return false;
        Article article = articles.selectById(Long.parseLong(endpoint.id()));
        if (article == null || !Integer.valueOf(0).equals(article.getIsDeleted())) return false;
        if (Integer.valueOf(1).equals(article.getStatus())) return true;
        return manage && Objects.equals(article.getUserId(), owner)
                && SecurityUtils.getUserRoles().contains("blog:publish:article")
                && Set.of(2, 3).contains(article.getStatus());
    }

    private void requireVisible(long owner, Endpoint endpoint) {
        if (!visible(owner, endpoint, true)) throw KnowledgeFailure.invalid("关系端点不存在或不可访问");
    }

    private Relation map(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new Relation(r.getObject("id", UUID.class), r.getLong("revision"),
                new Endpoint(r.getString("from_kind"), r.getString("from_id")),
                new Endpoint(r.getString("to_kind"), r.getString("to_id")),
                RelationType.valueOf(r.getString("type")), r.getString("note"));
    }

    public Relation save(long owner, RelationWrite write) {
        if (write == null || write.id() == null || write.expectedRevision() < 0 || write.type() == null
                || write.note() == null || write.note().codePointCount(0, write.note().length()) > 1000
                || write.note().indexOf('\0') >= 0) throw KnowledgeFailure.invalid("关系内容或版本无效");
        Endpoint from = normalize(write.from()), to = normalize(write.to());
        if (from.key().equals(to.key())) throw KnowledgeFailure.invalid("不能关联条目自身");
        if (write.type() == RelationType.RELATED_TO && from.key().compareTo(to.key()) > 0) {
            Endpoint swap = from; from = to; to = swap;
        }
        final Endpoint left = from, right = to;
        return store.tx(owner, () -> {
            store.lockOwner(owner);
            requireVisible(owner, left); requireVisible(owner, right);
            var p = KnowledgeStore.params(owner, write.id()).addValue("fk", left.kind()).addValue("fid", left.id())
                    .addValue("tk", right.kind()).addValue("tid", right.id()).addValue("type", write.type().name())
                    .addValue("note", write.note().strip()).addValue("revision", write.expectedRevision());
            Long duplicate = store.jdbc.queryForObject("SELECT count(*) FROM knowledge.relation WHERE owner_id=:owner AND from_kind=:fk AND from_id=:fid AND to_kind=:tk AND to_id=:tid AND type=:type AND deleted_at IS NULL AND id<>:id", p, Long.class);
            if (duplicate != null && duplicate > 0) throw KnowledgeFailure.conflict();
            if (write.expectedRevision() == 0) {
                if (store.jdbc.update("INSERT INTO knowledge.relation(owner_id,id,from_kind,from_id,to_kind,to_id,type,note,revision) VALUES(:owner,:id,:fk,:fid,:tk,:tid,:type,:note,1) ON CONFLICT DO NOTHING", p) != 1) throw KnowledgeFailure.conflict();
            } else {
                if (store.jdbc.update("UPDATE knowledge.relation SET from_kind=:fk,from_id=:fid,to_kind=:tk,to_id=:tid,type=:type,note=:note,revision=revision+1 WHERE owner_id=:owner AND id=:id AND revision=:revision AND deleted_at IS NULL", p) != 1) throw KnowledgeFailure.conflict();
            }
            return new Relation(write.id(), write.expectedRevision() + 1, left, right, write.type(), write.note().strip());
        });
    }

    public void delete(long owner, UUID id, long expectedRevision) {
        if (id == null || expectedRevision < 1) throw KnowledgeFailure.invalid("关系版本无效");
        store.tx(owner, () -> {
            store.lockOwner(owner);
            if (store.jdbc.update("UPDATE knowledge.relation SET deleted_at=now(),revision=revision+1 WHERE owner_id=:owner AND id=:id AND revision=:revision AND deleted_at IS NULL",
                    KnowledgeStore.params(owner, id).addValue("revision", expectedRevision)) != 1) throw KnowledgeFailure.conflict();
            return null;
        });
    }

    public Page<RelationNeighbor> neighbors(long owner, Endpoint center, int page, int size) {
        Endpoint key = normalize(center);
        if (page < 1 || page > 10000 || size < 1 || size > 50) throw KnowledgeFailure.invalid("关系页码或条数无效");
        return store.tx(owner, () -> {
            if (!visible(owner, key, access.canManage())) throw new KnowledgeFailure(404, "关系端点不存在或不可访问");
            var p = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("owner", owner)
                    .addValue("kind", key.kind()).addValue("id", key.id());
            // Filter before computing total; MySQL article state is checked on every request.
            boolean manage = access.canManage();
            Map<String, GraphNode> cache = new HashMap<>();
            List<RelationNeighbor> records = new ArrayList<>();
            long total = 0;
            UUID cursor = new UUID(0, 0);
            while (true) {
                p.addValue("cursor", cursor);
                List<Relation> batch = store.jdbc.query("SELECT * FROM knowledge.relation WHERE owner_id=:owner AND deleted_at IS NULL AND id>:cursor AND ((from_kind=:kind AND from_id=:id) OR (to_kind=:kind AND to_id=:id)) ORDER BY id LIMIT 50", p, this::map);
                if (batch.isEmpty()) break;
                for (Relation relation : batch) {
                    cursor = relation.id();
                    Endpoint other = relation.from().key().equals(key.key()) ? relation.to() : relation.from();
                    GraphNode neighbor = cache.get(other.key());
                    if (neighbor == null) {
                        neighbor = node(owner, other, manage);
                        if (neighbor != null) cache.put(other.key(), neighbor);
                    }
                    if (neighbor == null) continue;
                    if (total >= (long)(page - 1) * size && records.size() < size) records.add(new RelationNeighbor(relation, neighbor));
                    total++;
                }
                if (batch.size() < 50) break;
            }
            return new Page<>(records, total, page, size);
        });
    }

    private GraphNode node(long owner, Endpoint endpoint, boolean manage) {
        if (endpoint.kind().equals("ENTRY")) {
            return store.jdbc.query("SELECT title,kind FROM knowledge.entry WHERE owner_id=:owner AND id=:id AND deleted_at IS NULL",
                    KnowledgeStore.params(owner, UUID.fromString(endpoint.id())),
                    (r, n) -> new GraphNode(endpoint, r.getString("title"), r.getString("kind"), "/knowledge?entry=" + endpoint.id()))
                    .stream().findFirst().orElse(null);
        }
        if (!modules.canView("blog")) return null;
        Article article = articles.selectById(Long.parseLong(endpoint.id()));
        if (article == null || !Integer.valueOf(0).equals(article.getIsDeleted())) return null;
        boolean publicArticle = Integer.valueOf(1).equals(article.getStatus());
        boolean ownDraft = manage && Objects.equals(article.getUserId(), owner)
                && SecurityUtils.getUserRoles().contains("blog:publish:article")
                && (Integer.valueOf(2).equals(article.getStatus()) || Integer.valueOf(3).equals(article.getStatus()));
        if (!publicArticle && !ownDraft) return null;
        return new GraphNode(endpoint, article.getArticleTitle(), "ARTICLE",
                publicArticle ? "/blog/articles/" + endpoint.id() : "/admin/articles/" + endpoint.id());
    }

    /** Each frontier is scanned in bounded SQL batches; hidden endpoints never enter the returned graph. */
    public GraphView graph(long owner, Endpoint center, int depth, int maxNodes, int maxEdges) {
        Endpoint root = normalize(center);
        if (depth < 1 || depth > 2 || maxNodes < 1 || maxNodes > 100 || maxEdges < 1 || maxEdges > 200)
            throw KnowledgeFailure.invalid("关系图范围超出限制");
        return store.tx(owner, () -> {
            boolean manage = access.canManage();
            GraphNode first = node(owner, root, manage);
            if (first == null) throw new KnowledgeFailure(404, "关系端点不存在或不可访问");
            Map<String, GraphNode> nodes = new LinkedHashMap<>();
            Map<UUID, Relation> edges = new LinkedHashMap<>();
            nodes.put(root.key(), first);
            List<Endpoint> frontier = List.of(root);
            boolean truncated = false;
            for (int hop = 0; hop < depth && !frontier.isEmpty() && !truncated; hop++) {
                List<Endpoint> next = new ArrayList<>();
                for (Endpoint current : frontier) {
                    UUID cursor = new UUID(0, 0);
                    while (true) {
                        var p = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("owner", owner)
                                .addValue("kind", current.kind()).addValue("endpoint", current.id()).addValue("cursor", cursor);
                        List<Relation> batch = store.jdbc.query("SELECT * FROM knowledge.relation WHERE owner_id=:owner AND deleted_at IS NULL AND id>:cursor AND ((from_kind=:kind AND from_id=:endpoint) OR (to_kind=:kind AND to_id=:endpoint)) ORDER BY id LIMIT 50", p, this::map);
                        if (batch.isEmpty()) break;
                        for (Relation relation : batch) {
                            cursor = relation.id();
                            if (edges.containsKey(relation.id())) continue;
                            Endpoint other = relation.from().key().equals(current.key()) ? relation.to() : relation.from();
                            GraphNode neighbor = nodes.get(other.key());
                            if (neighbor == null) neighbor = node(owner, other, manage);
                            if (neighbor == null) continue;
                            if (edges.size() >= maxEdges || (!nodes.containsKey(other.key()) && nodes.size() >= maxNodes)) {
                                truncated = true;
                                break;
                            }
                            edges.put(relation.id(), relation);
                            if (!nodes.containsKey(other.key())) {
                                nodes.put(other.key(), neighbor);
                                next.add(other);
                            }
                        }
                        if (truncated || batch.size() < 50) break;
                    }
                    if (truncated) break;
                }
                frontier = next;
            }
            return new GraphView(List.copyOf(nodes.values()), List.copyOf(edges.values()), truncated, truncated);
        });
    }
}
