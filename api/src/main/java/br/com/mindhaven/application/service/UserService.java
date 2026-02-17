package br.com.mindhaven.application.service;

import br.com.mindhaven.application.usecase.interfaces.MinioUseCase;
import br.com.mindhaven.application.usecase.interfaces.UserUseCase;
import br.com.mindhaven.domain.entity.UserEntity;
import core.service.AbstractCrudService;
import core.service.model.Filter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class UserService extends AbstractCrudService<UserEntity> {
    @Inject
    UserUseCase userUseCase;

    @Inject
    MinioUseCase minioUseCase;

    public UserService() {
        super(UserEntity.class);
    }

    public UserEntity save(UserEntity entity) {
        userUseCase.save(getTenant(), getCurrentLoggedUser(), entity);

        try {
            String fileName = getTenant() + "/user/" + entity.getId() + "/user_profile.png";
            byte[] image = minioUseCase.getObjectBytes(fileName);
            if (image != null && image.length > 0) {
                minioUseCase.deleteObject(fileName);
            }
            minioUseCase.uploadBase64(fileName, entity.getProfileImage(), "image/png");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return entity;
    }

    public UserEntity update(UserEntity entity) {
        userUseCase.update(getTenant(), getCurrentLoggedUser(), entity);

        try {
            String fileName = getTenant() + "/user/" + entity.getId() + "/user_profile.png";
            byte[] image = minioUseCase.getObjectBytes(fileName);
            if (image != null && image.length > 0) {
                minioUseCase.deleteObject(fileName);
            }
            minioUseCase.uploadBase64(fileName, entity.getProfileImage(), "image/png");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return entity;
    }

    public UserEntity findById(UUID id) {
        UserEntity user = userUseCase.findById(getTenant(), getCurrentLoggedUser(), id);
        user.setProfileImage(includeImage(getTenant() + "/user/" + user.getId() + "/user_profile"));
        return user;
    }

    public void delete(UUID id) {
        userUseCase.delete(getTenant(), getCurrentLoggedUser(), id);
        minioUseCase.deleteObject(getTenant() + "/user/" + id + "/user_profile.png");
    }

    public List<UserEntity> filteredFindPaged(Filter filter, int page, int size) {
        List<UserEntity> users = userUseCase.filteredFindPaged(getTenant(), getCurrentLoggedUser(), filter, page, size);
        for (UserEntity user : users) {
            user.setProfileImage(includeImage(getTenant() + "/user/" + user.getId() + "/user_profile"));
        }
        return users;
    }

    public long countFiltered(Filter filter) {
        return userUseCase.countFiltered(getTenant(), getCurrentLoggedUser(), filter);
    }

    public UserEntity findByKeycloakId(String tenant, String keycloakId) {
        return userUseCase.findByKeycloakId(tenant, keycloakId);
    }

    private String includeImage(String fileName) {
        byte[] image = minioUseCase.getObjectBytes(fileName + ".png");
        if (image != null) {
            return java.util.Base64.getEncoder().encodeToString(image);
        }
        return "";
    }
}
