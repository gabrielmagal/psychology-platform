package core.service;

import br.com.mindhaven.domain.entity.UserEntity;
import jakarta.enterprise.context.RequestScoped;

import java.util.function.Supplier;

/**
 * Memoriza o usuário autenticado pelo tempo de vida da requisição.
 *
 * <p>Resolver o usuário logado custa uma consulta ao banco e um GET no MinIO (foto de
 * perfil em base64). Os serviços chamam {@code getCurrentLoggedUser()} em quase toda
 * operação — às vezes mais de uma vez no mesmo endpoint — então sem memoização esse custo
 * é multiplicado à toa.
 */
@RequestScoped
public class RequestScopedUserCache {

    private UserEntity user;

    public UserEntity get(Supplier<UserEntity> loader) {
        if (user == null) {
            user = loader.get();
        }
        return user;
    }
}
