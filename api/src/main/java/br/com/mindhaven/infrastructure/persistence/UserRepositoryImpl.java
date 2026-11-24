package br.com.mindhaven.infrastructure.persistence;

import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.UserType;
import br.com.mindhaven.domain.repository.UserRepository;
import core.repository.dao.GenericDao;
import core.repository.dao.interfaces.IGenericDao;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.criteria.Predicate;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class UserRepositoryImpl implements UserRepository {

    @Inject
    GenericDao dao;

    @Override
    public UserEntity save(String tenant, UserEntity entity) {
        return dao.update(tenant, entity);
    }

    @Override
    public UserEntity update(String tenant, UserEntity entity) {
        return dao.update(tenant, entity);
    }

    @Override
    public UserEntity findById(String tenant, UUID id) {
        return dao.findById(tenant, id, UserEntity.class);
    }

    @Override
    public void delete(String tenant, UUID id) {
        dao.delete(tenant, id, UserEntity.class);
    }

    @Override
    public List<UserEntity> filteredFindPaged(String tenant, Object filter, int page, int size) {
        return dao.filteredFindPaged(tenant, (Filter) filter, page, size, UserEntity.class);
    }

    @Override
    public long countFiltered(String tenant, Object filter) {
        return dao.countFiltered(tenant, (Filter) filter, UserEntity.class);
    }

    /**
     * A regra de visibilidade agora vai para o WHERE da consulta.
     *
     * <p>Antes a página era buscada do banco e só então filtrada em memória, o que quebrava
     * a paginação de forma visível: um psicólogo pedindo 5 registros por página recebia
     * "5 menos os que não são dele" — às vezes zero — e os registros descartados não
     * apareciam em página nenhuma. Filtrando no banco, cada página vem cheia e completa.
     */
    @Override
    public List<UserEntity> filteredFindPagedComPermissao(String tenant, UserEntity loggedUser, Object filter, int page, int size) {
        if (loggedUser.getUserType() == UserType.ADMINISTRADOR) {
            return filteredFindPaged(tenant, filter, page, size);
        }
        return dao.filteredFindPaged(tenant, (Filter) filter, page, size, UserEntity.class,
                visibilityOf(loggedUser));
    }

    /**
     * Contava carregando <b>a tabela inteira</b> de usuários ({@code size = Integer.MAX_VALUE})
     * e chamando {@code size()} na lista — em toda listagem. Agora é um {@code COUNT} no banco.
     */
    @Override
    public long countFilteredComPermissao(String tenant, UserEntity loggedUser, Object filter) {
        if (loggedUser.getUserType() == UserType.ADMINISTRADOR) {
            return countFiltered(tenant, filter);
        }
        return dao.countFiltered(tenant, (Filter) filter, UserEntity.class, visibilityOf(loggedUser));
    }

    /** Traduz o perfil do usuário logado em restrições de visibilidade. */
    private IGenericDao.PredicateProvider<UserEntity> visibilityOf(UserEntity loggedUser) {
        String keycloakId = loggedUser.getKeycloakId();

        return (cb, root, query) -> switch (loggedUser.getUserType()) {
            case PSICOLOGO -> List.of(cb.or(
                    cb.equal(root.get("keycloakId"), keycloakId),
                    cb.equal(root.get("registeredByKeycloakId"), keycloakId)));
            case PACIENTE -> List.<Predicate>of(cb.equal(root.get("keycloakId"), keycloakId));
            // Nenhum outro perfil enxerga usuários: predicado sempre falso.
            default -> List.<Predicate>of(cb.disjunction());
        };
    }
}
