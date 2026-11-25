package br.com.mindhaven.application.service;

import br.com.mindhaven.domain.entity.PatientTaskEntity;
import br.com.mindhaven.domain.entity.PatientTermEntity;
import br.com.mindhaven.domain.entity.SafetyPlanEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.UserType;
import core.error.BusinessException;
import core.repository.dao.GenericDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O que acontece entre as sessões e fica nas mãos do paciente: as tarefas combinadas, o
 * plano de segurança para crises e os termos que ele aceita pelo aplicativo.
 *
 * <p>Regra de acesso, igual à do prontuário: o profissional escreve para os pacientes que
 * atende e vê só o que é dele; o administrador lê tudo; o paciente vê o que é seu.
 */
@ApplicationScoped
@Transactional
public class CareService {
    public static final String ONLINE_CARE = "ATENDIMENTO_ONLINE";
    /** Versão do texto do termo de atendimento online. Mudou o texto, muda a versão. */
    public static final String ONLINE_CARE_VERSION = "1.0";

    private static final int MAX_TEXT = 5_000;

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    @Inject
    PatientAccess access;

    // ------------------------------------------------------------------ tarefas

    public List<Map<String, Object>> tasks(String tenant, UserEntity user, UUID patientId) {
        UUID target = target(tenant, user, patientId);
        boolean onlyMine = user.getUserType() == UserType.PROFISSIONAL;
        var query = em.createQuery("select t from PatientTaskEntity t where t.patientId = :p"
                + (onlyMine ? " and t.professionalId = :me" : "") + " order by t.createdAt desc", PatientTaskEntity.class)
                .setParameter("p", target);
        if (onlyMine) query.setParameter("me", user.getId());
        Map<UUID, String> names = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PatientTaskEntity t : query.getResultList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", t.getId());
            row.put("title", t.getTitle());
            row.put("description", t.getDescription());
            row.put("dueDate", t.getDueDate() == null ? null : t.getDueDate().toString());
            row.put("createdAt", t.getCreatedAt().toString());
            row.put("done", t.getDoneAt() != null);
            row.put("doneAt", t.getDoneAt() == null ? null : t.getDoneAt().toString());
            row.put("patientNote", t.getPatientNote());
            row.put("overdue", t.getDoneAt() == null && t.getDueDate() != null && t.getDueDate().isBefore(LocalDate.now()));
            row.put("professionalName", names.computeIfAbsent(t.getProfessionalId(), this::nameOf));
            rows.add(row);
        }
        return rows;
    }

    public Map<String, Object> addTask(String tenant, UserEntity user, UUID patientId, String title, String description,
                                       LocalDate dueDate) {
        UserEntity patient = patientOfProfessional(tenant, user, patientId, "A tarefa é passada pelo profissional que atende o paciente.");
        if (title == null || title.isBlank()) throw BusinessException.invalid("Descreva a tarefa.");
        if (title.length() > 200) throw BusinessException.invalid("O título da tarefa pode ter até 200 caracteres.");
        PatientTaskEntity task = new PatientTaskEntity();
        task.setPatientId(patient.getId());
        task.setProfessionalId(user.getId());
        task.setTitle(title.trim());
        task.setDescription(limited(description));
        task.setDueDate(dueDate);
        task.setCreatedAt(LocalDateTime.now());
        em.persist(task);
        return Map.of("id", task.getId());
    }

    /** O paciente marca (ou desmarca) a tarefa como feita e pode contar como foi. */
    public void completeTask(String tenant, UserEntity user, UUID taskId, boolean done, String note) {
        dao.defineSchema(tenant);
        PatientTaskEntity task = taskId == null ? null : em.find(PatientTaskEntity.class, taskId);
        if (task == null) throw BusinessException.invalid("Tarefa não encontrada.");
        if (user.getUserType() != UserType.PACIENTE || !task.getPatientId().equals(user.getId())) {
            throw new SecurityException("Só o próprio paciente marca a tarefa como feita.");
        }
        task.setDoneAt(done ? LocalDateTime.now() : null);
        task.setPatientNote(limited(note));
    }

    public void removeTask(String tenant, UserEntity user, UUID taskId) {
        dao.defineSchema(tenant);
        PatientTaskEntity task = taskId == null ? null : em.find(PatientTaskEntity.class, taskId);
        if (task == null) return;
        if (user.getUserType() != UserType.PROFISSIONAL || !task.getProfessionalId().equals(user.getId())) {
            throw new SecurityException("A tarefa é removida pelo profissional que a passou.");
        }
        em.remove(task);
    }

    // ---------------------------------------------------------- plano de crise

    /**
     * Os planos de segurança do paciente. O paciente recebe os seus (um por profissional que
     * o atende), com o contato de emergência do cadastro; o profissional recebe só o dele.
     */
    public Map<String, Object> safetyPlans(String tenant, UserEntity user, UUID patientId) {
        UUID target = target(tenant, user, patientId);
        boolean onlyMine = user.getUserType() == UserType.PROFISSIONAL;
        var query = em.createQuery("select s from SafetyPlanEntity s where s.patientId = :p"
                + (onlyMine ? " and s.professionalId = :me" : "") + " order by s.updatedAt desc", SafetyPlanEntity.class)
                .setParameter("p", target);
        if (onlyMine) query.setParameter("me", user.getId());
        List<Map<String, Object>> plans = new ArrayList<>();
        for (SafetyPlanEntity s : query.getResultList()) {
            Map<String, Object> plan = new LinkedHashMap<>();
            plan.put("id", s.getId());
            plan.put("professionalId", s.getProfessionalId());
            plan.put("professionalName", nameOf(s.getProfessionalId()));
            plan.put("warningSigns", s.getWarningSigns());
            plan.put("coping", s.getCoping());
            plan.put("distractions", s.getDistractions());
            plan.put("supportPeople", s.getSupportPeople());
            plan.put("professionals", s.getProfessionals());
            plan.put("safeEnvironment", s.getSafeEnvironment());
            plan.put("reasonsToLive", s.getReasonsToLive());
            plan.put("updatedAt", s.getUpdatedAt() == null ? null : s.getUpdatedAt().toString());
            plans.add(plan);
        }
        UserEntity patient = em.find(UserEntity.class, target);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plans", plans);
        result.put("patientName", fullName(patient));
        result.put("emergencyContactName", patient == null ? null : patient.getEmergencyContactName());
        result.put("emergencyContactPhone", patient == null ? null : patient.getEmergencyContactPhone());
        result.put("canEdit", user.getUserType() == UserType.PROFISSIONAL);
        return result;
    }

    public void saveSafetyPlan(String tenant, UserEntity user, UUID patientId, Map<String, Object> fields) {
        UserEntity patient = patientOfProfessional(tenant, user, patientId,
                "O plano de segurança é construído pelo profissional junto com o paciente.");
        Map<String, Object> body = fields == null ? Map.of() : fields;
        SafetyPlanEntity plan = em.createQuery("select s from SafetyPlanEntity s where s.patientId = :p "
                        + "and s.professionalId = :me", SafetyPlanEntity.class)
                .setParameter("p", patient.getId()).setParameter("me", user.getId())
                .getResultStream().findFirst().orElseGet(SafetyPlanEntity::new);
        plan.setPatientId(patient.getId());
        plan.setProfessionalId(user.getId());
        plan.setWarningSigns(limited(body.get("warningSigns")));
        plan.setCoping(limited(body.get("coping")));
        plan.setDistractions(limited(body.get("distractions")));
        plan.setSupportPeople(limited(body.get("supportPeople")));
        plan.setProfessionals(limited(body.get("professionals")));
        plan.setSafeEnvironment(limited(body.get("safeEnvironment")));
        plan.setReasonsToLive(limited(body.get("reasonsToLive")));
        plan.setUpdatedAt(LocalDateTime.now());
        em.merge(plan);
    }

    // ------------------------------------------------------------------- termos

    /**
     * Pede ao paciente que aceite o termo. Se já há um pedido da mesma versão (aceito ou
     * não), devolve o que existe em vez de pedir de novo.
     */
    public Map<String, Object> requestTerm(String tenant, UserEntity user, UUID patientId, String kind) {
        if (!ONLINE_CARE.equals(kind)) throw BusinessException.invalid("Tipo de termo desconhecido.");
        UserEntity patient = patientOfProfessional(tenant, user, patientId, "O termo é enviado pelo profissional que atende o paciente.");
        PatientTermEntity existing = em.createQuery("select t from PatientTermEntity t where t.patientId = :p "
                        + "and t.professionalId = :me and t.kind = :k and t.version = :v", PatientTermEntity.class)
                .setParameter("p", patient.getId()).setParameter("me", user.getId())
                .setParameter("k", kind).setParameter("v", ONLINE_CARE_VERSION)
                .getResultStream().findFirst().orElse(null);
        if (existing != null) return describe(existing, true);

        PatientTermEntity term = new PatientTermEntity();
        term.setPatientId(patient.getId());
        term.setProfessionalId(user.getId());
        term.setKind(kind);
        term.setVersion(ONLINE_CARE_VERSION);
        term.setContent(onlineCareText(patient, user));
        term.setRequestedAt(LocalDateTime.now());
        em.persist(term);
        return describe(term, true);
    }

    public List<Map<String, Object>> terms(String tenant, UserEntity user, UUID patientId) {
        UUID target = target(tenant, user, patientId);
        boolean onlyMine = user.getUserType() == UserType.PROFISSIONAL;
        var query = em.createQuery("select t from PatientTermEntity t where t.patientId = :p"
                + (onlyMine ? " and t.professionalId = :me" : "") + " order by t.requestedAt desc", PatientTermEntity.class)
                .setParameter("p", target);
        if (onlyMine) query.setParameter("me", user.getId());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PatientTermEntity t : query.getResultList()) rows.add(describe(t, true));
        return rows;
    }

    /** O aceite: o paciente (ou o responsável) digita o nome completo. Não se desfaz. */
    public Map<String, Object> signTerm(String tenant, UserEntity user, UUID termId, String name) {
        dao.defineSchema(tenant);
        PatientTermEntity term = termId == null ? null : em.find(PatientTermEntity.class, termId);
        if (term == null) throw BusinessException.invalid("Termo não encontrado.");
        if (user.getUserType() != UserType.PACIENTE || !term.getPatientId().equals(user.getId())) {
            throw new SecurityException("O termo é aceito pelo próprio paciente.");
        }
        if (term.getSignedAt() != null) throw BusinessException.conflict("Este termo já foi aceito.");
        if (name == null || name.trim().split("\\s+").length < 2) {
            throw BusinessException.invalid("Digite seu nome completo para aceitar o termo.");
        }
        term.setSignedName(name.trim());
        term.setSignedAt(LocalDateTime.now());
        return describe(term, true);
    }

    /** Pendências do paciente, para a tela inicial. */
    public Map<String, Object> patientSummary(String tenant, UserEntity patient) {
        dao.defineSchema(tenant);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("openTasks", em.createQuery("select count(t) from PatientTaskEntity t where t.patientId = :p "
                + "and t.doneAt is null", Long.class).setParameter("p", patient.getId()).getSingleResult());
        result.put("pendingTerms", em.createQuery("select count(t) from PatientTermEntity t where t.patientId = :p "
                + "and t.signedAt is null", Long.class).setParameter("p", patient.getId()).getSingleResult());
        result.put("hasSafetyPlan", em.createQuery("select count(s) from SafetyPlanEntity s where s.patientId = :p",
                Long.class).setParameter("p", patient.getId()).getSingleResult() > 0);
        return result;
    }

    // --------------------------------------------------------------- auxiliares

    private Map<String, Object> describe(PatientTermEntity t, boolean withContent) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", t.getId());
        row.put("kind", t.getKind());
        row.put("version", t.getVersion());
        row.put("title", "Termo de consentimento para atendimento online");
        row.put("professionalName", nameOf(t.getProfessionalId()));
        row.put("requestedAt", t.getRequestedAt().toString());
        row.put("signed", t.getSignedAt() != null);
        row.put("signedAt", t.getSignedAt() == null ? null : t.getSignedAt().toString());
        row.put("signedName", t.getSignedName());
        if (withContent) row.put("content", t.getContent());
        return row;
    }

    private static String onlineCareText(UserEntity patient, UserEntity professional) {
        String crp = professional.getCrp() == null || professional.getCrp().isBlank()
                ? "psicólogo(a)" : "psicólogo(a), CRP " + professional.getCrp();
        String guardian = patient.getGuardianName() == null || patient.getGuardianName().isBlank()
                ? "" : ", representado(a) por " + patient.getGuardianName() + ",";
        return String.join("\n\n",
                "Eu, " + fullName(patient) + ", CPF " + cpf(patient.getCpf()) + guardian + " declaro que fui informado(a) por "
                        + fullName(professional) + ", " + crp + ", e concordo com as condições abaixo para o atendimento "
                        + "psicológico realizado por meio de tecnologias digitais de informação e comunicação.",
                "1. Como funciona. As sessões acontecem por chamada de vídeo, em dia e horário combinados, pela sala "
                        + "indicada na minha agenda ou por outra ferramenta que combinarmos.",
                "2. Sigilo. O atendimento online segue o mesmo sigilo profissional do atendimento presencial, nos termos "
                        + "do Código de Ética Profissional do Psicólogo.",
                "3. Meu ambiente. Comprometo-me a participar de um local reservado, onde eu não seja ouvido(a) nem "
                        + "interrompido(a), usando de preferência fones de ouvido, e a não participar da sessão enquanto dirijo.",
                "4. Gravação. As sessões não são gravadas. Nenhuma das partes pode gravar áudio, vídeo ou tela sem "
                        + "autorização expressa da outra.",
                "5. Limites da tecnologia. Sei que a internet pode falhar e que nenhum meio digital é totalmente livre de "
                        + "riscos. Se a conexão cair, tentaremos retomar pela mesma sala; se não for possível em até 10 minutos, "
                        + "o(a) profissional entrará em contato pelo telefone cadastrado para combinar a continuação ou a remarcação.",
                "6. Situações de urgência. O atendimento online não substitui serviços de emergência. Em situação de risco, "
                        + "devo procurar o pronto-socorro mais próximo ou ligar para o SAMU (192) ou para o CVV (188). Autorizo "
                        + "o(a) profissional a acionar meu contato de emergência cadastrado se avaliar que há risco à minha integridade.",
                "7. Quando o online não é indicado. O(a) profissional pode indicar atendimento presencial ou encaminhamento "
                        + "quando avaliar que a modalidade online não é adequada ao meu caso.",
                "8. Dados pessoais. Meus dados e meu prontuário são tratados conforme a Lei Geral de Proteção de Dados "
                        + "(Lei 13.709/2018) e guardados pelo prazo exigido pelo Conselho Federal de Psicologia.",
                "9. Desistência. Posso retirar este consentimento a qualquer momento, comunicando o(a) profissional; o "
                        + "atendimento passará a ser presencial ou será encerrado, como combinarmos.",
                "Este atendimento segue as normas do Conselho Federal de Psicologia para serviços psicológicos prestados "
                        + "por meios digitais.");
    }

    /** De quem são os dados pedidos: o paciente só os próprios; a equipe, um paciente que possa ver. */
    private UUID target(String tenant, UserEntity user, UUID patientId) {
        dao.defineSchema(tenant);
        if (user.getUserType() == UserType.PACIENTE) return user.getId();
        if (patientId == null) throw BusinessException.invalid("Informe o paciente.");
        UserEntity patient = em.find(UserEntity.class, patientId);
        if (patient == null) throw BusinessException.invalid("Paciente não encontrado.");
        access.requireAssociated(tenant, user, patient);
        return patientId;
    }

    private UserEntity patientOfProfessional(String tenant, UserEntity user, UUID patientId, String refusal) {
        if (user.getUserType() != UserType.PROFISSIONAL) throw new SecurityException(refusal);
        dao.defineSchema(tenant);
        UserEntity patient = patientId == null ? null : em.find(UserEntity.class, patientId);
        if (patient == null || patient.getUserType() != UserType.PACIENTE) throw BusinessException.invalid("Paciente não encontrado.");
        access.requireAssociated(tenant, user, patient);
        return patient;
    }

    private String nameOf(UUID userId) {
        return userId == null ? null : fullName(em.find(UserEntity.class, userId));
    }

    private static String fullName(UserEntity user) {
        return user == null ? null : (user.getFirstName() + " " + user.getLastName()).trim();
    }

    private static String cpf(String cpf) {
        if (cpf == null || cpf.length() != 11) return cpf == null ? "" : cpf;
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
    }

    private static String limited(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        if (text.isEmpty()) return null;
        if (text.length() > MAX_TEXT) throw BusinessException.invalid("O texto pode ter até " + MAX_TEXT + " caracteres.");
        return text;
    }
}
