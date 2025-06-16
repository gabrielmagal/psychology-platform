package core.controller;

import br.com.psicologia.mapper.BaseMapper;
import core.context.IContext;
import core.controller.dto.BaseDto;
import core.repository.model.BaseEntity;
import core.service.AbstractEntityDescriptionService;
import core.service.model.Filter;
import core.service.model.FilterParam;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.util.*;

public abstract class AbstractBaseContextController<T extends BaseDto, E extends BaseEntity> extends AbstractEntityDescriptionService {

    protected final IContext<E> iContext;
    protected final BaseMapper<T, E> mapper;

    @Context
    public HttpHeaders headers;

    public AbstractBaseContextController(IContext<E> iContext, BaseMapper<T, E> mapper) {
        this.iContext = iContext;
        this.mapper = mapper;
    }

    @GET
    @Path("/{id}")
    public T findById(@Context SecurityContext securityContext, @PathParam("id") UUID id) {
        E entity = iContext.findById(securityContext, headers.getHeaderString("Tenant"), id);
        if (entity != null) {
            return mapper.toDto(entity);
        } else {
            throw new NotFoundException();
        }
    }

    @GET
    public Map<String, Object> findAllPaged(@Context SecurityContext securityContext,
                                            @Context UriInfo uriInfo,
                                            @QueryParam("page") @DefaultValue("0") int page,
                                            @QueryParam("size") @DefaultValue("10") int size) {
        String tenant = headers.getHeaderString("Tenant");

        Filter filter = new Filter();
        List<FilterParam> filterParams = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            String key = entry.getKey();
            if (!Set.of("page", "size").contains(key)) {
                filterParams.add(new FilterParam(key, entry.getValue().getFirst()));
            }
        }

        filter.setFilterParams(filterParams);
        filter.setSortingParams(Collections.emptyList());

        List<E> entities = iContext.filteredFindPaged(securityContext, tenant, filter, page, size);
        List<T> dtos = mapper.toDtoList(entities);
        long total = iContext.countFiltered(securityContext, tenant, filter);

        Map<String, Object> response = new HashMap<>();
        response.put("content", dtos);
        response.put("totalElements", total);
        response.put("page", page);
        response.put("size", size);

        return response;
    }

    @POST
    public T save(@Context SecurityContext securityContext, @RequestBody T dto) {
        E entity = mapper.toEntity(dto);
        E savedEntity = iContext.save(securityContext, headers.getHeaderString("Tenant"), entity);
        if (savedEntity != null) {
            return mapper.toDto(savedEntity);
        } else {
            throw new RuntimeException("Erro ao salvar.");
        }
    }

    @PUT
    @Path("/{id}")
    public T update(@Context SecurityContext securityContext, @PathParam("id") UUID id, @RequestBody T dto) {
        E existingEntity = iContext.findById(securityContext, headers.getHeaderString("Tenant"), id);
        if (existingEntity != null) {
            E entityToUpdate = mapper.toEntity(dto);
            //entityToUpdate.setId(id); // garantir ID consistente
            E updatedEntity = iContext.update(securityContext, headers.getHeaderString("Tenant"), entityToUpdate);
            if (updatedEntity != null) {
                return mapper.toDto(updatedEntity);
            } else {
                throw new RuntimeException("Erro ao atualizar.");
            }
        } else {
            throw new NotFoundException();
        }
    }

    @DELETE
    @Path("/{id}")
    public void delete(@Context SecurityContext securityContext, @PathParam("id") UUID id) {
        E existingEntity = iContext.findById(securityContext, headers.getHeaderString("Tenant"), id);
        if (existingEntity != null) {
            iContext.delete(securityContext, headers.getHeaderString("Tenant"), id);
        } else {
            throw new NotFoundException();
        }
    }
}
