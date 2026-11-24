package br.com.mindhaven.infrastructure.persistence;

import br.com.mindhaven.domain.entity.AnnotationEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.UserType;
import br.com.mindhaven.domain.repository.AnnotationRepository;
import core.repository.dao.GenericDao;
import core.repository.dao.interfaces.IGenericDao;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.criteria.Predicate;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AnnotationRepositoryImpl implements AnnotationRepository {

    @Inject
    GenericDao dao;

    @Override
    public AnnotationEntity save(String tenant, AnnotationEntity entity) {
        return dao.update(tenant, entity);
    }

    @Override
    public AnnotationEntity update(String tenant, AnnotationEntity entity) {
        return dao.update(tenant, entity);
    }

    @Override
    public AnnotationEntity findById(String tenant, UUID id) {
        return dao.findById(tenant, id, AnnotationEntity.class);
    }

    @Override
    public void delete(String tenant, UUID id) {
        dao.delete(tenant, id, AnnotationEntity.class);
    }

    @Override
    public List<AnnotationEntity> filteredFindPaged(String tenant, Object filter, int page, int size) {
        return dao.filteredFindPaged(tenant, (Filter) filter, page, size, AnnotationEntity.class);
    }

    @Override
    public long countFiltered(String tenant, Object filter) {
        return dao.countFiltered(tenant, (Filter) filter, AnnotationEntity.class);
    }

    /**
     * Mesma correção aplicada ao {@code UserRepositoryImpl}: a visibilidade era resolvida
     * em memória <em>depois</em> da paginação (páginas capadas, registros inalcançáveis) e a
     * contagem carregava a tabela inteira. As regras agora entram no WHERE.
     */
    @Override
    public List<AnnotationEntity> filteredFindPagedComPermissao(String tenant, UserEntity loggedUser, Object filter, int page, int size) {
        if (loggedUser.getUserType() == UserType.ADMINISTRADOR) {
            return filteredFindPaged(tenant, filter, page, size);
        }
        return dao.filteredFindPaged(tenant, (Filter) filter, page, size, AnnotationEntity.class,
                visibilityOf(loggedUser));
    }

    @Override
    public long countFilteredComPermissao(String tenant, UserEntity loggedUser, Object filter) {
        if (loggedUser.getUserType() == UserType.ADMINISTRADOR) {
            return countFiltered(tenant, filter);
        }
        return dao.countFiltered(tenant, (Filter) filter, AnnotationEntity.class, visibilityOf(loggedUser));
    }

    /** Anotação é visível para o psicólogo dono do pacote e para o paciente do pacote. */
    private IGenericDao.PredicateProvider<AnnotationEntity> visibilityOf(UserEntity loggedUser) {
        UUID userId = loggedUser.getId();

        return (cb, root, query) -> switch (loggedUser.getUserType()) {
            case PSICOLOGO -> List.of(cb.equal(
                    root.get("session").get("sessionPackage").get("psychologistId"), userId));
            case PACIENTE -> List.of(cb.equal(
                    root.get("session").get("sessionPackage").get("patient").get("id"), userId));
            default -> List.<Predicate>of(cb.disjunction());
        };
    }
}
