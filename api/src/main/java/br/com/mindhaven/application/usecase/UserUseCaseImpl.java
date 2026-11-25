package br.com.mindhaven.application.usecase;

import br.com.mindhaven.application.usecase.interfaces.UserUseCase;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.UserType;
import br.com.mindhaven.domain.repository.UserRepository;
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

    @jakarta.inject.Inject
    br.com.mindhaven.application.service.RecordsService records;

    @jakarta.inject.Inject
    br.com.mindhaven.application.service.PatientAccess access;

    public UserUseCaseImpl(UserRepository userRepository, KeycloakServiceImpl keycloakService) {
        this.userRepository = userRepository;
        this.keycloakService = keycloakService;
    }

    @Override
    public UserEntity save(String tenant, UserEntity loggedUser, UserEntity entity) {
        if (loggedUser.getUserType() == UserType.ADMINISTRADOR) {
            Set<String> roles = switch (entity.getUserType()) {
                case PROFISSIONAL -> Set.of("PROFISSIONAL_ROLE");
                case PACIENTE  -> Set.of("PACIENTE_ROLE");
                case ADMINISTRADOR  -> Set.of("ADMINISTRADOR_ROLE");
            };
            String userId = keycloakService.createUser(tenant, entity, roles);
            entity.setKeycloakId(userId);
            entity.setRegisteredByKeycloakId(loggedUser.getKeycloakId());
            UserEntity saved = userRepository.save(tenant, entity);
            return userRepository.findById(tenant, saved.getId());
        } else if (loggedUser.getUserType() == UserType.PROFISSIONAL) {
            if (entity.getUserType() == UserType.PACIENTE) {
                // Paciente que já tem cadastro (atendido por outro profissional): o cadastro é
                // compartilhado, então em vez de recusar o CPF o paciente passa a estar
                // associado também a este profissional. O prontuário de cada um continua separado.
                UserEntity existing = findByCpf(tenant, entity.getCpf());
                if (existing != null) {
                    if (existing.getUserType() != UserType.PACIENTE) {
                        throw core.error.BusinessException.conflict("Este CPF pertence a um usuário que não é paciente.");
                    }
                    access.link(tenant, existing.getId(), loggedUser.getId());
                    return existing;
                }
                Set<String> roles = Set.of("PACIENTE_ROLE");
                String userId = keycloakService.createUser(tenant, entity, roles);
                entity.setKeycloakId(userId);
                entity.setRegisteredByKeycloakId(loggedUser.getKeycloakId());
                UserEntity saved = userRepository.save(tenant, entity);
                return userRepository.findById(tenant, saved.getId());
            }
            throw new SecurityException("Profissionais só podem criar pacientes.");
        }
        throw new SecurityException("Sem permissão para criar usuário.");
    }

    private UserEntity findByCpf(String tenant, String cpf) {
        if (cpf == null || cpf.isBlank()) return null;
        Filter filter = new Filter();
        filter.setFilterParams(new java.util.ArrayList<>(List.of(new core.service.model.FilterParam("cpf", cpf))));
        List<UserEntity> found = userRepository.filteredFindPaged(tenant, filter, 0, 1);
        return found.isEmpty() ? null : found.getFirst();
    }

    /** Dados que o próprio usuário (ou quem o cadastrou) pode alterar. */
    private static void copyProfile(UserEntity target, UserEntity source) {
        target.setFirstName(source.getFirstName());
        target.setLastName(source.getLastName());
        target.setEmail(source.getEmail());
        target.setPhoneNumber(source.getPhoneNumber());
        target.setBirthDate(source.getBirthDate());
        target.setProfileImage(source.getProfileImage());
        target.setCrp(source.getCrp());
        target.setAddress(source.getAddress());
        target.setOccupation(source.getOccupation());
        target.setEmergencyContactName(source.getEmergencyContactName());
        target.setEmergencyContactPhone(source.getEmergencyContactPhone());
        target.setGuardianName(source.getGuardianName());
        target.setGuardianPhone(source.getGuardianPhone());
    }

    @Override
    public UserEntity update(String tenant, UserEntity loggedUser, UserEntity entity) {
        UserEntity original = userRepository.findById(tenant, entity.getId());
        if (original == null) {
            throw new IllegalArgumentException("Usuário não encontrado.");
        }
        if (loggedUser.getUserType() == UserType.ADMINISTRADOR) {
            Set<String> roles = switch (entity.getUserType()) {
                case PROFISSIONAL -> Set.of("PROFISSIONAL_ROLE");
                case PACIENTE  -> Set.of("PACIENTE_ROLE");
                case ADMINISTRADOR  -> Set.of("ADMINISTRADOR_ROLE");
            };
            // Sobre o registro gravado: gravar a entidade da requisição desvinculava a conta
            // do Mercado Pago do usuário (relação com orphanRemoval que não vem do formulário).
            copyProfile(original, entity);
            original.setCpf(entity.getCpf());
            original.setUserType(entity.getUserType());
            UserEntity saved = userRepository.update(tenant, original);
            keycloakService.updateUser(tenant, original.getKeycloakId(), saved, roles);
            return userRepository.findById(tenant, saved.getId());
        } else if (loggedUser.getUserType() == UserType.PROFISSIONAL) {
            boolean self = original.getId().equals(loggedUser.getId());
            boolean myPatient = original.getUserType() == UserType.PACIENTE && access.isAssociated(tenant, loggedUser, original);
            if (self || myPatient) {
                copyProfile(original, entity);
                return userRepository.update(tenant, original);
            }
            throw new SecurityException("Profissionais só podem editar seus próprios dados ou de seus pacientes.");
        } else if (loggedUser.getUserType() == UserType.PACIENTE) {
            if (original.getKeycloakId().equals(loggedUser.getKeycloakId()) || original.getRegisteredByKeycloakId().equals(loggedUser.getKeycloakId())) {
                copyProfile(original, entity);
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
        } else if (loggedUser.getUserType() == UserType.PROFISSIONAL) {
            // A mesma regra da listagem: quem aparece na lista pode ser aberto.
            Filter byId = new Filter();
            byId.setFilterParams(new java.util.ArrayList<>(List.of(new core.service.model.FilterParam("id", id.toString()))));
            if (userRepository.countFilteredComPermissao(tenant, loggedUser, byId) > 0) {
                return user;
            }
            throw new SecurityException("Profissional só pode ver a si mesmo e seus pacientes.");
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
        // Prontuário dentro do prazo de guarda não pode ser apagado junto com o cadastro.
        records.requireDisposable(tenant, id);
        if (loggedUser.getUserType() == UserType.ADMINISTRADOR) {
            userRepository.delete(tenant, id);
            keycloakService.deleteUser(tenant, UUID.fromString(user.getKeycloakId()));
        } else if (loggedUser.getUserType() == UserType.PROFISSIONAL) {
            boolean registeredByMe = loggedUser.getKeycloakId().equals(user.getRegisteredByKeycloakId());
            if (registeredByMe && user.getUserType() == UserType.PACIENTE) {
                // Cadastro compartilhado: não some para os outros profissionais que atendem o paciente.
                if (access.otherProfessionals(tenant, id, loggedUser.getId()) > 0) {
                    throw core.error.BusinessException.conflict("Este paciente também é atendido por outro "
                            + "profissional. O cadastro é compartilhado e não pode ser excluído por você.");
                }
                userRepository.delete(tenant, id);
                return;
            }
            throw new SecurityException("Profissional só pode gerenciar a sí mesmo e seus pacientes.");
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

    @Override
    public UserEntity findByKeycloakId(String tenant, String keycloakId) {
        UserEntity user = keycloakService.findByKeycloakId(tenant, keycloakId);
        String photo = access.photo(tenant, user.getId());
        if (!photo.isEmpty()) user.setProfileImage(photo);
        return user;
    }
}
