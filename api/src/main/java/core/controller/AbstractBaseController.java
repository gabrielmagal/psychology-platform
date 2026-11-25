package core.controller;

import br.com.mindhaven.mapper.BaseMapper;
import core.service.AbstractEntityDescriptionService;
import core.service.model.Filter;
import core.service.model.FilterParam;
import core.controller.dto.BaseDto;
import jakarta.ws.rs.*;
import core.repository.model.BaseEntity;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import core.service.AbstractCrudService;

import javax.management.relation.InvalidRoleValueException;
import java.util.*;

public abstract class AbstractBaseController<T extends BaseDto, E extends BaseEntity> extends AbstractEntityDescriptionService {

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
    public T findById(@PathParam("id") UUID id) {
        E entity = service.findById(id);
        if (entity != null) {
            return mapper.toDto(entity);
        } else {
            throw new NotFoundException();
        }
    }

    @GET
    public Map<String, Object> findAllPaged(@Context UriInfo uriInfo,
                                            @QueryParam("page") @DefaultValue("0") int page,
                                            @QueryParam("size") @DefaultValue("10") int size) throws InvalidRoleValueException {
        String tenant = headers.getHeaderString("Tenant");

        Filter filter = new Filter();
        List<FilterParam> filterParams = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            String key = entry.getKey();
            if (!Set.of("page", "size", "sort").contains(key)) {
                filterParams.add(new FilterParam(key, entry.getValue().getFirst()));
            }
        }

        filter.setFilterParams(filterParams);
        // ?sort=campo,desc — a ordem da listagem. Sem ela a ordem é pelo id (ver GenericDao).
        String sort = uriInfo.getQueryParameters().getFirst("sort");
        if (sort == null || sort.isBlank()) {
            filter.setSortingParams(Collections.emptyList());
        } else {
            String[] parts = sort.split(",");
            filter.setSortingParams(List.of(new core.service.model.SortingParams(
                    parts[0].trim(), parts.length > 1 ? parts[1].trim() : "asc", 0)));
        }

        List<E> entities = service.filteredFindPaged(filter, page, size);
        List<T> dtos = mapper.toDtoList(entities);
        long total = service.countFiltered(filter);

        Map<String, Object> response = new HashMap<>();
        response.put("content", dtos);
        response.put("totalElements", total);
        response.put("page", page);
        response.put("size", size);

        return response;
    }


    @POST
    public T save(@RequestBody T dto) {
        validate(dto);
        E entity = mapper.toEntity(dto);
        E savedEntity = service.save(entity);
        if (savedEntity != null) {
            return mapper.toDto(savedEntity);
        } else {
            throw new RuntimeException("Erro ao salvar.");
        }
    }

    @PUT
    @Path("/{id}")
    public T update(@PathParam("id") UUID id, @RequestBody T dto) {
        E existingEntity = service.findById(id);
        if (existingEntity != null) {
            validate(dto);
            dto.setId(id);
            E entityToUpdate = mapper.toEntity(dto);
            E updatedEntity = service.update(entityToUpdate);
            if (updatedEntity != null) {
                return mapper.toDto(updatedEntity);
            } else {
                throw new RuntimeException("Erro ao atualizar.");
            }
        } else {
            throw new NotFoundException();
        }
    }

    /** Mesmas regras do formulário, conferidas no servidor antes de qualquer gravação. */
    private void validate(T dto) {
        List<String> problems = core.validation.DtoValidator.problems(dto);
        if (!problems.isEmpty()) {
            throw core.error.BusinessException.invalid(String.join(" ", problems));
        }
    }

    @DELETE
    @Path("/{id}")
    public void delete(@PathParam("id") UUID id) {
        E existingEntity = service.findById(id);
        if (existingEntity != null) {
            service.delete(id);
        } else {
            throw new NotFoundException();
        }
    }
}
