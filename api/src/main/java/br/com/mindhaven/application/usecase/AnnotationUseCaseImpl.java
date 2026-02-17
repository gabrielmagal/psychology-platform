package br.com.mindhaven.application.usecase;

import br.com.mindhaven.application.usecase.interfaces.AnnotationUseCase;
import br.com.mindhaven.domain.entity.AnnotationEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.repository.AnnotationRepository;
import core.service.model.Filter;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AnnotationUseCaseImpl implements AnnotationUseCase {
    private final AnnotationRepository repository;

    public AnnotationUseCaseImpl(AnnotationRepository repository) {
        this.repository = repository;
    }

    public AnnotationEntity save(String tenant, UserEntity loggedUser, AnnotationEntity entity) {
        return switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> repository.save(tenant, entity);
            case PSICOLOGO -> {
                if (entity.getSession() != null &&
                        entity.getSession().getSessionPackage() != null &&
                        entity.getSession().getSessionPackage().getPsychologistId().equals(loggedUser.getId())) {
                    yield repository.save(tenant, entity);
                }
                throw new SecurityException("Psicólogo só pode criar anotações nas suas próprias sessões.");
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode criar anotações.");
        };
    }

    public AnnotationEntity update(String tenant, UserEntity loggedUser, AnnotationEntity entity) {
        AnnotationEntity original = repository.findById(tenant, entity.getId());
        if (original == null) {
            throw new IllegalArgumentException("Anotação não encontrada.");
        }
        return switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> repository.update(tenant, entity);
            case PSICOLOGO -> {
                if (original.getSession() != null &&
                        original.getSession().getSessionPackage() != null &&
                        original.getSession().getSessionPackage().getPsychologistId().equals(loggedUser.getId())) {
                    yield repository.update(tenant, entity);
                }
                throw new SecurityException("Psicólogo só pode alterar anotações das suas próprias sessões.");
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode alterar anotações.");
        };
    }

    public AnnotationEntity findById(String tenant, UserEntity loggedUser, UUID id) {
        AnnotationEntity original = repository.findById(tenant, id);
        if (original == null) {
            throw new IllegalArgumentException("Anotação não encontrada.");
        }
        return switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> original;
            case PSICOLOGO -> {
                if (original.getSession() != null &&
                        original.getSession().getSessionPackage() != null &&
                        original.getSession().getSessionPackage().getPsychologistId().equals(loggedUser.getId())) {
                    yield original;
                }
                throw new SecurityException("Psicólogo só pode ver suas próprias anotações.");
            }
            case PACIENTE -> {
                if (original.getSession() != null &&
                        original.getSession().getSessionPackage() != null &&
                        original.getSession().getSessionPackage().getPatient().getId().equals(loggedUser.getId())) {
                    yield original;
                }
                throw new SecurityException("Paciente só pode ver suas próprias anotações.");
            }
        };
    }

    public void delete(String tenant, UserEntity loggedUser, UUID id) {
        AnnotationEntity original = repository.findById(tenant, id);
        if (original == null) {
            throw new IllegalArgumentException("Anotação não encontrada.");
        }
        switch (loggedUser.getUserType()) {
            case ADMINISTRADOR:
                repository.delete(tenant, id);
                break;
            case PSICOLOGO:
                if (original.getSession() != null &&
                    original.getSession().getSessionPackage() != null &&
                    original.getSession().getSessionPackage().getPsychologistId().equals(loggedUser.getId())) {
                    repository.delete(tenant, id);
                    break;
                }
                throw new SecurityException("Psicólogo só pode deletar anotações das suas próprias sessões.");
            case PACIENTE:
                throw new SecurityException("Paciente não pode deletar anotações.");
            default:
                throw new SecurityException("Perfil não autorizado.");
        }
    }

    public List<AnnotationEntity> filteredFindPaged(String tenant, UserEntity loggedUser, Filter filter, int page, int size) {
        return repository.filteredFindPagedComPermissao(tenant, loggedUser, filter, page, size);
    }

    public long countFiltered(String tenant, UserEntity loggedUser, Filter filter) {
        return repository.countFilteredComPermissao(tenant, loggedUser, filter);
    }
}
