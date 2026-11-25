package br.com.mindhaven.application.service;

import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.WaitlistEntryEntity;
import br.com.mindhaven.domain.entity.enums.UserType;
import core.error.BusinessException;
import core.repository.dao.GenericDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lista de espera por vaga. O administrador (a recepção) cuida da lista da clínica inteira;
 * cada profissional vê a própria fila e quem espera "qualquer profissional".
 */
@ApplicationScoped
@Transactional
public class WaitlistService {
    private static final Set<String> STATUSES = Set.of(WaitlistEntryEntity.WAITING, WaitlistEntryEntity.CONTACTED,
            WaitlistEntryEntity.SCHEDULED, WaitlistEntryEntity.GAVE_UP);
    private static final Set<String> CLOSED = Set.of(WaitlistEntryEntity.SCHEDULED, WaitlistEntryEntity.GAVE_UP);

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    @Inject
    PatientAccess access;

    /** Fila por ordem de chegada. Quem já foi agendado ou desistiu só entra com {@code includeClosed}. */
    public List<Map<String, Object>> list(String tenant, UserEntity user, boolean includeClosed) {
        requireStaff(user);
        dao.defineSchema(tenant);
        boolean onlyMine = user.getUserType() == UserType.PROFISSIONAL;
        var query = em.createQuery("select w from WaitlistEntryEntity w where 1 = 1"
                + (onlyMine ? " and (w.professionalId = :me or w.professionalId is null)" : "")
                + (includeClosed ? "" : " and w.status not in :closed")
                + " order by w.createdAt", WaitlistEntryEntity.class);
        if (onlyMine) query.setParameter("me", user.getId());
        if (!includeClosed) query.setParameter("closed", CLOSED);

        Map<UUID, String> professionals = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (WaitlistEntryEntity w : query.getResultList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", w.getId());
            row.put("name", w.getName());
            row.put("phone", w.getPhone());
            row.put("patientId", w.getPatientId());
            row.put("preference", w.getPreference());
            row.put("modality", w.getModality());
            row.put("notes", w.getNotes());
            row.put("status", w.getStatus());
            row.put("createdAt", w.getCreatedAt().toString());
            row.put("waitingDays", ChronoUnit.DAYS.between(w.getCreatedAt(),
                    w.getResolvedAt() == null ? now : w.getResolvedAt()));
            row.put("professionalId", w.getProfessionalId());
            row.put("professionalName", w.getProfessionalId() == null ? null : professionals.computeIfAbsent(
                    w.getProfessionalId(), id -> fullName(em.find(UserEntity.class, id))));
            rows.add(row);
        }
        return rows;
    }

    public Map<String, Object> add(String tenant, UserEntity user, UUID patientId, String name, String phone,
                                   String preference, String modality, String notes, UUID professionalId) {
        requireStaff(user);
        dao.defineSchema(tenant);
        WaitlistEntryEntity entry = new WaitlistEntryEntity();
        if (patientId != null) {
            UserEntity patient = em.find(UserEntity.class, patientId);
            if (patient == null || patient.getUserType() != UserType.PACIENTE) {
                throw BusinessException.invalid("Paciente não encontrado.");
            }
            access.requireAssociated(tenant, user, patient);
            entry.setPatientId(patientId);
            name = fullName(patient);
            if (phone == null) phone = patient.getPhoneNumber();
        }
        if (name == null || name.isBlank()) throw BusinessException.invalid("Informe o nome de quem está esperando.");
        if (modality != null && !Set.of("PRESENCIAL", "ONLINE").contains(modality)) {
            throw BusinessException.invalid("Modalidade inválida.");
        }
        if (user.getUserType() == UserType.PROFISSIONAL) {
            // O profissional só põe gente na própria fila.
            professionalId = user.getId();
        } else if (professionalId != null) {
            UserEntity professional = em.find(UserEntity.class, professionalId);
            if (professional == null || professional.getUserType() != UserType.PROFISSIONAL) {
                throw BusinessException.invalid("Profissional não encontrado.");
            }
        }
        entry.setProfessionalId(professionalId);
        entry.setName(name.trim());
        entry.setPhone(phone);
        entry.setPreference(preference);
        entry.setModality(modality);
        entry.setNotes(notes);
        entry.setStatus(WaitlistEntryEntity.WAITING);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setCreatedBy(user.getId());
        em.persist(entry);
        return Map.of("id", entry.getId());
    }

    public void changeStatus(String tenant, UserEntity user, UUID id, String status) {
        if (status == null || !STATUSES.contains(status)) throw BusinessException.invalid("Situação inválida.");
        WaitlistEntryEntity entry = owned(tenant, user, id);
        entry.setStatus(status);
        entry.setResolvedAt(CLOSED.contains(status) ? LocalDateTime.now() : null);
    }

    public void remove(String tenant, UserEntity user, UUID id) {
        em.remove(owned(tenant, user, id));
    }

    private WaitlistEntryEntity owned(String tenant, UserEntity user, UUID id) {
        requireStaff(user);
        dao.defineSchema(tenant);
        WaitlistEntryEntity entry = id == null ? null : em.find(WaitlistEntryEntity.class, id);
        if (entry == null) throw BusinessException.invalid("Registro da lista de espera não encontrado.");
        if (user.getUserType() == UserType.PROFISSIONAL && entry.getProfessionalId() != null
                && !entry.getProfessionalId().equals(user.getId())) {
            throw new SecurityException("Esta pessoa está na fila de outro profissional.");
        }
        return entry;
    }

    private static void requireStaff(UserEntity user) {
        if (user.getUserType() == UserType.PACIENTE) throw new SecurityException("Ação restrita à equipe da clínica.");
    }

    private static String fullName(UserEntity user) {
        return user == null ? null : (user.getFirstName() + " " + user.getLastName()).trim();
    }
}
