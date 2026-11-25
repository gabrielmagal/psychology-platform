package br.com.mindhaven.application.service;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Instrumentos prontos: escalas de rastreio de uso livre (não são testes psicológicos
 * privativos e não dependem de aprovação no SATEPSI). O texto dos itens segue as traduções
 * em circulação no Brasil; o profissional deve conferi-lo com a versão validada que adota.
 *
 * <p>Formato da definição (o mesmo dos instrumentos criados pelo profissional):
 * <pre>{ title, description, instructions, options: [{value, label}],
 *   items: [{id, text, type: SCALE | TEXT | YESNO}], bands: [{min, max, label}],
 *   alertItems: [id], maxScore }</pre>
 */
public final class Instruments {
    public static final String SCALE = "SCALE";
    public static final String TEXT = "TEXT";
    public static final String YESNO = "YESNO";

    private static final Map<String, JsonObject> BUILT_IN = new LinkedHashMap<>();

    static {
        JsonArray frequency = options("Nenhuma vez", "Vários dias", "Mais da metade dos dias", "Quase todos os dias");

        BUILT_IN.put("PHQ9", new JsonObject()
                .put("title", "PHQ-9 — Questionário sobre a saúde do paciente")
                .put("description", "Rastreio de sintomas depressivos nas últimas duas semanas. 9 itens, de 0 a 27 pontos.")
                .put("instructions", "Durante as últimas 2 semanas, com que frequência você foi incomodado(a) por qualquer um dos problemas abaixo?")
                .put("options", frequency)
                .put("items", scaleItems(
                        "Pouco interesse ou pouco prazer em fazer as coisas",
                        "Se sentir \"para baixo\", deprimido(a) ou sem perspectiva",
                        "Dificuldade para pegar no sono ou permanecer dormindo, ou dormir mais do que de costume",
                        "Se sentir cansado(a) ou com pouca energia",
                        "Falta de apetite ou comendo demais",
                        "Se sentir mal consigo mesmo(a), ou achar que você é um fracasso ou que decepcionou sua família ou você mesmo(a)",
                        "Dificuldade para se concentrar nas coisas, como ler o jornal ou ver televisão",
                        "Lentidão para se movimentar ou falar, a ponto de as outras pessoas perceberem; ou o oposto: estar tão agitado(a) "
                                + "ou inquieto(a) que você fica andando de um lado para o outro muito mais do que de costume",
                        "Pensar em se ferir de alguma maneira ou que seria melhor estar morto(a)"))
                .put("bands", bands(0, 4, "Mínimo", 5, 9, "Leve", 10, 14, "Moderado", 15, 19, "Moderadamente grave", 20, 27, "Grave"))
                .put("alertItems", new JsonArray().add("9"))
                .put("maxScore", 27));

        BUILT_IN.put("GAD7", new JsonObject()
                .put("title", "GAD-7 — Escala de ansiedade generalizada")
                .put("description", "Rastreio de sintomas de ansiedade nas últimas duas semanas. 7 itens, de 0 a 21 pontos.")
                .put("instructions", "Durante as últimas 2 semanas, com que frequência você foi incomodado(a) pelos problemas abaixo?")
                .put("options", frequency)
                .put("items", scaleItems(
                        "Sentir-se nervoso(a), ansioso(a) ou muito tenso(a)",
                        "Não ser capaz de impedir ou de controlar as preocupações",
                        "Preocupar-se muito com diversas coisas",
                        "Dificuldade para relaxar",
                        "Ficar tão agitado(a) que se torna difícil permanecer sentado(a)",
                        "Ficar facilmente aborrecido(a) ou irritado(a)",
                        "Sentir medo como se algo horrível fosse acontecer"))
                .put("bands", bands(0, 4, "Mínima", 5, 9, "Leve", 10, 14, "Moderada", 15, 21, "Grave"))
                .put("alertItems", new JsonArray())
                .put("maxScore", 21));

        BUILT_IN.put("WHO5", new JsonObject()
                .put("title", "WHO-5 — Índice de bem-estar")
                .put("description", "Bem-estar subjetivo nas últimas duas semanas. 5 itens, de 0 a 25 pontos; quanto maior, melhor.")
                .put("instructions", "Indique, para cada afirmação, a que mais se aproxima de como você se sentiu nas últimas duas semanas.")
                .put("options", options("Nunca", "Algumas vezes", "Menos da metade do tempo", "Mais da metade do tempo",
                        "A maior parte do tempo", "O tempo todo"))
                .put("items", scaleItems(
                        "Senti-me alegre e bem-disposto(a)",
                        "Senti-me calmo(a) e tranquilo(a)",
                        "Senti-me ativo(a) e com energia",
                        "Acordei me sentindo descansado(a) e revigorado(a)",
                        "Meu dia a dia tem sido preenchido com coisas que me interessam"))
                .put("bands", bands(0, 12, "Bem-estar baixo", 13, 25, "Bem-estar preservado"))
                .put("alertItems", new JsonArray())
                .put("maxScore", 25));
    }

    private Instruments() {
    }

    public static Map<String, JsonObject> builtIn() {
        return BUILT_IN;
    }

    /** A faixa em que a pontuação cai, ou {@code null} se o instrumento não tem faixas. */
    public static String band(JsonObject definition, Integer score) {
        if (score == null) return null;
        JsonArray bands = definition.getJsonArray("bands", new JsonArray());
        for (int i = 0; i < bands.size(); i++) {
            JsonObject band = bands.getJsonObject(i);
            if (score >= band.getInteger("min") && score <= band.getInteger("max")) return band.getString("label");
        }
        return null;
    }

    private static JsonArray options(String... labels) {
        JsonArray options = new JsonArray();
        for (int i = 0; i < labels.length; i++) options.add(new JsonObject().put("value", i).put("label", labels[i]));
        return options;
    }

    private static JsonArray scaleItems(String... texts) {
        JsonArray items = new JsonArray();
        for (int i = 0; i < texts.length; i++) {
            items.add(new JsonObject().put("id", String.valueOf(i + 1)).put("text", texts[i]).put("type", SCALE));
        }
        return items;
    }

    private static JsonArray bands(Object... triples) {
        JsonArray bands = new JsonArray();
        for (int i = 0; i < triples.length; i += 3) {
            bands.add(new JsonObject().put("min", triples[i]).put("max", triples[i + 1]).put("label", triples[i + 2]));
        }
        return bands;
    }

    static final List<String> TYPES = List.of(SCALE, TEXT, YESNO);
}
