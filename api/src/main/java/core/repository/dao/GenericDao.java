package core.repository.dao;

import core.service.interfaces.TenantSchemaService;
import core.service.model.FilterParam;
import core.repository.dao.interfaces.IGenericDao;
import core.repository.model.BaseEntity;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@Transactional
public class GenericDao implements IGenericDao {

    /** Chave usada para lembrar qual schema já foi aplicado à conexão da transação corrente. */
    private static final String SCHEMA_TX_KEY = GenericDao.class.getName() + ".search_path";

    private static final String SEARCH_FIELD = "search";

    @PersistenceContext
    private EntityManager entityManager;

    @Inject
    TenantSchemaService schemaService;

    @Inject
    TransactionSynchronizationRegistry txRegistry;

    /** Campos String pesquisáveis por entidade — evita reflexão a cada query. */
    private final Map<Class<?>, List<String>> searchableFieldsCache = new ConcurrentHashMap<>();

    @Override
    public <T extends BaseEntity> T findById(String tenant, UUID id, Class<T> clazz) {
        defineSchema(tenant);
        return entityManager.find(clazz, id);
    }

    @Override
    public <T extends BaseEntity> void delete(String tenant, UUID id, Class<T> clazz) {
        defineSchema(tenant);
        T entity = entityManager.find(clazz, id);
        if (entity != null) {
            entityManager.remove(entity);
        }
    }

    @Override
    public <T extends BaseEntity> T update(String tenant, T entity) {
        defineSchema(tenant);
        return entityManager.merge(entity);
    }

    @Override
    public <T extends BaseEntity> long count(String tenant, Class<T> clazz) {
        defineSchema(tenant);
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<T> root = query.from(clazz);
        query.select(builder.count(root));
        return entityManager.createQuery(query).getSingleResult();
    }

    @Override
    public <T extends BaseEntity> List<T> listAll(String tenant, Class<T> clazz) {
        defineSchema(tenant);
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = builder.createQuery(clazz);
        Root<T> root = query.from(clazz);
        query.select(root);
        return entityManager.createQuery(query).getResultList();
    }

    @Override
    public <T extends BaseEntity> List<T> findAllPaged(String tenant, int page, int size, Class<T> clazz) {
        defineSchema(tenant);
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = builder.createQuery(clazz);
        Root<T> root = query.from(clazz);
        query.select(root);

        return entityManager.createQuery(query)
                .setFirstResult(firstResult(page, size))
                .setMaxResults(pageSize(size))
                .getResultList();
    }

    @Override
    public CriteriaBuilder getCriteriaBuilder(String tenant) {
        defineSchema(tenant);
        return entityManager.getCriteriaBuilder();
    }

    @Override
    public <T extends BaseEntity> List<T> filteredFindPaged(String tenant, Filter filter, int page, int size, Class<T> clazz) {
        return filteredFindPaged(tenant, filter, page, size, clazz, null);
    }

    @Override
    public <T extends BaseEntity> List<T> filteredFindPaged(String tenant, Filter filter, int page, int size,
                                                            Class<T> clazz, PredicateProvider<T> extraPredicates) {
        defineSchema(tenant);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(clazz);
        Root<T> root = query.from(clazz);
        query.select(root);

        List<Predicate> predicates = buildPredicatesFromFilter(filter, cb, root, clazz);
        if (extraPredicates != null) {
            predicates.addAll(extraPredicates.apply(cb, root, query));
        }
        if (!predicates.isEmpty()) {
            query.where(cb.and(predicates.toArray(new Predicate[0])));
        }

        return entityManager.createQuery(query)
                .setFirstResult(firstResult(page, size))
                .setMaxResults(pageSize(size))
                .getResultList();
    }

    @Override
    public <T extends BaseEntity> long countFiltered(String tenant, Filter filter, Class<T> clazz) {
        return countFiltered(tenant, filter, clazz, null);
    }

    @Override
    public <T extends BaseEntity> long countFiltered(String tenant, Filter filter, Class<T> clazz,
                                                     PredicateProvider<T> extraPredicates) {
        defineSchema(tenant);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<T> root = query.from(clazz);
        query.select(cb.count(root));

        List<Predicate> predicates = buildPredicatesFromFilter(filter, cb, root, clazz);
        if (extraPredicates != null) {
            predicates.addAll(extraPredicates.apply(cb, root, query));
        }
        if (!predicates.isEmpty()) {
            query.where(cb.and(predicates.toArray(new Predicate[0])));
        }

        return entityManager.createQuery(query).getSingleResult();
    }

    /**
     * Fonte única de verdade para tradução de {@link Filter} em predicados.
     *
     * <p>Antes existiam três cópias divergentes desta lógica (listagem, contagem e este
     * método), o que produzia dois defeitos observáveis: a listagem <em>ignorava
     * silenciosamente</em> filtros por campos simples (String, enum, data) e a contagem
     * <em>estourava</em> {@code IllegalArgumentException} nesses mesmos campos. Com uma
     * implementação só, listagem e contagem passam a concordar entre si.
     */
    public <T extends BaseEntity> List<Predicate> buildPredicatesFromFilter(
            Filter filter,
            CriteriaBuilder cb,
            Root<T> root,
            Class<T> clazz
    ) {
        List<Predicate> predicates = new ArrayList<>();
        if (filter == null || filter.getFilterParams() == null) {
            return predicates;
        }

        for (FilterParam param : filter.getFilterParams()) {
            String field = param.getField();
            String value = param.getValue();

            if (field == null || value == null || value.isBlank()) continue;

            if (SEARCH_FIELD.equalsIgnoreCase(field)) {
                Predicate search = buildSearchPredicate(cb, root, clazz, value);
                if (search != null) predicates.add(search);
                continue;
            }

            if (field.contains(".")) {
                Path<Object> path = resolveNestedPath(root, field);
                predicates.add(cb.equal(path, convertToType(path.getJavaType(), value)));
                continue;
            }

            Path<?> path;
            try {
                path = root.get(field);
            } catch (IllegalArgumentException e) {
                // Query param que não corresponde a um atributo da entidade: ignora em vez de quebrar.
                continue;
            }

            if (path.getModel() instanceof SingularAttribute<?, ?> attr && attr.getType() instanceof EntityType<?>) {
                // Filtro por associação: compara pela chave primária.
                predicates.add(cb.equal(root.get(field).get("id"), UUID.fromString(value)));
                continue;
            }

            predicates.add(cb.equal(path, convertToType(path.getJavaType(), value)));
        }

        return predicates;
    }

    private <T> Predicate buildSearchPredicate(CriteriaBuilder cb, Root<T> root, Class<T> clazz, String value) {
        List<String> stringFields = searchableFieldsCache.computeIfAbsent(clazz, this::searchableFieldsOf);

        if (stringFields.isEmpty()) return null;

        String pattern = "%" + value.toLowerCase() + "%";
        List<Predicate> orPredicates = stringFields.stream()
                .map(f -> cb.like(cb.lower(root.get(f)), pattern))
                .toList();

        return cb.or(orPredicates.toArray(new Predicate[0]));
    }

    /**
     * Campos String em que a busca livre ({@code ?search=...}) pode incidir.
     *
     * <p>A lista era montada com {@code clazz.getDeclaredFields()}, que devolve também os
     * campos {@code @Transient}. Em {@code UserEntity} existe um deles — {@code profileImage},
     * a foto em base64 —, e como ele não é um atributo persistente, montar um {@code LIKE}
     * sobre ele fazia o Hibernate rejeitar a consulta inteira
     * ({@code Could not resolve attribute 'profileImage'}). Na prática, toda busca por
     * usuário estourava 500 — inclusive o autocomplete de paciente, que consulta a cada
     * digitação. Consultando o metamodelo do JPA, só entram atributos que existem de fato
     * no mapeamento.
     */
    private List<String> searchableFieldsOf(Class<?> clazz) {
        return entityManager.getMetamodel().entity(clazz).getSingularAttributes().stream()
                .filter(attr -> attr.getJavaType().equals(String.class))
                .map(SingularAttribute::getName)
                .filter(name -> !isLob(clazz, name))
                .sorted()
                .toList();
    }

    /** Colunas @Lob ficam de fora: LIKE sobre texto longo não é filtro, é varredura. */
    private boolean isLob(Class<?> clazz, String fieldName) {
        for (Class<?> type = clazz; type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                return type.getDeclaredField(fieldName).isAnnotationPresent(Lob.class);
            } catch (NoSuchFieldException ignored) {
                // Campo declarado na superclasse: continua subindo a hierarquia.
            }
        }
        return false;
    }

    private Path<Object> resolveNestedPath(Root<?> root, String path) {
        String[] parts = path.split("\\.");
        if ("parent".equals(parts[0])) {
            Class<?> entityClass = root.getJavaType();
            Field parentField = Arrays.stream(entityClass.getDeclaredFields())
                    .filter(f -> f.isAnnotationPresent(ManyToOne.class) || f.isAnnotationPresent(OneToOne.class))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Campo 'parent' não pôde ser resolvido em " + entityClass.getSimpleName()));
            parts[0] = parentField.getName();
        }
        Path<?> result = root;
        for (String part : parts) {
            result = result.get(part);
        }
        return (Path<Object>) result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object convertToType(Class<?> type, String value) {
        if (type.equals(String.class)) return value;
        if (type.equals(Long.class) || type.equals(long.class)) return Long.valueOf(value);
        if (type.equals(Integer.class) || type.equals(int.class)) return Integer.valueOf(value);
        if (type.equals(Double.class) || type.equals(double.class)) return Double.valueOf(value);
        if (type.equals(BigDecimal.class)) return new BigDecimal(value);
        if (type.equals(UUID.class)) return UUID.fromString(value);
        if (type.equals(Boolean.class) || type.equals(boolean.class)) return Boolean.valueOf(value);
        if (type.equals(LocalDate.class)) return LocalDate.parse(value);
        if (type.equals(LocalDateTime.class)) return LocalDateTime.parse(value);
        if (type.isEnum()) return Enum.valueOf((Class<Enum>) type, value);

        if (BaseEntity.class.isAssignableFrom(type)) {
            return entityManager.getReference(type, UUID.fromString(value));
        }
        throw new IllegalArgumentException("Tipo não suportado: " + type.getName());
    }

    /**
     * Posiciona a conexão da transação corrente no schema do tenant.
     *
     * <p>Duas correções importantes aqui:
     *
     * <ol>
     *   <li><b>Injeção de SQL:</b> o valor vinha do header HTTP {@code Tenant} e era
     *       concatenado direto no comando. Identificadores não podem ser parametrizados em
     *       JDBC, então validamos o nome antes de usá-lo.</li>
     *   <li><b>Round-trip desnecessário:</b> {@code SET search_path} era disparado em toda
     *       chamada de DAO. Como uma requisição normal faz várias (buscar usuário logado,
     *       listar, contar…), eram vários round-trips extras por requisição. A conexão
     *       está ligada à transação, então basta aplicar uma vez por transação — o registro
     *       da transação nos diz se já fizemos isso.</li>
     * </ol>
     */
    public void defineSchema(String schema) {
        String tenant = schemaService.initSchema(schema);

        if (isTransactionActive()) {
            if (tenant.equals(txRegistry.getResource(SCHEMA_TX_KEY))) {
                return;
            }
            entityManager.createNativeQuery("SET search_path TO " + tenant).executeUpdate();
            txRegistry.putResource(SCHEMA_TX_KEY, tenant);
            return;
        }

        entityManager.createNativeQuery("SET search_path TO " + tenant).executeUpdate();
    }

    private boolean isTransactionActive() {
        try {
            return txRegistry.getTransactionStatus() == Status.STATUS_ACTIVE;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static int pageSize(int size) {
        return size <= 0 ? 10 : Math.min(size, 500);
    }

    private static int firstResult(int page, int size) {
        return Math.max(page, 0) * pageSize(size);
    }
}
