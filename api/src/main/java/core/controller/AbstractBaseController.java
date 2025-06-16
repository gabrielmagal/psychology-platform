package core.controller;

import br.com.psicologia.mapper.BaseMapper;
import core.service.model.Filter;
import core.service.model.FilterParam;
import core.controller.dto.BaseDto;
import jakarta.ws.rs.*;
import core.repository.model.BaseEntity;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import core.service.AbstractCrudService;

import javax.management.relation.InvalidRoleValueException;
import java.util.*;

public abstract class AbstractBaseController<T extends BaseDto, E extends BaseEntity> {

    protected final AbstractCrudService<E> service;
    protected final BaseMapper<T, E> mapper;

    @Context
    public HttpHeaders headers;

    public AbstractBaseController(AbstractCrudService<E> service, BaseMapper<T, E> mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GET
    @Path("/{id}")
    public T findById(@Context SecurityContext securityContext, @PathParam("id") UUID id) {
        E entity = service.findById(securityContext, headers.getHeaderString("Tenant"), id);
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
                                            @QueryParam("size") @DefaultValue("10") int size) throws InvalidRoleValueException {
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

        List<E> entities = service.filteredFindPaged(securityContext, tenant, filter, page, size);
        List<T> dtos = mapper.toDtoList(entities);
        long total = service.countFiltered(securityContext, tenant, filter);

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
        E savedEntity = service.save(securityContext, headers.getHeaderString("Tenant"), entity);
        if (savedEntity != null) {
            return mapper.toDto(savedEntity);
        } else {
            throw new RuntimeException("Erro ao salvar.");
        }
    }

    @PUT
    @Path("/{id}")
    public T update(@Context SecurityContext securityContext, @PathParam("id") UUID id, @RequestBody T dto) {
        E existingEntity = service.findById(securityContext, headers.getHeaderString("Tenant"), id);
        if (existingEntity != null) {
            E entityToUpdate = mapper.toEntity(dto);
            //entityToUpdate.setId(id); // garantir ID consistente
            E updatedEntity = service.update(securityContext, headers.getHeaderString("Tenant"), entityToUpdate);
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
        E existingEntity = service.findById(securityContext, headers.getHeaderString("Tenant"), id);
        if (existingEntity != null) {
            service.delete(securityContext, headers.getHeaderString("Tenant"), id);
        } else {
            throw new NotFoundException();
        }
    }
}
