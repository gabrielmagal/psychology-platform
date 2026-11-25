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

    @jakarta.inject.Inject
    br.com.mindhaven.application.service.PatientAccess access;

    @jakarta.inject.Inject
    br.com.mindhaven.domain.repository.UserRepository userRepository;

    public SessionPackageUseCaseImpl(SessionPackageRepository sessionPackageRepository) {
        this.sessionPackageRepository = sessionPackageRepository;
    }

    public SessionPackageEntity save(String tenant, UserEntity loggedUser, SessionPackageEntity entity) {
        return switch (loggedUser.getUserType()) {
            // O pacote é do profissional que atende; o administrador acompanha, não contrata.
            case ADMINISTRADOR -> throw new SecurityException(
                    "O pacote é criado pelo profissional que vai atender o paciente.");
            case PROFISSIONAL -> {
                // O formulário não tem campo de profissional: sem este padrão, nenhum profissional
                // conseguia criar pacote pela tela (o id chegava nulo e caía no erro abaixo).
                if (entity.getProfessionalId() == null) {
                    entity.setProfessionalId(loggedUser.getId());
                }
                if (entity.getProfessionalId().equals(loggedUser.getId())) {
                    // Só para paciente associado ao profissional: sem isto, bastaria conhecer um id
                    // para abrir pacote (e prontuário) de um paciente de outro profissional.
                    if (entity.getPatient() == null || entity.getPatient().getId() == null) {
                        throw core.error.BusinessException.invalid("Escolha o paciente do pacote.");
                    }
                    br.com.mindhaven.domain.entity.UserEntity patient = userRepository.findById(tenant, entity.getPatient().getId());
                    if (patient == null) throw core.error.BusinessException.invalid("Paciente não encontrado.");
                    access.requireAssociated(tenant, loggedUser, patient);
                    yield sessionPackageRepository.save(tenant, entity);
                } else {
                    throw new SecurityException("Profissional só pode criar pacotes para si mesmo.");
                }
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode criar pacotes de sessão.");
        };
    }

    /**
     * Altera só os dados do próprio pacote, sobre o registro já gravado.
     *
     * <p>Gravar a entidade vinda da requisição substituía as listas de sessões e de
     * pagamentos pelas listas vazias que chegam do formulário — e, como essas relações têm
     * {@code orphanRemoval}, <b>editar o título de um pacote apagava todas as sessões e
     * pagamentos dele</b>. O paciente do pacote também não muda na edição: o histórico de
     * sessões e pagamentos pertence a ele.
     */
    public SessionPackageEntity update(String tenant, UserEntity loggedUser, SessionPackageEntity entity) {
        SessionPackageEntity stored = entity.getId() == null ? null : sessionPackageRepository.findById(tenant, entity.getId());
        if (stored == null) {
            throw new IllegalArgumentException("Pacote de sessão não encontrado.");
        }
        switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> {
                if (entity.getProfessionalId() != null) {
                    stored.setProfessionalId(entity.getProfessionalId());
                }
            }
            case PROFISSIONAL -> {
                if (!loggedUser.getId().equals(stored.getProfessionalId())) {
                    throw new SecurityException("Profissional só pode atualizar pacotes próprios.");
                }
            }
            case PACIENTE -> throw new SecurityException("Paciente não pode atualizar pacotes de sessão.");
        }
        stored.setPackageTitle(entity.getPackageTitle());
        stored.setTotalSessions(entity.getTotalSessions());
        stored.setPrice(entity.getPrice());
        return sessionPackageRepository.update(tenant, stored);
    }

    public SessionPackageEntity findById(String tenant, UserEntity loggedUser, UUID id) {
        SessionPackageEntity pacote = sessionPackageRepository.findById(tenant, id);
        if (pacote == null) return null;
        return switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> pacote;
            case PROFISSIONAL -> {
                if (pacote.getProfessionalId() != null && pacote.getProfessionalId().equals(loggedUser.getId())) {
                    yield pacote;
                } else {
                    throw new SecurityException("Profissional só pode acessar pacotes próprios.");
                }
            }
            // O paciente enxerga (e paga) os próprios pacotes; criar, alterar e excluir seguem
            // restritos ao profissional.
            case PACIENTE -> {
                if (pacote.getPatient() != null && loggedUser.getId().equals(pacote.getPatient().getId())) {
                    yield pacote;
                }
                throw new SecurityException("Paciente só pode acessar os próprios pacotes.");
            }
        };
    }

    public void delete(String tenant, UserEntity loggedUser, UUID id) {
        SessionPackageEntity pacote = sessionPackageRepository.findById(tenant, id);
        if (pacote == null) throw new IllegalArgumentException("Pacote de sessão não encontrado.");
        switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> sessionPackageRepository.delete(tenant, id);
            case PROFISSIONAL -> {
                if (pacote.getProfessionalId() != null && pacote.getProfessionalId().equals(loggedUser.getId())) {
                    sessionPackageRepository.delete(tenant, id);
                } else {
                    throw new SecurityException("Profissional só pode deletar pacotes próprios.");
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
            case PROFISSIONAL -> {
                if (filter.getFilterParams() == null) {
                    filter.setFilterParams(new ArrayList<>());
                }
                filter.getFilterParams().add(new FilterParam("professionalId", loggedUser.getId().toString()));
                return sessionPackageRepository.filteredFindPaged(tenant, filter, page, size);
            }
            case PACIENTE -> {
                if (filter.getFilterParams() == null) {
                    filter.setFilterParams(new ArrayList<>());
                }
                filter.getFilterParams().add(new FilterParam("patient", loggedUser.getId().toString()));
                return sessionPackageRepository.filteredFindPaged(tenant, filter, page, size);
            }
            default -> throw new SecurityException("Tipo de usuário não suportado.");
        }
    }

    public long countFiltered(String tenant, UserEntity loggedUser, Filter filter) {
        switch (loggedUser.getUserType()) {
            case ADMINISTRADOR -> {
                return sessionPackageRepository.countFiltered(tenant, filter);
            }
            case PROFISSIONAL -> {
                if (filter.getFilterParams() == null) {
                    filter.setFilterParams(new ArrayList<>());
                }
                filter.getFilterParams().add(new FilterParam("professionalId", loggedUser.getId().toString()));
                return sessionPackageRepository.countFiltered(tenant, filter);
            }
            case PACIENTE -> {
                if (filter.getFilterParams() == null) {
                    filter.setFilterParams(new ArrayList<>());
                }
                filter.getFilterParams().add(new FilterParam("patient", loggedUser.getId().toString()));
                return sessionPackageRepository.countFiltered(tenant, filter);
            }
            default -> throw new SecurityException("Tipo de usuário não suportado.");
        }
    }
}
