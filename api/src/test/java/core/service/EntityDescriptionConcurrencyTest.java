package core.service;

import br.com.mindhaven.adapter.controller.dto.SessionPackageDto;
import br.com.mindhaven.adapter.controller.dto.UserDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O estado de controle de ciclos era um campo de instância compartilhado entre requisições.
 * Estes testes fixam o comportamento correto sob concorrência.
 */
class EntityDescriptionConcurrencyTest {

    /** A classe é abstrata porque serve de base para controllers/serviços. */
    static class Describer extends AbstractEntityDescriptionService {
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Map<String, Object> description) {
        return (Map<String, Object>) description.get("properties");
    }

    @Test
    @DisplayName("descreve o DTO com todas as propriedades")
    void describesAllProperties() {
        Map<String, Object> description = new Describer().describeEntity(UserDto.class);

        assertEquals("UserDto", description.get("type"));
        assertTrue(properties(description).containsKey("firstName"));
        assertTrue(properties(description).containsKey("userType"));
    }

    @Test
    @DisplayName("chamadas concorrentes nunca devolvem uma descrição vazia")
    void concurrentCallsNeverReturnEmptyProperties() throws Exception {
        Describer describer = new Describer();

        // Antes, duas threads descrevendo a mesma classe disputavam o mesmo Set de
        // "visitadas": a que perdesse a corrida via a classe como já visitada e recebia
        // properties: {} — ou seja, um formulário vazio na tela do usuário.
        List<Callable<Map<String, Object>>> tasks = IntStream.range(0, 64)
                .<Callable<Map<String, Object>>>mapToObj(i -> () ->
                        describer.describeEntity(i % 2 == 0 ? UserDto.class : SessionPackageDto.class))
                .toList();

        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            List<Future<Map<String, Object>>> results = pool.invokeAll(tasks);
            for (Future<Map<String, Object>> result : results) {
                assertFalse(properties(result.get()).isEmpty(),
                        "descrição concorrente veio vazia");
            }
        }
    }

    @Test
    @DisplayName("a descrição é reaproveitada entre chamadas e entre instâncias")
    void descriptionIsCached() {
        Map<String, Object> first = new Describer().describeEntity(SessionPackageDto.class);
        Map<String, Object> second = new Describer().describeEntity(SessionPackageDto.class);

        assertSame(first, second, "a varredura reflexiva deveria acontecer uma vez só");
    }

    @Test
    @DisplayName("relacionamento aninhado é descrito, não truncado")
    void describesNestedRelationship() {
        Map<String, Object> description = new Describer().describeEntity(SessionPackageDto.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> patient = (Map<String, Object>) properties(description).get("patient");

        assertEquals(Boolean.TRUE, patient.get("manyToOne"));
        assertEquals("user", patient.get("relatedType"));
    }
}
