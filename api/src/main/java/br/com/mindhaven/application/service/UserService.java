package br.com.mindhaven.application.service;

import br.com.mindhaven.application.usecase.interfaces.MinioUseCase;
import br.com.mindhaven.application.usecase.interfaces.UserUseCase;
import br.com.mindhaven.domain.entity.UserEntity;
import core.service.AbstractCrudService;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@ApplicationScoped
public class UserService extends AbstractCrudService<UserEntity> {

    private static final Logger LOG = Logger.getLogger(UserService.class);

    @Inject
    UserUseCase userUseCase;

    // A foto de perfil mora no banco: o serviço de arquivos (MinIO) deixou de ser necessário
    // para o cadastro funcionar.
    @Inject
    PatientAccess access;

    public UserService() {
        super(UserEntity.class);
    }

    /**
     * Duas correções aqui, além da simplificação do acesso ao MinIO:
     *
     * <ol>
     *   <li>{@code userUseCase.save} devolve a entidade gerenciada — que é quem carrega o id
     *       gerado. O código antigo ignorava esse retorno e usava a instância recebida, cujo
     *       id continua nulo depois do {@code merge}. Resultado: a foto de todo usuário novo
     *       era gravada em {@code .../user/null/user_profile.png}, uma sobrescrevendo a outra.</li>
     *   <li>O upload era precedido de um GET (baixando a imagem inteira só para saber se ela
     *       existia) e de um DELETE. {@code putObject} já sobrescreve, então os dois eram
     *       round-trips desperdiçados.</li>
     * </ol>
     */
    public UserEntity save(UserEntity entity) {
        UserEntity saved = userUseCase.save(getTenant(), getCurrentLoggedUser(), entity);
        access.savePhoto(getTenant(), saved.getId(), entity.getProfileImage());
        saved.setProfileImage(access.photo(getTenant(), saved.getId()));
        return saved;
    }

    public UserEntity update(UserEntity entity) {
        UserEntity saved = userUseCase.update(getTenant(), getCurrentLoggedUser(), entity);
        access.savePhoto(getTenant(), saved.getId(), entity.getProfileImage());
        saved.setProfileImage(access.photo(getTenant(), saved.getId()));
        return saved;
    }

    public UserEntity findById(UUID id) {
        UserEntity user = userUseCase.findById(getTenant(), getCurrentLoggedUser(), id);
        user.setProfileImage(access.photo(getTenant(), user.getId()));
        return user;
    }

    public void delete(UUID id) {
        userUseCase.delete(getTenant(), getCurrentLoggedUser(), id);
    }

    /** As fotos da página vêm numa consulta só. */
    public List<UserEntity> filteredFindPaged(Filter filter, int page, int size) {
        List<UserEntity> users = userUseCase.filteredFindPaged(getTenant(), getCurrentLoggedUser(), filter, page, size);
        if (users.isEmpty()) return users;
        Map<UUID, String> photos = access.photos(getTenant(), users.stream().map(UserEntity::getId).toList());
        for (UserEntity user : users) {
            user.setProfileImage(photos.getOrDefault(user.getId(), ""));
        }
        return users;
    }

    public long countFiltered(Filter filter) {
        return userUseCase.countFiltered(getTenant(), getCurrentLoggedUser(), filter);
    }

    public UserEntity findByKeycloakId(String tenant, String keycloakId) {
        return userUseCase.findByKeycloakId(tenant, keycloakId);
    }
}
