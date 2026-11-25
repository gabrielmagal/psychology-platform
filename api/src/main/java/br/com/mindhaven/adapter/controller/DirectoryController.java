package br.com.mindhaven.adapter.controller;

import br.com.mindhaven.application.service.ProfessionalDirectory;
import br.com.mindhaven.application.service.UserService;
import br.com.mindhaven.domain.entity.UserEntity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Guia de profissionais: perfis públicos, avaliações recebidas e moderação dos comentários. */
@Path("/directory")
public class DirectoryController {
    @Inject
    ProfessionalDirectory directory;

    @Inject
    UserService userService;

    @GET
    @Path("/professionals")
    public List<Map<String, Object>> list(@QueryParam("search") String search) {
        return directory.list(tenant(), me(), search);
    }

    @GET
    @Path("/professionals/{id}")
    public Map<String, Object> view(@PathParam("id") UUID id) {
        return directory.view(tenant(), me(), id);
    }

    @GET
    @Path("/profile")
    public Map<String, Object> mine() {
        return directory.mine(tenant(), me());
    }

    @PUT
    @Path("/profile")
    public Map<String, Object> saveMine(Map<String, Object> body) {
        return directory.saveMine(tenant(), me(), body);
    }

    @GET
    @Path("/reviews")
    public Map<String, Object> received(@QueryParam("professionalId") UUID professionalId) {
        return directory.received(tenant(), me(), professionalId);
    }

    @GET
    @Path("/reviews/moderation")
    public List<Map<String, Object>> moderationQueue() {
        return directory.moderationQueue(tenant(), me());
    }

    @PUT
    @Path("/reviews/{id}/publish")
    public void publish(@PathParam("id") UUID id) {
        directory.moderate(tenant(), me(), id, true);
    }

    @PUT
    @Path("/reviews/{id}/reject")
    public void reject(@PathParam("id") UUID id) {
        directory.moderate(tenant(), me(), id, false);
    }

    private String tenant() {
        return userService.getTenant();
    }

    private UserEntity me() {
        UserEntity user = userService.getCurrentLoggedUser();
        if (user == null) throw new SecurityException("Usuário logado não está cadastrado na plataforma.");
        return user;
    }
}
