package br.com.mindhaven.application.service;

import br.com.mindhaven.domain.entity.PatientLinkEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.UserPhotoEntity;
import br.com.mindhaven.domain.entity.enums.UserType;
import core.error.BusinessException;
import core.repository.dao.GenericDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Quem enxerga quem: Administrador > Profissional > Paciente.
 *
 * <ul>
 *   <li>O <b>administrador</b> vê tudo, de todos os profissionais.</li>
 *   <li>O <b>profissional</b> vê a si mesmo e os pacientes associados a ele: os que cadastrou,
 *       os que têm pacote com ele e os que foram vinculados a ele.</li>
 *   <li>O <b>paciente</b> só vê a si mesmo.</li>
 * </ul>
 *
 * <p>O cadastro do paciente é compartilhado entre os profissionais que o atendem. O que cada
 * profissional registra — anamnese, evolução, anexos, pagamentos — é só dele.
 *
 * <p>Também guarda a foto de perfil, que mora no banco.
 */
@ApplicationScoped
@Transactional
public class PatientAccess {
    private static final int MAX_PHOTO_BYTES = 2 * 1024 * 1024;

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    /** O profissional está associado ao paciente? */
    public boolean isAssociated(String tenant, UserEntity professional, UserEntity patient) {
        if (professional == null || patient == null) return false;
        if (professional.getKeycloakId() != null
                && professional.getKeycloakId().equals(patient.getRegisteredByKeycloakId())) {
            return true;
        }
        dao.defineSchema(tenant);
        if (em.find(PatientLinkEntity.class, new PatientLinkEntity.Key(patient.getId(), professional.getId())) != null) {
            return true;
        }
        return em.createQuery("select count(p) from SessionPackageEntity p where p.patient.id = :patient "
                        + "and p.professionalId = :me", Long.class)
                .setParameter("patient", patient.getId()).setParameter("me", professional.getId())
                .getSingleResult() > 0;
    }

    /** Lança se o profissional não está associado ao paciente. Administrador sempre passa. */
    public void requireAssociated(String tenant, UserEntity user, UserEntity patient) {
        if (user.getUserType() == UserType.ADMINISTRADOR) return;
        if (user.getUserType() != UserType.PROFISSIONAL || !isAssociated(tenant, user, patient)) {
            throw new SecurityException("Este paciente não está associado a você. Para atendê-lo, cadastre-o "
                    + "informando o CPF: o cadastro existente será vinculado a você.");
        }
    }

    /** Passa a associar o paciente ao profissional (sem efeito se já estiver). */
    public void link(String tenant, UUID patientId, UUID professionalId) {
        dao.defineSchema(tenant);
        PatientLinkEntity.Key key = new PatientLinkEntity.Key(patientId, professionalId);
        if (em.find(PatientLinkEntity.class, key) != null) return;
        PatientLinkEntity link = new PatientLinkEntity();
        link.setPatientId(patientId);
        link.setProfessionalId(professionalId);
        link.setLinkedAt(LocalDateTime.now());
        em.persist(link);
    }

    /** Quantos OUTROS profissionais estão associados ao paciente (por vínculo ou por pacote). */
    public long otherProfessionals(String tenant, UUID patientId, UUID professionalId) {
        dao.defineSchema(tenant);
        long links = em.createQuery("select count(l) from PatientLinkEntity l where l.patientId = :p "
                        + "and l.professionalId <> :me", Long.class)
                .setParameter("p", patientId).setParameter("me", professionalId).getSingleResult();
        long packages = em.createQuery("select count(distinct s.professionalId) from SessionPackageEntity s "
                        + "where s.patient.id = :p and s.professionalId <> :me", Long.class)
                .setParameter("p", patientId).setParameter("me", professionalId).getSingleResult();
        return links + packages;
    }

    // -------------------------------------------------------------------- foto

    public void savePhoto(String tenant, UUID userId, String base64) {
        if (userId == null || base64 == null || base64.isBlank()) return;
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.replaceFirst("^data:[^,]+,", ""));
        } catch (IllegalArgumentException e) {
            throw BusinessException.invalid("A imagem chegou corrompida. Escolha o arquivo de novo.");
        }
        if (bytes.length > MAX_PHOTO_BYTES) {
            throw BusinessException.invalid("A foto passa de 2 MB. Escolha uma imagem menor.");
        }
        dao.defineSchema(tenant);
        UserPhotoEntity photo = em.find(UserPhotoEntity.class, userId);
        if (photo == null) {
            photo = new UserPhotoEntity();
            photo.setUserId(userId);
        }
        photo.setContent(bytes);
        photo.setUpdatedAt(LocalDateTime.now());
        em.merge(photo);
    }

    /** @return a foto em base64, ou texto vazio se o usuário não tem foto */
    public String photo(String tenant, UUID userId) {
        if (userId == null) return "";
        dao.defineSchema(tenant);
        UserPhotoEntity photo = em.find(UserPhotoEntity.class, userId);
        return photo == null ? "" : Base64.getEncoder().encodeToString(photo.getContent());
    }

    /** Fotos de vários usuários numa consulta só (listagens). */
    public Map<UUID, String> photos(String tenant, Collection<UUID> userIds) {
        Map<UUID, String> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) return result;
        dao.defineSchema(tenant);
        for (UserPhotoEntity photo : em.createQuery("select p from UserPhotoEntity p where p.userId in :ids",
                UserPhotoEntity.class).setParameter("ids", userIds).getResultList()) {
            result.put(photo.getUserId(), Base64.getEncoder().encodeToString(photo.getContent()));
        }
        return result;
    }

    public void deletePhoto(String tenant, UUID userId) {
        dao.defineSchema(tenant);
        UserPhotoEntity photo = em.find(UserPhotoEntity.class, userId);
        if (photo != null) em.remove(photo);
    }
}
