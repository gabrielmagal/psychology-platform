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
import jakarta.transaction.Transactional;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Transactional
public class GenericDao implements IGenericDao {
    @PersistenceContext
    private EntityManager entityManager;

    @Inject
    TenantSchemaService schemaService;

    @Override
    public <T extends BaseEntity> T findById(String tenant, UUID id, Class<T> clazz) {
        defineSchema(tenant);
        return entityManager.find(clazz, id);
    }

    @Override
    public <T extends BaseEntity> void delete(String tenant, UUID id, Class<T> clazz) {
        defineSchema(tenant);
        T entity = findById(tenant, id, clazz);
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

        int firstResult = page * size;
        return entityManager.createQuery(query)
                .setFirstResult(firstResult)
                .setMaxResults(size)
                .getResultList();
    }

    @Override
    public CriteriaBuilder getCriteriaBuilder(String tenant) {
        defineSchema(tenant);
        return entityManager.getCriteriaBuilder();
    }

    @Override
    public <T extends BaseEntity> List<T> filteredFindPaged(String tenant, Filter filter, int page, int size, Class<T> clazz) {
        defineSchema(tenant);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(clazz);
        Root<T> root = query.from(clazz);
        query.select(root);

        List<Predicate> predicates = new ArrayList<>();

        if (filter.getFilterParams() != null) {
            for (FilterParam param : filter.getFilterParams()) {
                if (param.getField().equalsIgnoreCase("search")) {
                    List<String> stringFields = Arrays.stream(clazz.getDeclaredFields())
                            .filter(f -> f.getType().equals(String.class))
                            .filter(f -> !f.isAnnotationPresent(Lob.class))
                            .map(Field::getName)
                            .toList();

                    List<Predicate> orPredicates = stringFields.stream()
                            .map(f -> cb.like(cb.lower(root.get(f)), "%" + param.getValue().toLowerCase() + "%"))
                            .toList();

                    predicates.add(cb.or(orPredicates.toArray(new Predicate[0])));
                } else if (param.getField().contains(".")) {
                    Path<Object> path = resolveNestedPath(root, param.getField());
                    Class<?> type = path.getJavaType();
                    Object value = convertToType(type, param.getValue());
                    predicates.add(cb.equal(path, value));
                } else {
                    Path<?> path = root.get(param.getField());
                    if (path.getModel() instanceof SingularAttribute) {
                        SingularAttribute<?, ?> attr = (SingularAttribute<?, ?>) path.getModel();
                        if (attr.getType() instanceof EntityType<?>) {
                            path = root.get(param.getField()).get("id");
                            predicates.add(cb.equal(path, UUID.fromString(param.getValue())));
                        }
                        if (attr.getJavaType().equals(UUID.class)) {
                            predicates.add(cb.equal(path, UUID.fromString(param.getValue())));
                        }
                    }
                }
            }
        }

        query.where(cb.and(predicates.toArray(new Predicate[0])));

        return entityManager.createQuery(query)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    public <T extends BaseEntity> long countFiltered(String tenant, Filter filter, Class<T> clazz) {
        defineSchema(tenant);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<T> root = query.from(clazz);

        List<Predicate> predicates = new ArrayList<>();

        if (filter.getFilterParams() != null) {
            for (FilterParam param : filter.getFilterParams()) {
                if (param.getField().equalsIgnoreCase("search")) {
                    List<String> stringFields = Arrays.stream(clazz.getDeclaredFields())
                            .filter(f -> f.getType().equals(String.class))
                            .filter(f -> !f.isAnnotationPresent(Lob.class))
                            .map(Field::getName)
                            .toList();

                    List<Predicate> orPredicates = stringFields.stream()
                            .map(f -> cb.like(cb.lower(root.get(f)), "%" + param.getValue().toLowerCase() + "%"))
                            .toList();

                    predicates.add(cb.or(orPredicates.toArray(new Predicate[0])));
                } else if (param.getField().contains(".")) {
                    Path<Object> path = resolveNestedPath(root, param.getField());
                    Class<?> type = path.getJavaType();
                    Object value = convertToType(type, param.getValue());
                    predicates.add(cb.equal(path, value));
                } else {
                    Path<Object> path = root.get(param.getField());
                    Class<?> type = path.getJavaType();
                    Object value = convertToType(type, param.getValue());
                    predicates.add(cb.equal(path, value));
                }
            }
        }

        query.select(cb.count(root));
        query.where(cb.and(predicates.toArray(new Predicate[0])));

        return entityManager.createQuery(query).getSingleResult();
    }

    public <T extends BaseEntity> List<Predicate> buildPredicatesFromFilter(
            Filter filter,
            CriteriaBuilder cb,
            Root<T> root,
            Class<T> clazz
    ) {
        List<Predicate> predicates = new ArrayList<>();

        if (filter.getFilterParams() != null) {
            for (FilterParam param : filter.getFilterParams()) {
                if (param.getField().equalsIgnoreCase("search")) {
                    List<String> stringFields = Arrays.stream(clazz.getDeclaredFields())
                            .filter(f -> f.getType().equals(String.class))
                            .filter(f -> !f.isAnnotationPresent(Lob.class))
                            .map(Field::getName)
                            .toList();

                    List<Predicate> orPredicates = stringFields.stream()
                            .map(f -> cb.like(cb.lower(root.get(f)), "%" + param.getValue().toLowerCase() + "%"))
                            .toList();

                    predicates.add(cb.or(orPredicates.toArray(new Predicate[0])));
                } else if (param.getField().contains(".")) {
                    Path<Object> path = resolveNestedPath(root, param.getField());
                    predicates.add(cb.equal(path, convertToType(path.getJavaType(), param.getValue())));
                } else {
                    Path<?> path = root.get(param.getField());
                    if (path.getModel() instanceof SingularAttribute<?, ?> attr) {
                        if (attr.getJavaType().equals(UUID.class)) {
                            predicates.add(cb.equal(path, UUID.fromString(param.getValue())));
                        } else {
                            predicates.add(cb.equal(path, convertToType(attr.getJavaType(), param.getValue())));
                        }
                    }
                }
            }
        }

        return predicates;
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

    private Object convertToType(Class<?> type, String value) {
        if (type.equals(String.class)) return value;
        if (type.equals(Long.class)) return Long.valueOf(value);
        if (type.equals(Integer.class)) return Integer.valueOf(value);
        if (type.equals(UUID.class)) return UUID.fromString(value);
        if (type.equals(Boolean.class)) return Boolean.valueOf(value);

        if (BaseEntity.class.isAssignableFrom(type)) {
            return entityManager.getReference(type, UUID.fromString(value));
        }
        throw new IllegalArgumentException("Tipo não suportado: " + type.getName());
    }

    public void defineSchema(String schema) {
        schemaService.initSchema(schema);
        entityManager.createNativeQuery("SET search_path TO " + schema).executeUpdate();
    }
}
