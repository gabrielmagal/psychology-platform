package br.com.mindhaven.review;

import br.com.mindhaven.review.ReviewEntity.CommentStatus;
import core.error.BusinessException;
import core.repository.dao.GenericDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Avaliações com nota de 1 a 5, recomendação e comentário.
 *
 * <p>Módulo reaproveitável: não conhece usuários, sessões nem perfis. Regras que valem para
 * qualquer sistema:
 * <ul>
 *   <li>uma avaliação por autor e contexto — avaliar de novo substitui a anterior;</li>
 *   <li>o autor nunca é exposto nas leituras públicas;</li>
 *   <li>o comentário só é público se o autor autorizou <b>e</b> a moderação aprovou;</li>
 *   <li>a nota entra na média mesmo que o comentário seja recusado, para que a moderação não
 *       sirva para esconder nota baixa.</li>
 * </ul>
 */
@ApplicationScoped
@Transactional
public class ReviewService {
    public static final int MAX_COMMENT = 1000;

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    public ReviewEntity submit(String tenant, UUID authorId, UUID subjectId, String contextType, UUID contextId,
                               int rating, Boolean recommend, String comment, boolean allowPublicComment) {
        if (rating < 1 || rating > 5) throw BusinessException.invalid("A nota vai de 1 a 5.");
        if (subjectId == null || authorId == null || contextId == null) throw BusinessException.invalid("Avaliação incompleta.");
        if (subjectId.equals(authorId)) throw BusinessException.invalid("Não é possível avaliar a si mesmo.");
        String text = comment == null || comment.isBlank() ? null : comment.trim();
        if (text != null && text.length() > MAX_COMMENT) {
            throw BusinessException.invalid("O comentário pode ter até " + MAX_COMMENT + " caracteres.");
        }
        dao.defineSchema(tenant);
        ReviewEntity review = em.createQuery("select r from ReviewEntity r where r.authorId = :a "
                        + "and r.contextType = :t and r.contextId = :c", ReviewEntity.class)
                .setParameter("a", authorId).setParameter("t", contextType).setParameter("c", contextId)
                .getResultStream().findFirst().orElseGet(ReviewEntity::new);
        review.setAuthorId(authorId);
        review.setSubjectId(subjectId);
        review.setContextType(contextType);
        review.setContextId(contextId);
        review.setRating(rating);
        review.setRecommend(recommend);
        review.setComment(text);
        // Texto novo volta para a fila: o que foi aprovado antes era outro comentário.
        review.setCommentStatus(text != null && allowPublicComment ? CommentStatus.PENDING : CommentStatus.PRIVATE);
        review.setCreatedAt(LocalDateTime.now());
        return em.merge(review);
    }

    /** Contextos de um tipo que o autor já avaliou. */
    public Set<UUID> reviewedContexts(String tenant, UUID authorId, String contextType) {
        dao.defineSchema(tenant);
        return new HashSet<>(em.createQuery("select r.contextId from ReviewEntity r where r.authorId = :a "
                        + "and r.contextType = :t", UUID.class)
                .setParameter("a", authorId).setParameter("t", contextType).getResultList());
    }

    /** Resumo de quem foi avaliado: média, total, recomendação e distribuição das notas. */
    public Map<String, Object> summary(String tenant, UUID subjectId) {
        return summaries(tenant, List.of(subjectId)).get(subjectId);
    }

    /** O mesmo resumo para vários avaliados, em uma consulta só. Quem não tem avaliação vem zerado. */
    public Map<UUID, Map<String, Object>> summaries(String tenant, Collection<UUID> subjectIds) {
        Map<UUID, long[]> stars = new HashMap<>();
        Map<UUID, long[]> recommend = new HashMap<>();
        for (UUID id : subjectIds) {
            stars.put(id, new long[5]);
            recommend.put(id, new long[2]);
        }
        if (!subjectIds.isEmpty()) {
            dao.defineSchema(tenant);
            for (Object[] row : em.createQuery("select r.subjectId, r.rating, r.recommend, count(r) from ReviewEntity r "
                            + "where r.subjectId in :ids group by r.subjectId, r.rating, r.recommend", Object[].class)
                    .setParameter("ids", subjectIds).getResultList()) {
                UUID subject = (UUID) row[0];
                int rating = ((Number) row[1]).intValue();
                long count = ((Number) row[3]).longValue();
                if (rating >= 1 && rating <= 5) stars.get(subject)[rating - 1] += count;
                if (row[2] != null) recommend.get(subject)[Boolean.TRUE.equals(row[2]) ? 0 : 1] += count;
            }
        }
        Map<UUID, Map<String, Object>> result = new HashMap<>();
        for (UUID id : subjectIds) {
            long[] s = stars.get(id);
            long total = 0, sum = 0;
            for (int i = 0; i < 5; i++) {
                total += s[i];
                sum += s[i] * (i + 1);
            }
            long yes = recommend.get(id)[0], answered = yes + recommend.get(id)[1];
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("count", total);
            summary.put("average", total == 0 ? null
                    : BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP));
            summary.put("distribution", List.of(s[0], s[1], s[2], s[3], s[4]));
            summary.put("recommendAnswers", answered);
            summary.put("recommendPercent", answered == 0 ? null : Math.round(yes * 100.0 / answered));
            result.put(id, summary);
        }
        return result;
    }

    /** Comentários publicados de alguém: sem autor, só o mês em que foram escritos. */
    public List<Map<String, Object>> publishedComments(String tenant, UUID subjectId, int limit) {
        dao.defineSchema(tenant);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ReviewEntity r : em.createQuery("select r from ReviewEntity r where r.subjectId = :s "
                        + "and r.commentStatus = :status order by r.createdAt desc", ReviewEntity.class)
                .setParameter("s", subjectId).setParameter("status", CommentStatus.PUBLISHED)
                .setMaxResults(Math.max(1, Math.min(limit, 100))).getResultList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rating", r.getRating());
            row.put("recommend", r.getRecommend());
            row.put("comment", r.getComment());
            row.put("month", r.getCreatedAt() == null ? null : r.getCreatedAt().toLocalDate().withDayOfMonth(1).toString());
            rows.add(row);
        }
        return rows;
    }

    /**
     * Tudo o que alguém recebeu, para o próprio avaliado e para a administração. O autor só
     * entra quando {@code withAuthor} é verdadeiro.
     */
    public List<Map<String, Object>> received(String tenant, UUID subjectId, boolean withAuthor, int limit) {
        dao.defineSchema(tenant);
        return rows(em.createQuery("select r from ReviewEntity r where r.subjectId = :s order by r.createdAt desc",
                        ReviewEntity.class)
                .setParameter("s", subjectId).setMaxResults(Math.max(1, Math.min(limit, 200))).getResultList(), withAuthor);
    }

    /** Fila da moderação: comentários que o autor autorizou e ainda não foram avaliados. */
    public List<Map<String, Object>> pendingModeration(String tenant) {
        dao.defineSchema(tenant);
        return rows(em.createQuery("select r from ReviewEntity r where r.commentStatus = :status order by r.createdAt",
                        ReviewEntity.class)
                .setParameter("status", CommentStatus.PENDING).setMaxResults(200).getResultList(), true);
    }

    /** Decisão da moderação. Só vale para comentário que o autor autorizou a publicar. */
    public void moderate(String tenant, UUID reviewId, boolean publish) {
        dao.defineSchema(tenant);
        ReviewEntity review = em.find(ReviewEntity.class, reviewId);
        if (review == null) throw BusinessException.invalid("Avaliação não encontrada.");
        if (review.getCommentStatus() == CommentStatus.PRIVATE) {
            throw BusinessException.conflict("O autor não autorizou a exibição deste comentário.");
        }
        review.setCommentStatus(publish ? CommentStatus.PUBLISHED : CommentStatus.REJECTED);
    }

    private static List<Map<String, Object>> rows(List<ReviewEntity> reviews, boolean withAuthor) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ReviewEntity r : reviews) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("subjectId", r.getSubjectId());
            if (withAuthor) row.put("authorId", r.getAuthorId());
            row.put("rating", r.getRating());
            row.put("recommend", r.getRecommend());
            row.put("comment", r.getComment());
            row.put("commentStatus", r.getCommentStatus().name());
            row.put("createdAt", r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
            rows.add(row);
        }
        return rows;
    }
}
