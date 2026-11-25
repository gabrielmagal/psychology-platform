package br.com.mindhaven.application.service;

import br.com.mindhaven.domain.entity.IssuedDocumentEntity;
import br.com.mindhaven.domain.entity.UserEntity;
import br.com.mindhaven.domain.entity.enums.UserType;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import core.error.BusinessException;
import core.repository.dao.GenericDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Emissão de documentos em PDF: recibo, declaração de comparecimento e contrato.
 *
 * <p>Cada documento recebe um número sequencial por tipo e ano e fica guardado exatamente
 * como foi emitido. Pedir de novo o recibo de um mesmo pagamento devolve o já emitido, em
 * vez de gerar outro número.
 */
@ApplicationScoped
@Transactional
public class DocumentService {
    public static final String RECEIPT = "RECIBO";
    public static final String ATTENDANCE = "DECLARACAO";
    public static final String CONTRACT = "CONTRATO";
    /** Prefixo de um parágrafo que é título de seção. */
    public static final String HEADING = "## ";
    /** O que o paciente abre pelo aplicativo. O resto é entregue pelo profissional. */
    private static final java.util.Set<String> PATIENT_VISIBLE = java.util.Set.of(RECEIPT, ATTENDANCE);

    private static final Locale BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", BR);
    private static final Font TITLE = new Font(Font.HELVETICA, 15, Font.BOLD);
    private static final Font BODY = new Font(Font.HELVETICA, 11.5f);
    private static final Font BOLD = new Font(Font.HELVETICA, 11.5f, Font.BOLD);
    private static final Font SMALL = new Font(Font.HELVETICA, 9);

    @Inject
    GenericDao dao;

    @Inject
    EntityManager em;

    @Inject
    ClinicService clinic;

    @SuppressWarnings("unchecked")
    public Map<String, Object> issue(String tenant, UserEntity user, String type, UUID referenceId) {
        if (referenceId == null) throw BusinessException.invalid("Informe a que o documento se refere.");
        dao.defineSchema(tenant);

        // Os mesmos métodos que alimentam a tela conferem o acesso e trazem os dados.
        Map<String, Object> data;
        String title;
        List<String> paragraphs = new ArrayList<>();
        boolean patientSigns = false;
        switch (type) {
            case RECEIPT -> {
                data = clinic.receipt(tenant, user, referenceId);
                title = "RECIBO";
                Map<String, Object> patient = (Map<String, Object>) data.get("patient");
                paragraphs.add("Recebi de " + patient.get("name") + ", CPF " + cpf(patient.get("cpf")) + ", a quantia de "
                        + money(data.get("amount")) + ", paga em " + date(data.get("paymentDate")) + " por "
                        + method(data.get("paymentMethod")) + ", referente a serviços de psicologia"
                        + (data.get("packageTitle") == null ? "" : " (" + data.get("packageTitle") + ")") + ".");
                paragraphs.add("Por ser verdade, firmo o presente recibo.");
            }
            case ATTENDANCE -> {
                data = clinic.attendance(tenant, user, referenceId);
                title = "DECLARAÇÃO DE COMPARECIMENTO";
                Map<String, Object> patient = (Map<String, Object>) data.get("patient");
                paragraphs.add("Declaro, para os devidos fins, que " + patient.get("name") + ", CPF " + cpf(patient.get("cpf"))
                        + ", compareceu a atendimento psicológico no dia " + date(data.get("date"))
                        + (data.get("startTime") == null ? "" : ", às " + data.get("startTime") + ", com duração de "
                        + data.get("durationMinutes") + " minutos") + ".");
            }
            case CONTRACT -> {
                if (user.getUserType() == UserType.PACIENTE) {
                    throw new SecurityException("O contrato é emitido pelo profissional.");
                }
                Map<String, Object> record = clinic.patientRecord(tenant, user, referenceId);
                data = new LinkedHashMap<>();
                data.put("patient", record.get("patient"));
                Map<String, Object> professional = new LinkedHashMap<>();
                professional.put("id", user.getId());
                professional.put("name", (user.getFirstName() + " " + user.getLastName()).trim());
                professional.put("cpf", user.getCpf());
                professional.put("crp", user.getCrp());
                data.put("professional", professional);
                title = "CONTRATO DE PRESTAÇÃO DE SERVIÇOS PSICOLÓGICOS";
                Map<String, Object> patient = (Map<String, Object>) record.get("patient");
                patientSigns = true;
                paragraphs.add("Contratante: " + patient.get("name") + ", CPF " + cpf(patient.get("cpf"))
                        + (patient.get("guardianName") == null ? "" : ", representado(a) por " + patient.get("guardianName")) + ".");
                paragraphs.add("Contratado(a): " + professional.get("name") + ", " + crp(professional) + ", CPF "
                        + cpf(professional.get("cpf")) + ".");
                paragraphs.add("1. Objeto. Prestação de atendimento psicológico em sessões com duração e periodicidade "
                        + "combinadas entre as partes, na modalidade presencial ou online.");
                paragraphs.add("2. Sigilo. O atendimento é protegido pelo sigilo profissional, nos termos do Código de Ética "
                        + "Profissional do Psicólogo. As informações só serão compartilhadas nas hipóteses previstas em lei "
                        + "ou com autorização do contratante.");
                paragraphs.add("3. Faltas e remarcações. Remarcações devem ser pedidas com antecedência mínima de 24 horas. "
                        + "A sessão não desmarcada nesse prazo é considerada realizada para fins de cobrança.");
                paragraphs.add("4. Honorários. Os valores e a forma de pagamento são os registrados no pacote de sessões "
                        + "contratado, com emissão de recibo a cada pagamento.");
                paragraphs.add("5. Dados pessoais. Os dados e o prontuário são tratados conforme a Lei Geral de Proteção de "
                        + "Dados (Lei 13.709/2018) e guardados pelo prazo exigido pelo Conselho Federal de Psicologia.");
                paragraphs.add("6. Encerramento. Qualquer das partes pode encerrar o atendimento a qualquer momento, "
                        + "recomendando-se uma sessão de fechamento.");
            }
            default -> throw BusinessException.invalid("Tipo de documento desconhecido.");
        }

        // Recibo e declaração são únicos por pagamento/sessão: devolve o que já foi emitido.
        if (!CONTRACT.equals(type)) {
            IssuedDocumentEntity existing = em.createQuery(
                            "select d from IssuedDocumentEntity d where d.docType = :t and d.referenceId = :r",
                            IssuedDocumentEntity.class)
                    .setParameter("t", type).setParameter("r", referenceId).setMaxResults(1)
                    .getResultStream().findFirst().orElse(null);
            if (existing != null) return describe(existing);
        }

        Map<String, Object> patient = (Map<String, Object>) data.get("patient");
        Map<String, Object> professional = (Map<String, Object>) data.get("professional");
        int year = LocalDate.now().getYear();
        int number = em.createQuery("select coalesce(max(d.docNumber), 0) from IssuedDocumentEntity d "
                        + "where d.docType = :t and d.docYear = :y", Integer.class)
                .setParameter("t", type).setParameter("y", year).getSingleResult() + 1;
        String code = String.format("%04d/%d", number, year);

        IssuedDocumentEntity document = new IssuedDocumentEntity();
        document.setDocType(type);
        document.setDocYear(year);
        document.setDocNumber(number);
        document.setTitle(title + " nº " + code + " — " + patient.get("name"));
        document.setPatientId((UUID) patient.get("id"));
        document.setProfessionalId(professionalId(professional, user));
        document.setReferenceId(referenceId);
        document.setIssuedBy(user.getId());
        document.setIssuedAt(LocalDateTime.now());
        document.setContent(render(title, code, paragraphs, professional, patientSigns ? patient : null));
        em.persist(document);
        return describe(document);
    }

    /**
     * Emite um documento escrito pelo profissional (atestado, relatório, laudo, parecer). Os
     * parágrafos chegam prontos; os que começam com {@link #HEADING} são títulos de seção.
     *
     * @return o id do documento emitido
     */
    public UUID issueWritten(String tenant, UserEntity professional, UserEntity patient, String type, String title,
                             UUID referenceId, List<String> paragraphs) {
        dao.defineSchema(tenant);
        Map<String, Object> author = new LinkedHashMap<>();
        author.put("id", professional.getId());
        author.put("name", (professional.getFirstName() + " " + professional.getLastName()).trim());
        author.put("cpf", professional.getCpf());
        author.put("crp", professional.getCrp());
        String patientName = (patient.getFirstName() + " " + patient.getLastName()).trim();

        List<String> body = new ArrayList<>();
        body.add("Pessoa atendida: " + patientName + ", CPF " + cpf(patient.getCpf()) + ".");
        body.add("Autor(a): " + author.get("name") + ", psicólogo(a), " + crp(author) + ".");
        body.addAll(paragraphs);

        int year = LocalDate.now().getYear();
        int number = em.createQuery("select coalesce(max(d.docNumber), 0) from IssuedDocumentEntity d "
                        + "where d.docType = :t and d.docYear = :y", Integer.class)
                .setParameter("t", type).setParameter("y", year).getSingleResult() + 1;
        String code = String.format("%04d/%d", number, year);

        IssuedDocumentEntity document = new IssuedDocumentEntity();
        document.setDocType(type);
        document.setDocYear(year);
        document.setDocNumber(number);
        document.setTitle(title + " nº " + code + " — " + patientName);
        document.setPatientId(patient.getId());
        document.setProfessionalId(professional.getId());
        document.setReferenceId(referenceId);
        document.setIssuedBy(professional.getId());
        document.setIssuedAt(LocalDateTime.now());
        document.setContent(render(title, code, body, author, null));
        em.persist(document);
        return document.getId();
    }

    public List<Map<String, Object>> list(String tenant, UserEntity user, UUID patientId) {
        dao.defineSchema(tenant);
        UUID target = patientId;
        if (user.getUserType() == UserType.PACIENTE) {
            target = user.getId(); // o paciente só lista os próprios
        } else {
            if (target == null) throw BusinessException.invalid("Informe o paciente.");
            clinic.patientRecord(tenant, user, target); // confere que o profissional atende o paciente
        }
        var query = em.createQuery("select d from IssuedDocumentEntity d where d.patientId = :p"
                        + (user.getUserType() == UserType.PROFISSIONAL ? " and d.professionalId = :me" : "")
                        + " order by d.issuedAt desc", IssuedDocumentEntity.class)
                .setParameter("p", target);
        if (user.getUserType() == UserType.PROFISSIONAL) query.setParameter("me", user.getId());
        List<Map<String, Object>> result = new ArrayList<>();
        for (IssuedDocumentEntity d : query.getResultList()) {
            // O paciente vê recibos e declarações; contrato, laudo e relatório ficam com o profissional.
            if (user.getUserType() == UserType.PACIENTE && !PATIENT_VISIBLE.contains(d.getDocType())) continue;
            result.add(describe(d));
        }
        return result;
    }

    public IssuedDocumentEntity pdf(String tenant, UserEntity user, UUID documentId) {
        dao.defineSchema(tenant);
        IssuedDocumentEntity document = em.find(IssuedDocumentEntity.class, documentId);
        if (document == null) throw BusinessException.invalid("Documento não encontrado.");
        boolean allowed = switch (user.getUserType()) {
            case ADMINISTRADOR -> true;
            case PROFISSIONAL -> user.getId().equals(document.getProfessionalId());
            case PACIENTE -> user.getId().equals(document.getPatientId()) && PATIENT_VISIBLE.contains(document.getDocType());
        };
        if (!allowed) throw new SecurityException("Você não tem acesso a este documento.");
        return document;
    }

    private static Map<String, Object> describe(IssuedDocumentEntity d) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", d.getId());
        row.put("type", d.getDocType());
        row.put("number", String.format("%04d/%d", d.getDocNumber(), d.getDocYear()));
        row.put("title", d.getTitle());
        row.put("issuedAt", d.getIssuedAt().toString());
        return row;
    }

    private static UUID professionalId(Map<String, Object> professional, UserEntity user) {
        Object id = professional == null ? null : professional.get("id");
        return id instanceof UUID uuid ? uuid : user.getId();
    }

    private byte[] render(String title, String code, List<String> paragraphs, Map<String, Object> professional,
                          Map<String, Object> patientSigner) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document pdf = new Document(PageSize.A4, 72, 72, 80, 72);
        PdfWriter.getInstance(pdf, out);
        pdf.open();

        Paragraph heading = new Paragraph(title, TITLE);
        heading.setAlignment(Element.ALIGN_CENTER);
        pdf.add(heading);
        Paragraph numbering = new Paragraph("Nº " + code, SMALL);
        numbering.setAlignment(Element.ALIGN_CENTER);
        numbering.setSpacingAfter(28);
        pdf.add(numbering);

        for (String text : paragraphs) {
            if (text.startsWith(HEADING)) {
                Paragraph section = new Paragraph(text.substring(HEADING.length()), BOLD);
                section.setSpacingBefore(10);
                section.setSpacingAfter(6);
                pdf.add(section);
                continue;
            }
            Paragraph p = new Paragraph(text, BODY);
            p.setAlignment(Element.ALIGN_JUSTIFIED);
            p.setLeading(18);
            p.setSpacingAfter(12);
            pdf.add(p);
        }

        Paragraph when = new Paragraph(LocalDate.now().format(LONG_DATE) + ".", BODY);
        when.setSpacingBefore(18);
        pdf.add(when);

        addSignature(pdf, String.valueOf(professional.get("name")), "Psicólogo(a) — " + crp(professional)
                + (professional.get("cpf") == null ? "" : " — CPF " + cpf(professional.get("cpf"))));
        if (patientSigner != null) {
            Object signer = patientSigner.get("guardianName") == null ? patientSigner.get("name") : patientSigner.get("guardianName");
            addSignature(pdf, String.valueOf(signer), "Contratante");
        }
        pdf.close();
        return out.toByteArray();
    }

    private static void addSignature(Document pdf, String name, String caption) {
        Paragraph line = new Paragraph("_____________________________________________", BODY);
        line.setAlignment(Element.ALIGN_CENTER);
        line.setSpacingBefore(56);
        pdf.add(line);
        Paragraph who = new Paragraph(new Chunk(name, BOLD));
        who.setAlignment(Element.ALIGN_CENTER);
        pdf.add(who);
        Paragraph what = new Paragraph(caption, SMALL);
        what.setAlignment(Element.ALIGN_CENTER);
        pdf.add(what);
    }

    private static String crp(Map<String, Object> professional) {
        Object crp = professional.get("crp");
        return crp == null || crp.toString().isBlank() ? "CRP não informado" : "CRP " + crp;
    }

    private static String cpf(Object value) {
        String cpf = value == null ? "" : value.toString();
        if (cpf.length() != 11) return cpf;
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
    }

    private static String money(Object value) {
        return NumberFormat.getCurrencyInstance(BR).format(value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()));
    }

    private static String date(Object iso) {
        return iso == null ? "" : LocalDate.parse(iso.toString().substring(0, 10)).format(DATE);
    }

    private static String method(Object value) {
        return switch (String.valueOf(value)) {
            case "PIX" -> "Pix";
            case "CASH" -> "dinheiro";
            case "TRANSFER" -> "transferência";
            case "CARD" -> "cartão";
            case "BOLETO" -> "boleto";
            default -> "outro meio";
        };
    }
}
