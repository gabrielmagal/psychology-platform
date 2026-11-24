package br.com.mindhaven.application.usecase;

import br.com.mindhaven.application.usecase.interfaces.SessionUseCase;
import br.com.mindhaven.domain.entity.SessionEntity;
import br.com.mindhaven.domain.entity.SessionPackageEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.application.BusinessException;
import br.com.mindhaven.domain.entity.enums.SessionStatus;
import br.com.mindhaven.domain.repository.SessionPackageRepository;
import br.com.mindhaven.domain.repository.SessionRepository;
import core.service.model.Filter;
import core.service.model.FilterParam;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SessionUseCaseImpl implements SessionUseCase {
    private final SessionRepository sessionRepository;
    private final SessionPackageRepository sessionPackageRepository;

    public SessionUseCaseImpl(SessionRepository sessionRepository, SessionPackageRepository sessionPackageRepository) {
        this.sessionRepository = sessionRepository;
        this.sessionPackageRepository = sessionPackageRepository;
    }

    public SessionEntity save(String tenant, UserEntity loggedUser, SessionEntity entity) {
        return switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> {
                SessionPackageEntity pacote = entity.getSessionPackage();
                if (pacote.getId() != null) {
                    pacote = sessionPackageRepository.findById(tenant, pacote.getId());
                    entity.setSessionPackage(pacote);
                }
                assertNoConflict(tenant, entity);
                    yield sessionRepository.save(tenant, entity);
            }
            case PSICOLOGO -> {
                SessionPackageEntity pacote = entity.getSessionPackage();
                if (pacote.getId() != null && (pacote.getPsychologistId() == null || !pacote.getPsychologistId().equals(loggedUser.getId()))) {
                    pacote = sessionPackageRepository.findById(tenant, pacote.getId());
                }
                if (pacote != null && pacote.getPsychologistId().equals(loggedUser.getId())) {
                    entity.setSessionPackage(pacote);
                    assertNoConflict(tenant, entity);
                    yield sessionRepository.save(tenant, entity);
                } else {
                    throw new SecurityException("Psicólogo só pode criar sessões nos próprios pacotes.");
                }
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode criar sessões.");
        };
    }

    public SessionEntity update(String tenant, UserEntity loggedUser, SessionEntity entity) {
        return switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> {
                SessionPackageEntity pacote = entity.getSessionPackage();
                if (pacote.getId() != null) {
                    pacote = sessionPackageRepository.findById(tenant, pacote.getId());
                    entity.setSessionPackage(pacote);
                }
                assertNoConflict(tenant, entity);
                    yield sessionRepository.update(tenant, entity);
            }
            case PSICOLOGO -> {
                SessionPackageEntity pacote = entity.getSessionPackage();
                if (pacote.getId() != null && (pacote.getPsychologistId() == null || !pacote.getPsychologistId().equals(loggedUser.getId()))) {
                    pacote = sessionPackageRepository.findById(tenant, pacote.getId());
                }
                if (pacote != null && pacote.getPsychologistId().equals(loggedUser.getId())) {
                    entity.setSessionPackage(pacote);
                    assertNoConflict(tenant, entity);
                    yield sessionRepository.update(tenant, entity);
                } else {
                    throw new SecurityException("Psicólogo só pode atualizar sessões dos próprios pacotes.");
                }
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode atualizar sessões.");
        };
    }

    /**
     * Impede duas sessões do mesmo psicólogo no mesmo horário. Só vale para sessão com
     * horário definido; sessões canceladas não ocupam a agenda.
     */
    private void assertNoConflict(String tenant, SessionEntity entity) {
        SessionPackageEntity pacote = entity.getSessionPackage();
        if (entity.getDateSession() == null || entity.getStartTime() == null
                || entity.getStatus() == SessionStatus.CANCELADA
                || pacote == null || pacote.getPsychologistId() == null) {
            return;
        }

        Filter filter = new Filter();
        List<FilterParam> params = new ArrayList<>();
        params.add(new FilterParam("dateSession", entity.getDateSession().toString()));
        params.add(new FilterParam("sessionPackage.psychologistId", pacote.getPsychologistId().toString()));
        filter.setFilterParams(params);

        int start = entity.getStartTime().toSecondOfDay() / 60;
        int end = start + (entity.getDurationMinutes() == null ? 50 : entity.getDurationMinutes());

        for (SessionEntity other : sessionRepository.filteredFindPaged(tenant, filter, 0, 200)) {
            if (other.getId().equals(entity.getId()) || other.getStartTime() == null
                    || other.getStatus() == SessionStatus.CANCELADA) {
                continue;
            }
            int otherStart = other.getStartTime().toSecondOfDay() / 60;
            int otherEnd = otherStart + (other.getDurationMinutes() == null ? 50 : other.getDurationMinutes());
            if (start < otherEnd && otherStart < end) {
                throw BusinessException.conflict("Já existe uma sessão neste horário (começa às "
                        + other.getStartTime().toString().substring(0, 5) + ").");
            }
        }
    }

    public SessionEntity findById(String tenant, UserEntity loggedUser, UUID id) {
        SessionEntity session = sessionRepository.findById(tenant, id);
        if (session == null) return null;
        return switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> session;
            case PSICOLOGO -> {
                if (session.getSessionPackage() != null && session.getSessionPackage().getPsychologistId() != null && session.getSessionPackage().getPsychologistId().equals(loggedUser.getId())) {
                    yield session;
                } else {
                    throw new SecurityException("Psicólogo só pode acessar sessões dos próprios pacotes.");
                }
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode acessar sessões.");
        };
    }

    public void delete(String tenant, UserEntity loggedUser, UUID id) {
        SessionEntity session = sessionRepository.findById(tenant, id);
        if (session == null) throw new IllegalArgumentException("Sessão não encontrada.");
        switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> sessionRepository.delete(tenant, id);
            case PSICOLOGO -> {
                if (session.getSessionPackage() != null && session.getSessionPackage().getPsychologistId() != null && session.getSessionPackage().getPsychologistId().equals(loggedUser.getId())) {
                    sessionRepository.delete(tenant, id);
                } else {
                    throw new SecurityException("Psicólogo só pode deletar sessões dos próprios pacotes.");
                }
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode deletar sessões.");
        }
    }

    public List<SessionEntity> filteredFindPaged(String tenant, UserEntity loggedUser, Filter filter, int page, int size) {
        switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> {
                return sessionRepository.filteredFindPaged(tenant, filter, page, size);
            }
            case PSICOLOGO -> {
                if (filter.getFilterParams() == null) {
                    filter.setFilterParams(new ArrayList<>());
                }
                filter.getFilterParams().add(new FilterParam("sessionPackage.psychologistId", loggedUser.getId().toString()));
                return sessionRepository.filteredFindPaged(tenant, filter, page, size);
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode listar sessões.");
            default -> throw new SecurityException("Tipo de usuário não suportado.");
        }
    }

    public long countFiltered(String tenant, UserEntity loggedUser, Filter filter) {
        switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> {
                return sessionRepository.countFiltered(tenant, filter);
            }
            case PSICOLOGO -> {
                if (filter.getFilterParams() == null) {
                    filter.setFilterParams(new ArrayList<>());
                }
                filter.getFilterParams().add(new FilterParam("sessionPackage.psychologistId", loggedUser.getId().toString()));
                return sessionRepository.countFiltered(tenant, filter);
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode contar sessões.");
            default -> throw new SecurityException("Tipo de usuário não suportado.");
        }
    }
}
