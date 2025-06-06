package br.com.psicologia.service;

import br.com.psicologia.repository.model.SessionEntity;
import core.repository.dao.GenericDao;
import core.service.AbstractCrudService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;

@ApplicationScoped
public class SessionService extends AbstractCrudService<SessionEntity> {

    @Inject
    EntityManager em;

    @Inject
    GenericDao genericDao;

    public SessionService() {
        super(SessionEntity.class);
    }

    @Override
    public List<SessionEntity> findAllPaged(String tenant, int page, int size) {
        genericDao.defineSchema(tenant);

        return em.createQuery("""
            SELECT DISTINCT s FROM SessionEntity s
            LEFT JOIN FETCH s.annotation
            LEFT JOIN FETCH s.patient
            LEFT JOIN FETCH s.psychologist
            LEFT JOIN FETCH s.payment
        """, SessionEntity.class)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }
}
