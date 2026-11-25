package br.com.mindhaven.application.usecase;

import br.com.mindhaven.application.usecase.interfaces.SessionUseCase;
import br.com.mindhaven.domain.entity.SessionEntity;
import br.com.mindhaven.domain.entity.SessionPackageEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import core.error.BusinessException;
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

    private final br.com.mindhaven.application.service.AgendaRules agendaRules;

    public SessionUseCaseImpl(SessionRepository sessionRepository, SessionPackageRepository sessionPackageRepository,
                              br.com.mindhaven.application.service.AgendaRules agendaRules) {
        this.agendaRules = agendaRules;
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
            case PROFISSIONAL -> {
                SessionPackageEntity pacote = entity.getSessionPackage();
                if (pacote.getId() != null && (pacote.getProfessionalId() == null || !pacote.getProfessionalId().equals(loggedUser.getId()))) {
                    pacote = sessionPackageRepository.findById(tenant, pacote.getId());
                }
                if (pacote != null && pacote.getProfessionalId().equals(loggedUser.getId())) {
                    entity.setSessionPackage(pacote);
                    assertNoConflict(tenant, entity);
                    yield sessionRepository.save(tenant, entity);
                } else {
                    throw new SecurityException("Profissional só pode criar sessões nos próprios pacotes.");
                }
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode criar sessões.");
        };
    }

    /**
     * Altera os dados da sessão sobre o registro já gravado.
     *
     * <p>Pelo mesmo motivo do pacote: gravar a entidade vinda da requisição trocava a lista
     * de anotações pela lista vazia do formulário e, com {@code orphanRemoval}, apagava as
     * anotações clínicas da sessão. A sessão também permanece no pacote em que foi criada,
     * e a verificação de dono usa o pacote gravado, não o que o cliente informa.
     */
    public SessionEntity update(String tenant, UserEntity loggedUser, SessionEntity entity) {
        SessionEntity stored = entity.getId() == null ? null : sessionRepository.findById(tenant, entity.getId());
        if (stored == null) {
            throw new IllegalArgumentException("Sessão não encontrada.");
        }
        SessionPackageEntity pacote = stored.getSessionPackage();
        switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> { }
            case PROFISSIONAL -> {
                if (pacote == null || !loggedUser.getId().equals(pacote.getProfessionalId())) {
                    throw new SecurityException("Profissional só pode atualizar sessões dos próprios pacotes.");
                }
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode atualizar sessões.");
        }
        stored.setDateSession(entity.getDateSession());
        stored.setStartTime(entity.getStartTime());
        stored.setTitle(entity.getTitle());
        stored.setSummary(entity.getSummary());
        stored.setEvolution(entity.getEvolution());
        stored.setPrivateNotes(entity.getPrivateNotes());
        stored.setAttendedAt(entity.getAttendedAt());
        stored.setMeetingLink(entity.getMeetingLink());
        if (entity.getDurationMinutes() != null) stored.setDurationMinutes(entity.getDurationMinutes());
        if (entity.getStatus() != null) stored.setStatus(entity.getStatus());
        if (entity.getModality() != null) stored.setModality(entity.getModality());
        assertNoConflict(tenant, stored);
        return sessionRepository.update(tenant, stored);
    }

    /**
     * Impede duas sessões do mesmo profissional no mesmo horário. Só vale para sessão com
     * horário definido; sessões canceladas não ocupam a agenda.
     */
    private void assertNoConflict(String tenant, SessionEntity entity) {
        SessionPackageEntity pacote = entity.getSessionPackage();
        if (entity.getStatus() == SessionStatus.CANCELADA || pacote == null) return;
        agendaRules.check(tenant, pacote.getProfessionalId(), entity.getDateSession(), entity.getStartTime(),
                entity.getDurationMinutes(), entity.getId());
    }

    public SessionEntity findById(String tenant, UserEntity loggedUser, UUID id) {
        SessionEntity session = sessionRepository.findById(tenant, id);
        if (session == null) return null;
        return switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> session;
            case PROFISSIONAL -> {
                if (session.getSessionPackage() != null && session.getSessionPackage().getProfessionalId() != null && session.getSessionPackage().getProfessionalId().equals(loggedUser.getId())) {
                    yield session;
                } else {
                    throw new SecurityException("Profissional só pode acessar sessões dos próprios pacotes.");
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
            case PROFISSIONAL -> {
                if (session.getSessionPackage() != null && session.getSessionPackage().getProfessionalId() != null && session.getSessionPackage().getProfessionalId().equals(loggedUser.getId())) {
                    sessionRepository.delete(tenant, id);
                } else {
                    throw new SecurityException("Profissional só pode deletar sessões dos próprios pacotes.");
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
            case PROFISSIONAL -> {
                if (filter.getFilterParams() == null) {
                    filter.setFilterParams(new ArrayList<>());
                }
                filter.getFilterParams().add(new FilterParam("sessionPackage.professionalId", loggedUser.getId().toString()));
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
            case PROFISSIONAL -> {
                if (filter.getFilterParams() == null) {
                    filter.setFilterParams(new ArrayList<>());
                }
                filter.getFilterParams().add(new FilterParam("sessionPackage.professionalId", loggedUser.getId().toString()));
                return sessionRepository.countFiltered(tenant, filter);
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode contar sessões.");
            default -> throw new SecurityException("Tipo de usuário não suportado.");
        }
    }
}
