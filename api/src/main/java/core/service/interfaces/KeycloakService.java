package core.service.interfaces;

import br.com.psicologia.model.UserEntity;

import java.util.Set;
import java.util.UUID;

public interface KeycloakService {
    String getAdminAccessToken();
    String createUser(String realm, UserEntity user, Set<String> roles);
    void updateUser(String realm, String userId, UserEntity user, Set<String> roles);
    UserEntity findByKeycloakId(String tenant, String keycloakId);
    void deleteUser(String realm, UUID keycloakUserId);
}
