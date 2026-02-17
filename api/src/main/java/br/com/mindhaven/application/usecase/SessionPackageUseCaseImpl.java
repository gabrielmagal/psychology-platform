package br.com.mindhaven.application.usecase;

import br.com.mindhaven.application.usecase.interfaces.SessionPackageUseCase;
import br.com.mindhaven.domain.entity.SessionPackageEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.repository.SessionPackageRepository;
import core.service.model.Filter;
import core.service.model.FilterParam;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SessionPackageUseCaseImpl implements SessionPackageUseCase {
    private final SessionPackageRepository sessionPackageRepository;

    public SessionPackageUseCaseImpl(SessionPackageRepository sessionPackageRepository) {
        this.sessionPackageRepository = sessionPackageRepository;
    }

    public SessionPackageEntity save(String tenant, UserEntity loggedUser, SessionPackageEntity entity) {
        return switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> sessionPackageRepository.save(tenant, entity);
            case PSICOLOGO -> {
                if (entity.getPsychologistId() != null && entity.getPsychologistId().equals(loggedUser.getId())) {
                    yield sessionPackageRepository.save(tenant, entity);
                } else {
                    throw new SecurityException("Psicólogo só pode criar pacotes para si mesmo.");
                }
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode criar pacotes de sessão.");
        };
    }

    public SessionPackageEntity update(String tenant, UserEntity loggedUser, SessionPackageEntity entity) {
        return switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> sessionPackageRepository.update(tenant, entity);
            case PSICOLOGO -> {
                if (entity.getPsychologistId() != null && entity.getPsychologistId().equals(loggedUser.getId())) {
                    yield sessionPackageRepository.update(tenant, entity);
                } else {
                    throw new SecurityException("Psicólogo só pode atualizar pacotes próprios.");
                }
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode atualizar pacotes de sessão.");
        };
    }

    public SessionPackageEntity findById(String tenant, UserEntity loggedUser, UUID id) {
        SessionPackageEntity pacote = sessionPackageRepository.findById(tenant, id);
        if (pacote == null) return null;
        return switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> pacote;
            case PSICOLOGO -> {
                if (pacote.getPsychologistId() != null && pacote.getPsychologistId().equals(loggedUser.getId())) {
                    yield pacote;
                } else {
                    throw new SecurityException("Psicólogo só pode acessar pacotes próprios.");
                }
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode acessar pacotes de sessão.");
        };
    }

    public void delete(String tenant, UserEntity loggedUser, UUID id) {
        SessionPackageEntity pacote = sessionPackageRepository.findById(tenant, id);
        if (pacote == null) throw new IllegalArgumentException("Pacote de sessão não encontrado.");
        switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> sessionPackageRepository.delete(tenant, id);
            case PSICOLOGO -> {
                if (pacote.getPsychologistId() != null && pacote.getPsychologistId().equals(loggedUser.getId())) {
                    sessionPackageRepository.delete(tenant, id);
                } else {
                    throw new SecurityException("Psicólogo só pode deletar pacotes próprios.");
                }
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode deletar pacotes de sessão.");
        }
    }

    public List<SessionPackageEntity> filteredFindPaged(String tenant, UserEntity loggedUser, Filter filter, int page, int size) {
        switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> {
                return sessionPackageRepository.filteredFindPaged(tenant, filter, page, size);
            }
            case PSICOLOGO -> {
                if (filter.getFilterParams() == null) {
                    filter.setFilterParams(new ArrayList<>());
                }
                filter.getFilterParams().add(new FilterParam("psychologistId", loggedUser.getId().toString()));
                return sessionPackageRepository.filteredFindPaged(tenant, filter, page, size);
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode listar pacotes de sessão.");
            default -> throw new SecurityException("Tipo de usuário não suportado.");
        }
    }

    public long countFiltered(String tenant, UserEntity loggedUser, Filter filter) {
        switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> {
                return sessionPackageRepository.countFiltered(tenant, filter);
            }
            case PSICOLOGO -> {
                if (filter.getFilterParams() == null) {
                    filter.setFilterParams(new ArrayList<>());
                }
                filter.getFilterParams().add(new FilterParam("psychologistId", loggedUser.getId().toString()));
                return sessionPackageRepository.countFiltered(tenant, filter);
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode contar pacotes de sessão.");
            default -> throw new SecurityException("Tipo de usuário não suportado.");
        }
    }
}
