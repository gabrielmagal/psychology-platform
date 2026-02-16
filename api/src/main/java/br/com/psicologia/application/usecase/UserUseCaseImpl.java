package br.com.psicologia.application.usecase;

import br.com.psicologia.application.usecase.interfaces.UserUseCase;
import br.com.psicologia.domain.entity.UserEntity;
import br.com.psicologia.domain.entity.enums.UserType;
import br.com.psicologia.domain.repository.UserRepository;
import core.service.KeycloakServiceImpl;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class UserUseCaseImpl implements UserUseCase {
    private final UserRepository userRepository;
    private final KeycloakServiceImpl keycloakService;

    public UserUseCaseImpl(UserRepository userRepository, KeycloakServiceImpl keycloakService) {
        this.userRepository = userRepository;
        this.keycloakService = keycloakService;
    }

    @Override
    public UserEntity save(String tenant, UserEntity loggedUser, UserEntity entity) {
        if (loggedUser.getUserType() == UserType.ADMINISTRADOR) {
            Set<String> roles = switch (entity.getUserType()) {
                case PSICOLOGO -> Set.of("PSICOLOGO_ROLE");
                case PACIENTE  -> Set.of("PACIENTE_ROLE");
                case ADMINISTRADOR  -> Set.of("ADMINISTRADOR_ROLE");
            };
            String userId = keycloakService.createUser(tenant, entity, roles);
            entity.setKeycloakId(userId);
            entity.setRegisteredByKeycloakId(loggedUser.getKeycloakId());
            UserEntity saved = userRepository.save(tenant, entity);
            return userRepository.findById(tenant, saved.getId());
        } else if (loggedUser.getUserType() == UserType.PSICOLOGO) {
            if (entity.getUserType() == UserType.PACIENTE) {
                Set<String> roles = Set.of("PACIENTE_ROLE");
                String userId = keycloakService.createUser(tenant, entity, roles);
                entity.setKeycloakId(userId);
                entity.setRegisteredByKeycloakId(loggedUser.getKeycloakId());
                UserEntity saved = userRepository.save(tenant, entity);
                return userRepository.findById(tenant, saved.getId());
            }
            throw new SecurityException("Psicólogos só podem criar pacientes.");
        }
        throw new SecurityException("Sem permissão para criar usuário.");
    }

    @Override
    public UserEntity update(String tenant, UserEntity loggedUser, UserEntity entity) {
        UserEntity original = userRepository.findById(tenant, entity.getId());
        if (original == null) {
            throw new IllegalArgumentException("Usuário não encontrado.");
        }
        if (loggedUser.getUserType() == UserType.ADMINISTRADOR) {
            Set<String> roles = switch (entity.getUserType()) {
                case PSICOLOGO -> Set.of("PSICOLOGO_ROLE");
                case PACIENTE  -> Set.of("PACIENTE_ROLE");
                case ADMINISTRADOR  -> Set.of("ADMINISTRADOR_ROLE");
            };
            entity.setKeycloakId(original.getKeycloakId());
            entity.setRegisteredByKeycloakId(original.getRegisteredByKeycloakId());
            UserEntity saved = userRepository.update(tenant, entity);
            keycloakService.updateUser(tenant, original.getKeycloakId(), saved, roles);
            return userRepository.findById(tenant, saved.getId());
        } else if (loggedUser.getUserType() == UserType.PSICOLOGO) {
            if (original.getKeycloakId().equals(loggedUser.getKeycloakId()) || original.getRegisteredByKeycloakId().equals(loggedUser.getKeycloakId())) {
                original.setFirstName(entity.getFirstName());
                original.setLastName(entity.getLastName());
                original.setEmail(entity.getEmail());
                original.setPhoneNumber(entity.getPhoneNumber());
                original.setBirthDate(entity.getBirthDate());
                original.setProfileImage(entity.getProfileImage());
                return userRepository.update(tenant, original);
            }
            throw new SecurityException("Psicólogos só podem editar seus próprios dados ou de seus pacientes.");
        } else if (loggedUser.getUserType() == UserType.PACIENTE) {
            if (original.getKeycloakId().equals(loggedUser.getKeycloakId()) || original.getRegisteredByKeycloakId().equals(loggedUser.getKeycloakId())) {
                original.setFirstName(entity.getFirstName());
                original.setLastName(entity.getLastName());
                original.setEmail(entity.getEmail());
                original.setPhoneNumber(entity.getPhoneNumber());
                original.setBirthDate(entity.getBirthDate());
                original.setProfileImage(entity.getProfileImage());
                return userRepository.update(tenant, original);
            }
            throw new SecurityException("Paciente só pode alterar seus próprios dados.");
        }
        throw new SecurityException("Sem permissão para atualizar usuário.");
    }

    @Override
    public UserEntity findById(String tenant, UserEntity loggedUser, UUID id) {
        UserEntity user = userRepository.findById(tenant, id);
        if (user == null) {
            throw new IllegalArgumentException("Usuário não encontrado.");
        }
        if (loggedUser.getUserType() == UserType.ADMINISTRADOR) {
            return user;
        } else if (loggedUser.getUserType() == UserType.PSICOLOGO) {
            if (user.getRegisteredByKeycloakId().equals(loggedUser.getKeycloakId()) || user.getKeycloakId().equals(loggedUser.getKeycloakId())) {
                return user;
            }
            throw new SecurityException("Psicólogo só pode ver a sí mesmo e seus pacientes.");
        } else if (loggedUser.getUserType() == UserType.PACIENTE) {
            if (user.getKeycloakId().equals(loggedUser.getKeycloakId()) || user.getRegisteredByKeycloakId().equals(loggedUser.getKeycloakId())) {
                return user;
            }
            throw new SecurityException("Paciente só pode ver a sí mesmo.");
        }
        throw new SecurityException("Sem permissão para visualizar usuário.");
    }

    @Override
    public void delete(String tenant, UserEntity loggedUser, UUID id) {
        UserEntity user = userRepository.findById(tenant, id);
        if (user == null) {
            throw new IllegalArgumentException("Usuário não encontrado.");
        }
        if (loggedUser.getUserType() == UserType.ADMINISTRADOR) {
            userRepository.delete(tenant, id);
            keycloakService.deleteUser(tenant, UUID.fromString(user.getKeycloakId()));
        } else if (loggedUser.getUserType() == UserType.PSICOLOGO) {
            if (user.getRegisteredByKeycloakId().equals(loggedUser.getKeycloakId()) || user.getKeycloakId().equals(loggedUser.getKeycloakId())) {
                userRepository.delete(tenant, id);
                return;
            }
            throw new SecurityException("Psicólogo só pode gerenciar a sí mesmo e seus pacientes.");
        } else if (loggedUser.getUserType() == UserType.PACIENTE) {
            throw new SecurityException("Pacientes não têm permissão para remover usuários.");
        }
        throw new SecurityException("Sem permissão para remover usuário.");
    }

    @Override
    public List<UserEntity> filteredFindPaged(String tenant, UserEntity loggedUser, Filter filter, int page, int size) {
        return userRepository.filteredFindPagedComPermissao(tenant, loggedUser, filter, page, size);
    }

    @Override
    public long countFiltered(String tenant, UserEntity loggedUser, Filter filter) {
        return userRepository.countFilteredComPermissao(tenant, loggedUser, filter);
    }
}
