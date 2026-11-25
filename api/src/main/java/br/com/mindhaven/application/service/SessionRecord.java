package br.com.mindhaven.application.service;

import br.com.mindhaven.domain.entity.AnnotationEntity;
import br.com.mindhaven.domain.entity.SessionEntity;
import core.error.BusinessException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registro clínico de uma sessão, no formato pedido pela Resolução CFP 001/2009: a demanda,
 * o que foi observado, os procedimentos usados, a evolução e o encaminhamento.
 *
 * <p>Substitui três coisas que se sobrepunham — o "resumo", a "evolução" em texto solto e uma
 * lista de "anotações" com campos como "pensamento dominante". O que já estava escrito nesses
 * lugares não se perde: aparece dentro dos campos novos e passa a morar neles no primeiro
 * salvamento.
 *
 * <p>As notas privadas ficam de fora do prontuário de propósito: o paciente tem direito de
 * acesso ao prontuário, e a anotação pessoal do profissional não faz parte dele.
 */
public final class SessionRecord {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int MAX = 20_000;

    private SessionRecord() {
    }

    /** O registro como é lido: campos novos mais o conteúdo antigo ainda não convertido. */
    public static Map<String, Object> read(SessionEntity s) {
        String demand = s.getDemand();
        String observations = s.getObservations();
        String interventions = s.getInterventions();
        String evolution = join(s.getEvolution(), s.getSummary());
        for (AnnotationEntity a : s.getAnnotation() == null ? java.util.List.<AnnotationEntity>of() : s.getAnnotation()) {
            demand = join(demand, a.getSignificantEvents());
            observations = join(observations, labelled("Sentimento principal", a.getMainFeeling()));
            observations = join(observations, labelled("Pensamento dominante", a.getDominantThought()));
            observations = join(observations, labelled("Fase atual", a.getCurrentPhase()));
            interventions = join(interventions, a.getIntervention());
        }
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("demand", demand);
        record.put("observations", observations);
        record.put("interventions", interventions);
        record.put("evolution", evolution);
        record.put("plan", s.getPlan());
        record.put("technicalIssues", s.getTechnicalIssues());
        record.put("riskAlert", Boolean.TRUE.equals(s.getRiskAlert()));
        record.put("addenda", s.getAddenda());
        record.put("signedAt", s.getRecordSignedAt() == null ? null : s.getRecordSignedAt().toString());
        return record;
    }

    public static boolean isEmpty(SessionEntity s) {
        Map<String, Object> record = read(s);
        return record.get("demand") == null && record.get("observations") == null
                && record.get("interventions") == null && record.get("evolution") == null && record.get("plan") == null;
    }

    /** Grava o registro. O conteúdo antigo, já trazido para os campos novos na leitura, é descartado. */
    public static void write(SessionEntity s, Map<String, Object> body) {
        requireOpen(s);
        s.setDemand(text(body.get("demand")));
        s.setObservations(text(body.get("observations")));
        s.setInterventions(text(body.get("interventions")));
        s.setEvolution(text(body.get("evolution")));
        s.setPlan(text(body.get("plan")));
        if (body.containsKey("technicalIssues")) s.setTechnicalIssues(text(body.get("technicalIssues")));
        s.setRiskAlert(Boolean.TRUE.equals(body.get("riskAlert")));
        if (body.containsKey("privateNotes")) s.setPrivateNotes(text(body.get("privateNotes")));
        s.setSummary(null);
        if (s.getAnnotation() != null) s.getAnnotation().clear();
    }

    /** Conclui o registro: a partir daqui ele não é mais alterado, só recebe adendos. */
    public static void sign(SessionEntity s, LocalDateTime now) {
        requireOpen(s);
        if (isEmpty(s)) throw BusinessException.invalid("Escreva o registro da sessão antes de concluí-lo.");
        // Fixa nos campos novos o que ainda estava no formato antigo, para o registro
        // concluído não depender de dados que podem ser limpos depois.
        Map<String, Object> current = new LinkedHashMap<>(read(s));
        current.remove("signedAt");
        write(s, current);
        s.setRecordSignedAt(now);
    }

    /** Acrescenta um adendo datado. É o único jeito de complementar um registro concluído. */
    public static void addendum(SessionEntity s, String text, LocalDateTime now) {
        if (s.getRecordSignedAt() == null) {
            throw BusinessException.conflict("O registro ainda está aberto: altere o próprio texto.");
        }
        String clean = text(text);
        if (clean == null) throw BusinessException.invalid("Escreva o adendo.");
        s.setAddenda(join(s.getAddenda(), "[" + now.format(STAMP) + "] " + clean));
    }

    private static void requireOpen(SessionEntity s) {
        if (s.getRecordSignedAt() != null) {
            throw BusinessException.conflict("Este registro já foi concluído. Para complementar, acrescente um adendo.");
        }
    }

    private static String labelled(String label, String value) {
        return value == null || value.isBlank() ? null : label + ": " + value.trim();
    }

    private static String join(String first, String second) {
        boolean noFirst = first == null || first.isBlank();
        boolean noSecond = second == null || second.isBlank();
        if (noFirst && noSecond) return null;
        if (noFirst) return second.trim();
        if (noSecond) return first.trim();
        return first.trim() + "\n\n" + second.trim();
    }

    private static String text(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        if (text.isEmpty()) return null;
        if (text.length() > MAX) throw BusinessException.invalid("Cada campo do registro pode ter até " + MAX + " caracteres.");
        return text;
    }
}
