package br.com.psicologia.service;

import br.com.psicologia.model.SessionRatingEntity;
import br.com.psicologia.model.UserEntity;
import core.repository.dao.GenericDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.SecurityContext;

import java.util.UUID;

@ApplicationScoped
public class SessionRatingService {

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    @Transactional
    public SessionRatingEntity save(String tenant, SessionRatingEntity entity) {
        return dao.update(tenant, entity);
    }

    public Double calculateAverageByPsychologist(UUID psychologistId) {
        return em.createQuery("""
            SELECT AVG(r.rating)
            FROM SessionRatingEntity r
            JOIN r.session s
            JOIN s.sessionPackage sp
            WHERE sp.psychologistId = :id
        """, Double.class)
                .setParameter("id", psychologistId)
                .getSingleResult();
    }
}
