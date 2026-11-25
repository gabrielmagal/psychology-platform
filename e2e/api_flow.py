"""Teste de ponta a ponta contra a API local (Postgres + Keycloak reais do WSL).

Cria três usuários de teste (admin, psicólogo, paciente) e percorre os fluxos novos.
Todos os dados criados usam CPF 9000000000x e e-mail @exemplo.com, para serem fáceis de
achar e apagar depois.
"""
import datetime as dt
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

def env(name, default=None):
    value = os.environ.get(name, default)
    if value is None:
        raise SystemExit(f"Defina a variável {name} (veja e2e/.env.example).")
    return value


KC = env("E2E_KEYCLOAK_URL", "http://localhost:8083")
API = env("E2E_API_URL", "http://127.0.0.1:8080/api")
REALM = env("E2E_REALM", "barueri")
KC_ADMIN_USER = env("E2E_KEYCLOAK_ADMIN_USER")
KC_ADMIN_PASSWORD = env("E2E_KEYCLOAK_ADMIN_PASSWORD")
# Senha dada aos três usuários de teste que o roteiro cria.
PASSWORD = env("E2E_TEST_USER_PASSWORD")
DB_CONTAINER = env("E2E_POSTGRES_CONTAINER", "postgres")
DB_USER = env("E2E_POSTGRES_USER", "arquitetura")
DB_NAME = env("E2E_POSTGRES_DB", "psychology")
results = []


def http(method, url, token=None, body=None, form=None, tenant=True):
    headers = {}
    data = None
    if url.startswith(KC + "/admin"):
        token = kc_admin_token()  # o token do admin do Keycloak vale só 60s
    if token:
        headers["Authorization"] = "Bearer " + token
    if tenant and url.startswith(API):
        headers["Tenant"] = REALM
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if form is not None:
        data = urllib.parse.urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    if url.startswith(KC):
        # IPv4 direto (o localhost em IPv6 trava 60s), mantendo o Host para o emissor do token bater.
        url = url.replace('//localhost:', '//127.0.0.1:')
        headers['Host'] = urllib.parse.urlparse(KC).netloc
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw = resp.read().decode(errors="replace")
            status, hdrs = resp.status, resp.headers
    except urllib.error.HTTPError as e:
        raw, status, hdrs = e.read().decode(errors="replace"), e.code, e.headers
    try:
        parsed = json.loads(raw) if raw else None
    except ValueError:
        parsed = raw
    return status, parsed, hdrs


def check(name, ok, detail=""):
    results.append((name, bool(ok)))
    print(("PASS  " if ok else "FAIL  ") + name + (("  -> " + str(detail)[:300]) if (detail and not ok) else ""))


def expect(name, response, status):
    code, body, _ = response
    check(f"{name} [{code}]", code == status, body)
    return body


def kc_admin_token():
    _, body, _ = http("POST", f"{KC}/realms/master/protocol/openid-connect/token",
                      form={"grant_type": "password", "client_id": "admin-cli",
                            "username": KC_ADMIN_USER, "password": KC_ADMIN_PASSWORD})
    return body["access_token"]


def kc_find_user(admin, username):
    _, body, _ = http("GET", f"{KC}/admin/realms/{REALM}/users?exact=true&username={urllib.parse.quote(username)}",
                      token=admin)
    return body[0] if isinstance(body, list) and body else None


def kc_set_password(admin, user_id):
    http("PUT", f"{KC}/admin/realms/{REALM}/users/{user_id}/reset-password", token=admin,
         body={"type": "password", "temporary": False, "value": PASSWORD})
    http("PUT", f"{KC}/admin/realms/{REALM}/users/{user_id}", token=admin,
         body={"requiredActions": [], "emailVerified": True})


def login(username):
    code, body, _ = http("POST", f"{KC}/realms/{REALM}/protocol/openid-connect/token",
                         form={"grant_type": "password", "client_id": "frontend",
                               "username": username, "password": PASSWORD})
    if code != 200:
        raise SystemExit(f"login de {username} falhou: {code} {body}")
    return body["access_token"]


# No Windows o Docker costuma estar dentro do WSL.
DOCKER = ["wsl", "-e", "docker"] if os.name == "nt" else ["docker"]


def psql(sql):
    out = subprocess.run(DOCKER + ["exec", DB_CONTAINER, "psql", "-U", DB_USER, "-d", DB_NAME, "-Atc", sql], capture_output=True, text=True)
    return out.stdout.strip(), out.stderr.strip()


# ----------------------------------------------------------------- preparação
admin_kc = kc_admin_token()

ADMIN_USER = "teste.admin"
kc_user = kc_find_user(admin_kc, ADMIN_USER)
if not kc_user:
    code, body, hdrs = http("POST", f"{KC}/admin/realms/{REALM}/users", token=admin_kc, body={
        "username": ADMIN_USER, "email": "teste.admin@exemplo.com", "enabled": True,
        "firstName": "Admin", "lastName": "Teste", "emailVerified": True})
    assert code == 201, (code, body)
    kc_user = kc_find_user(admin_kc, ADMIN_USER)
    _, role, _ = http("GET", f"{KC}/admin/realms/{REALM}/roles/ADMINISTRADOR_ROLE", token=admin_kc)
    http("POST", f"{KC}/admin/realms/{REALM}/users/{kc_user['id']}/role-mappings/realm", token=admin_kc, body=[role])
kc_set_password(admin_kc, kc_user["id"])

existing, _ = psql(f"select id from {REALM}.user_info where keycloak_id = '{kc_user['id']}'")
if not existing:
    out, err = psql(
        f"insert into {REALM}.user_info (id, keycloak_id, cpf, first_name, last_name, email, phone_number, "
        "birth_date, user_type) values (gen_random_uuid(), '%s', '90000000001', 'Admin', 'Teste', "
        "'teste.admin@exemplo.com', '11900000001', '1985-01-01', 'ADMINISTRADOR')" % kc_user["id"])
    assert "INSERT" in out, (out, err)

admin = login(ADMIN_USER)

# A primeira chamada autenticada aplica as migrações pendentes no schema do tenant.
me = expect("admin lê o próprio cadastro (dispara a migração)", http("GET", f"{API}/user/keycloak", token=admin), 200)
history, _ = psql(f"select version from {REALM}.flyway_schema_history order by installed_rank")
check("migrações novas aplicadas no Postgres real", "20261124" in history and "20261126" in history, history)


def ensure_user(cpf, first, user_type, extra=None):
    found, _ = psql(f"select id from {REALM}.user_info where cpf = '{cpf}'")
    if not found:
        payload = {"cpf": cpf, "firstName": first, "lastName": "Teste", "email": f"teste.{first.lower()}@exemplo.com",
                   "phoneNumber": "119" + cpf[-8:], "birthDate": "1990-05-10", "userType": user_type}
        payload.update(extra or {})
        expect(f"admin cadastra {first} ({user_type}) — cria usuário no Keycloak",
               http("POST", f"{API}/user", token=admin, body=payload), 200)
        found, _ = psql(f"select id from {REALM}.user_info where cpf = '{cpf}'")
    kc = kc_find_user(admin_kc, cpf)
    check(f"{first} existe no Keycloak", kc is not None)
    kc_set_password(admin_kc, kc["id"])
    return found


psy_id = ensure_user("90000000002", "Psicologa", "PROFISSIONAL", {"crp": "06/123456", "address": "Rua das Flores, 100"})
pat_id = ensure_user("90000000003", "Paciente", "PACIENTE", {"emergencyContactName": "Mãe", "emergencyContactPhone": "11988887777"})
psy = login("90000000002")
pat = login("90000000003")

# ------------------------------------------------------------------- pacotes
pkgs = expect("psicóloga lista pacotes", http("GET", f"{API}/session-package?page=0&size=50", token=psy), 200)
pkg = next((p for p in pkgs["content"] if p.get("packageTitle") == "Pacote E2E"), None)
if not pkg:
    pkg = expect("psicóloga cria pacote com valor", http("POST", f"{API}/session-package", token=psy, body={
        "professionalId": psy_id, "patient": {"id": pat_id}, "packageTitle": "Pacote E2E",
        "totalSessions": 4, "price": 400.00}), 200)
pkg_id = pkg["id"]
check("pacote guarda o valor", float(pkg.get("price") or 0) == 400.0, pkg)

mine = expect("paciente lista os próprios pacotes", http("GET", f"{API}/session-package?page=0&size=50", token=pat), 200)
check("paciente vê só pacotes dele, incluindo este", pkg_id in [p["id"] for p in mine["content"]] and all(p["patient"]["id"] == pat_id for p in mine["content"]), [p["packageTitle"] for p in mine["content"]])
expect("paciente NÃO cria pacote", http("POST", f"{API}/session-package", token=pat, body={
    "professionalId": psy_id, "patient": {"id": pat_id}, "packageTitle": "x"}), 403)

# -------------------------------------------------------------------- agenda
now = dt.datetime.now()
soon = now + dt.timedelta(hours=2)
far_day = (now + dt.timedelta(days=40)).date()

# Limpa sessões de execuções anteriores deste roteiro para o resultado ser repetível.
psql(f"delete from {REALM}.session_info where session_package_id = '{pkg_id}'")
psql(f"delete from {REALM}.session_package where single_session = true and patient_id = '{pat_id}'")
psql(f"delete from {REALM}.working_hours where owner_id = '{psy_id}'")
psql(f"delete from {REALM}.schedule_block where owner_id = '{psy_id}'")

created = expect("psicóloga agenda 3 sessões semanais", http("POST", f"{API}/clinic/agenda", token=psy, body={
    "sessionPackageId": pkg_id, "date": str(far_day), "startTime": "14:00", "durationMinutes": 50,
    "modality": "ONLINE", "meetingLink": "https://meet.exemplo/sala", "repeatWeeks": 3}), 200)
check("3 sessões criadas", len(created["created"]) == 3, created)

conflict = expect("horário sobreposto é recusado", http("POST", f"{API}/clinic/agenda", token=psy, body={
    "sessionPackageId": pkg_id, "date": str(far_day), "startTime": "14:30"}), 409)
check("conflito devolve mensagem legível", "Já existe uma sessão" in str(conflict), conflict)

expect("paciente NÃO agenda", http("POST", f"{API}/clinic/agenda", token=pat, body={
    "sessionPackageId": pkg_id, "date": str(far_day), "startTime": "09:00"}), 403)

week = expect("psicóloga vê a agenda", http("GET", f"{API}/clinic/agenda?from={far_day}&to={far_day}", token=psy), 200)
check("agenda traz horário e paciente", week and week[0]["startTime"] == "14:00" and week[0]["patientName"] == "Paciente Teste", week)
session_id = week[0]["id"]

pat_week = expect("paciente vê a mesma sessão", http("GET", f"{API}/clinic/agenda?from={far_day}&to={far_day}", token=pat), 200)
check("paciente vê o nome da psicóloga e não vê telefone",
      pat_week[0].get("professionalName") == "Psicologa Teste" and "patientPhone" not in pat_week[0], pat_week)

expect("paciente confirma presença", http("PUT", f"{API}/clinic/agenda/{session_id}/confirm", token=pat), 204)
expect("paciente NÃO muda a situação", http("PUT", f"{API}/clinic/agenda/{session_id}/status/REALIZADA", token=pat), 403)
expect("psicóloga marca como realizada", http("PUT", f"{API}/clinic/agenda/{session_id}/status/REALIZADA", token=psy), 204)
expect("paciente avalia a sessão", http("PUT", f"{API}/clinic/agenda/{session_id}/rating", token=pat, body={
    "rating": 5, "recommend": True, "comment": "Me senti acolhido desde a primeira sessão.", "allowPublicComment": True}), 204)
doc = expect("declaração de comparecimento", http("GET", f"{API}/clinic/document/attendance/{session_id}", token=pat), 200)
check("declaração traz CRP da psicóloga", doc["professional"].get("crp") == "06/123456", doc)

# Formulário antigo (CRUD dinâmico) com os campos novos, incluindo LocalTime.
desc = expect("descrição dinâmica da sessão", http("GET", f"{API}/session/entity-description", token=psy), 200)
check("formulário dinâmico expõe horário e situação",
      desc["properties"].get("startTime", {}).get("type") == "LocalTime" and "enumValues" in desc["properties"].get("status", {}), desc["properties"].keys())

# ---------------------------------------------------------------- prontuário
expect("psicóloga salva a anamnese", http("PUT", f"{API}/clinic/patient-record/{pat_id}", token=psy, body={
    "chiefComplaint": "Ansiedade", "therapeuticGoals": "Reduzir crises"}), 204)
record = expect("psicóloga lê o prontuário", http("GET", f"{API}/clinic/patient-record/{pat_id}", token=psy), 200)
check("anamnese persistida", record.get("chiefComplaint") == "Ansiedade", record)
timeline = expect("linha do tempo", http("GET", f"{API}/clinic/patient-record/{pat_id}/timeline", token=psy), 200)
check("linha do tempo tem as 3 sessões", len(timeline) == 3, len(timeline))
expect("paciente NÃO lê o prontuário", http("GET", f"{API}/clinic/patient-record/{pat_id}", token=pat), 403)
audit, _ = psql(f"select count(*) from {REALM}.audit_log where entity_name ilike '%PatientRecord%' and (coalesce(new_value,'') like '%Ansiedade%' or coalesce(old_value,'') like '%Ansiedade%')")
check("auditoria não guarda o texto clínico em claro", audit == "0", audit)

# ------------------------------------------- CRUD dinâmico (edição e vínculos)
expect("psicóloga edita o pacote (PUT)", http("PUT", f"{API}/session-package/{pkg_id}", token=psy, body={
    "id": pkg_id, "patient": {"id": pat_id}, "packageTitle": "Pacote E2E", "totalSessions": 5, "price": 400.00}), 200)
count, _ = psql(f"select count(*) || '/' || max(total_sessions) from {REALM}.session_package where package_title = 'Pacote E2E'")
check("editar altera o registro em vez de duplicar", count == "1/5", count)
# Anotação no formato antigo (antes do registro estruturado): tem de continuar legível.
expect("anotação no formato antigo ainda é aceita", http("POST", f"{API}/annotation", token=psy, body={
    "mainFeeling": "Alívio", "significantEvents": "Semana de provas", "currentPhase": "Inicial",
    "dominantThought": "Vou conseguir", "intervention": "Psicoeducação", "session": {"id": session_id}}), 200)
timeline = expect("linha do tempo com anotação antiga", http("GET", f"{API}/clinic/patient-record/{pat_id}/timeline", token=psy), 200)
old_note = next(i for i in timeline if i["id"] == session_id)
check("anotação antiga aparece dentro do registro novo", old_note.get("interventions") == "Psicoeducação"
      and old_note.get("demand") == "Semana de provas" and "Sentimento principal: Alívio" in (old_note.get("observations") or ""), old_note)
edited = expect("admin edita o paciente (PUT)", http("PUT", f"{API}/user/{pat_id}", token=admin, body={
    "cpf": "90000000003", "firstName": "Paciente", "lastName": "Teste", "email": "teste.paciente@exemplo.com",
    "phoneNumber": "11900000003", "birthDate": "1990-05-10", "userType": "PACIENTE", "occupation": "Estudante",
    "emergencyContactName": "Mãe", "emergencyContactPhone": "11988887777"}), 200)
occupation, _ = psql(f"select count(*) || '/' || max(occupation) from {REALM}.user_info where cpf = '90000000003'")
check("edição do usuário persiste sem duplicar", occupation == "1/Estudante", occupation)

# ---------------------------------------------------------------- financeiro
psql(f"delete from {REALM}.payment where session_package_id = '{pkg_id}'")
payment = expect("psicóloga lança pagamento de R$ 150", http("POST", f"{API}/payment", token=psy, body={
    "amount": 150.00, "paymentMethod": "PIX", "paymentDate": str(now.date()), "paid": True,
    "receiptName": "pix", "receiptType": "text/plain", "sessionPackage": {"id": pkg_id}}), 200)
finance = expect("resumo financeiro", http("GET", f"{API}/clinic/finance", token=psy), 200)
debtor = next((d for d in finance["debtors"] if d["packageId"] == pkg_id), None)
check("saldo em aberto = 400 - 150", debtor and float(debtor["balance"]) == 250.0, finance)
expect("paciente NÃO vê o financeiro", http("GET", f"{API}/clinic/finance", token=pat), 403)
paid = expect("paciente consulta os próprios pagamentos", http("GET", f"{API}/session-package/{pkg_id}/payments", token=pat), 200)
check("total pago visto pelo paciente = 150", float(paid["totalPaid"]) == 150.0, paid)
receipt = expect("paciente tira o recibo", http("GET", f"{API}/clinic/document/receipt/{payment['id']}", token=pat), 200)
check("recibo com valor e pagador", float(receipt["amount"]) == 150.0 and receipt["patient"]["name"] == "Paciente Teste", receipt)
acct = expect("psicóloga consulta a conta de recebimento", http("GET", f"{API}/online-payment/account", token=psy), 200)
check("sem conta do Asaas conectada", acct["configured"] is False, acct)
bad = expect("chave de API inválida é recusada com explicação",
             http("PUT", f"{API}/online-payment/account", token=psy, body={"apiKey": "chave-invalida", "sandbox": True}), 400)
check("mensagem explica a recusa do Asaas", "Asaas" in str(bad), bad)
expect("paciente NÃO configura recebimento", http("GET", f"{API}/online-payment/account", token=pat), 403)
pay = expect("paciente tenta pagar online sem conta conectada", http("POST", f"{API}/online-payment/charge/{pkg_id}", token=pat), 409)
check("orientação clara ao paciente", "pagamento online" in str(pay), pay)
expect("webhook de tenant desconhecido é ignorado", http("POST", f"{API}/online-payment/webhook/naoexiste", body={"event": "PAYMENT_RECEIVED", "payment": {"id": "pay_x"}}, tenant=False), 404)
expect("webhook sem cobrança conhecida é aceito e ignorado", http("POST", f"{API}/online-payment/webhook/{REALM}", body={"event": "PAYMENT_RECEIVED", "payment": {"id": "pay_x"}}, tenant=False), 200)
dup = expect("CPF repetido vira mensagem clara", http("POST", f"{API}/user", token=admin, body={
    "cpf": "90000000003", "firstName": "Outro", "lastName": "Teste", "email": "outro.dup@exemplo.com",
    "phoneNumber": "11977776666", "birthDate": "1990-05-10", "userType": "PACIENTE"}), 409)
check("mensagem cita o CPF", "CPF" in str(dup), dup)

# --------------------------------------------------------------- LGPD / humor
expect("paciente aceita o consentimento", http("POST", f"{API}/clinic/consent", token=pat), 204)
consent = expect("situação do consentimento", http("GET", f"{API}/clinic/consent", token=pat), 200)
check("consentimento registrado", consent["accepted"] is True, consent)
expect("paciente registra o humor", http("POST", f"{API}/clinic/mood", token=pat, body={"mood": 4, "note": "dia bom"}), 204)
expect("psicóloga NÃO registra humor", http("POST", f"{API}/clinic/mood", token=psy, body={"mood": 4}), 403)
diary = expect("psicóloga lê o diário do paciente", http("GET", f"{API}/clinic/mood?patientId={pat_id}", token=psy), 200)
check("diário tem o registro de hoje", diary and diary[0]["mood"] == 4, diary)
export = expect("paciente exporta os próprios dados", http("GET", f"{API}/clinic/my-data", token=pat), 200)
check("exportação traz sessões, pagamentos e consentimento",
      len(export["sessions"]) == 3 and len(export["payments"]) >= 1 and len(export["consents"]) >= 1, export.keys())
check("exportação não traz notas privadas", "privateNotes" not in json.dumps(export))

# ------------------------------------------------- núcleo: cifragem e validação
stored, _ = psql(f"select chief_complaint from {REALM}.patient_record where patient_id = '{pat_id}'")
check("anamnese fica cifrada no banco", stored.startswith("enc:v1:") and "Ansiedade" not in stored, stored[:40])
invalid = expect("servidor recusa cadastro incompleto", http("POST", f"{API}/user", token=admin, body={"cpf": "123"}), 400)
check("erro cita os campos pelo nome da tela", "Nome" in str(invalid) and "CPF deve ter 11 dígitos" in str(invalid), invalid)
seen = expect("profissional lista usuários", http("GET", f"{API}/user?page=0&size=50", token=psy), 200)
check("profissional enxerga o paciente que atende", any(u["id"] == pat_id for u in seen["content"]), [u["firstName"] for u in seen["content"]])
expect("administrador NÃO cria pacote", http("POST", f"{API}/session-package", token=admin, body={
    "professionalId": psy_id, "patient": {"id": pat_id}, "packageTitle": "x"}), 403)

# --------------------------------------------- agenda: regras, remarcação, avulsa
monday = far_day + dt.timedelta(days=(7 - far_day.weekday()) % 7 + 14)
weekday = monday.isoweekday()
expect("profissional define horário de atendimento", http("PUT", f"{API}/clinic/agenda-settings/working-hours", token=psy,
       body=[{"weekday": weekday, "startTime": "08:00", "endTime": "12:00"}]), 204)
outside = expect("agendar fora do horário de atendimento é recusado", http("POST", f"{API}/clinic/agenda", token=psy, body={
    "sessionPackageId": pkg_id, "date": str(monday), "startTime": "15:00"}), 409)
check("mensagem explica o motivo", "horário de atendimento" in str(outside), outside)
inside = expect("agendar dentro do horário funciona", http("POST", f"{API}/clinic/agenda", token=psy, body={
    "sessionPackageId": pkg_id, "date": str(monday), "startTime": "09:00"}), 200)
movable = inside["created"][0]
slots = expect("paciente consulta horários livres", http("GET", f"{API}/clinic/agenda/{movable}/free-slots?date={monday}", token=pat), 200)
check("horários livres descontam a sessão marcada", "09:00" not in slots and len(slots) >= 1, slots)
expect("paciente remarca com antecedência", http("PUT", f"{API}/clinic/agenda/{movable}/reschedule", token=pat,
       body={"date": str(monday), "startTime": slots[0]}), 204)
block = expect("profissional bloqueia um período", http("POST", f"{API}/clinic/agenda-settings/blocks", token=psy, body={
    "startAt": f"{monday}T08:00:00", "endAt": f"{monday}T12:00:00", "reason": "Congresso"}), 200)
check("bloqueio avisa das sessões já marcadas no período", block["sessionsInPeriod"] >= 1, block)
blocked = expect("agendar em período bloqueado é recusado", http("POST", f"{API}/clinic/agenda", token=psy, body={
    "sessionPackageId": pkg_id, "date": str(monday), "startTime": "11:00"}), 409)
check("mensagem cita o motivo do bloqueio", "Congresso" in str(blocked), blocked)
expect("remove o bloqueio", http("DELETE", f"{API}/clinic/agenda-settings/blocks/{block['id']}", token=psy), 204)
expect("remove o horário de atendimento", http("PUT", f"{API}/clinic/agenda-settings/working-hours", token=psy, body=[]), 204)
expect("paciente cancela com antecedência", http("PUT", f"{API}/clinic/agenda/{movable}/patient-cancel", token=pat), 204)

late = expect("sessão para daqui a 3 horas", http("POST", f"{API}/clinic/agenda", token=psy, body={
    "sessionPackageId": pkg_id, "date": str((now + dt.timedelta(hours=3)).date()),
    "startTime": (now + dt.timedelta(hours=3)).strftime("%H:%M")}), 200)
too_late = expect("paciente NÃO cancela em cima da hora", http("PUT", f"{API}/clinic/agenda/{late['created'][0]}/patient-cancel", token=pat), 409)
check("mensagem manda falar com o profissional", "profissional" in str(too_late), too_late)
expect("profissional remarca a qualquer momento", http("PUT", f"{API}/clinic/agenda/{late['created'][0]}/reschedule", token=psy,
       body={"date": str(monday + dt.timedelta(days=1)), "startTime": "10:00"}), 204)

expect("atalho antigo de evolução continua aceito", http("PUT", f"{API}/clinic/agenda/{session_id}/evolution", token=psy,
       body={"evolution": "Relatou melhora do sono."}), 204)
expect("paciente NÃO lê o registro da sessão", http("GET", f"{API}/clinic/agenda/{session_id}/record", token=pat), 403)
evo, _ = psql(f"select evolution from {REALM}.session_info where id = '{session_id}'")
check("evolução fica cifrada no banco", evo.startswith("enc:v1:"), evo[:30])
converted = expect("registro depois do primeiro salvamento", http("GET", f"{API}/clinic/agenda/{session_id}/record", token=psy), 200)
left, _ = psql(f"select count(*) from {REALM}.annotation where session_id = '{session_id}'")
check("anotação antiga foi convertida para o registro novo, sem perder texto",
      left == "0" and converted["interventions"] == "Psicoeducação" and converted["evolution"] == "Relatou melhora do sono.", (left, converted))

single = expect("sessão avulsa com valor próprio", http("POST", f"{API}/clinic/agenda/single", token=psy, body={
    "patientId": pat_id, "price": "180.00", "date": str(monday + dt.timedelta(days=2)), "startTime": "14:00"}), 200)
check("sessão avulsa criada", len(single["created"]) == 1, single)

# ------------------------------------------------- documentos, caixa, anexos, guarda
issued = expect("recibo emitido em PDF", http("POST", f"{API}/clinic/documents", token=psy, body={"type": "RECIBO", "referenceId": payment["id"]}), 200)
again = expect("pedir o mesmo recibo devolve o mesmo número", http("POST", f"{API}/clinic/documents", token=pat, body={"type": "RECIBO", "referenceId": payment["id"]}), 200)
check("numeração não duplica", issued["id"] == again["id"] and "/" in issued["number"], (issued, again))
code, pdf, _ = http("GET", f"{API}/clinic/documents/{issued['id']}/pdf", token=pat)
check(f"paciente baixa o PDF do recibo [{code}]", code == 200 and str(pdf).startswith("%PDF"), str(pdf)[:20])
expect("contrato emitido pelo profissional", http("POST", f"{API}/clinic/documents", token=psy, body={"type": "CONTRATO", "referenceId": pat_id}), 200)
expect("paciente NÃO emite contrato", http("POST", f"{API}/clinic/documents", token=pat, body={"type": "CONTRATO", "referenceId": pat_id}), 403)
docs = expect("documentos emitidos do paciente", http("GET", f"{API}/clinic/documents?patientId={pat_id}", token=psy), 200)
check("lista traz recibo e contrato", {d["type"] for d in docs} >= {"RECIBO", "CONTRATO"}, docs)

psql(f"delete from {REALM}.expense where owner_id = '{psy_id}'")
expense = expect("profissional lança despesa", http("POST", f"{API}/clinic/expenses", token=psy, body={
    "date": str(now.date()), "description": "Aluguel da sala", "category": "Aluguel", "amount": "120.00", "deductible": True}), 200)
year = expect("resumo do ano", http("GET", f"{API}/clinic/finance/year?year={now.year}", token=psy), 200)
check("resultado do ano = recebido - despesas", abs(float(year["result"]) - (float(year["income"]) - 120.0)) < 0.01, year["result"])
code, csv, _ = http("GET", f"{API}/clinic/finance/year.csv?year={now.year}", token=psy)
check(f"exportação CSV para o contador [{code}]", code == 200 and "Aluguel" in str(csv) and "120,00" in str(csv), str(csv)[:120])
expect("paciente NÃO vê o livro-caixa", http("GET", f"{API}/clinic/finance/year", token=pat), 403)

import base64
attached = expect("anexo no prontuário", http("POST", f"{API}/clinic/patient-record/{pat_id}/attachments", token=psy, body={
    "fileName": "laudo.txt", "contentType": "text/plain", "content": base64.b64encode(b"laudo de teste").decode()}), 200)
code, content, _ = http("GET", f"{API}/clinic/attachments/{attached['id']}", token=psy)
check(f"download do anexo [{code}]", code == 200 and "laudo de teste" in str(content), str(content)[:40])
expect("tipo de arquivo não aceito é recusado", http("POST", f"{API}/clinic/patient-record/{pat_id}/attachments", token=psy, body={
    "fileName": "x.exe", "contentType": "application/x-msdownload", "content": "AAAA"}), 400)
expect("paciente NÃO acessa anexos", http("GET", f"{API}/clinic/patient-record/{pat_id}/attachments", token=pat), 403)
expect("remove o anexo", http("DELETE", f"{API}/clinic/attachments/{attached['id']}", token=psy), 204)

retention = expect("administrador consulta a guarda de prontuários", http("GET", f"{API}/clinic/retention", token=admin), 200)
check("paciente em guarda obrigatória", any(p["patientId"] == pat_id and not p["disposable"] for p in retention["patients"]), retention)
kept = expect("excluir paciente com prontuário no prazo é recusado", http("DELETE", f"{API}/user/{pat_id}", token=admin), 409)
check("mensagem diz até quando guardar", "guardado até" in str(kept), kept)

# ------------------------------------ modelo de acesso: cadastro compartilhado e foto
psy2_id = ensure_user("90000000004", "Outra", "PROFISSIONAL", {"crp": "06/654321"})
psy2 = login("90000000004")
psql(f"delete from {REALM}.patient_professional where professional_id = '{psy2_id}'")
psql(f"delete from {REALM}.patient_record where professional_id = '{psy2_id}'")
before = expect("outra profissional lista usuários", http("GET", f"{API}/user?page=0&size=50", token=psy2), 200)
check("ela não vê o paciente de outro profissional", all(u["id"] != pat_id for u in before["content"]), [u["firstName"] for u in before["content"]])
expect("nem abre o prontuário dele", http("GET", f"{API}/clinic/patient-record/{pat_id}", token=psy2), 403)
expect("nem cria pacote para ele", http("POST", f"{API}/session-package", token=psy2, body={"patient": {"id": pat_id}, "packageTitle": "x"}), 403)
linked = expect("cadastrar pelo CPF vincula o cadastro existente", http("POST", f"{API}/user", token=psy2, body={
    "cpf": "90000000003", "firstName": "Qualquer", "lastName": "Nome", "email": "qualquer@exemplo.com",
    "phoneNumber": "11955554444", "birthDate": "1990-05-10", "userType": "PACIENTE"}), 200)
check("devolve o mesmo cadastro, sem duplicar", linked["id"] == pat_id and linked["firstName"] == "Paciente", linked)
after = expect("agora o paciente aparece para ela", http("GET", f"{API}/user?page=0&size=50", token=psy2), 200)
check("cadastro compartilhado visível", any(u["id"] == pat_id for u in after["content"]), [u["firstName"] for u in after["content"]])
own = expect("o prontuário dela começa vazio", http("GET", f"{API}/clinic/patient-record/{pat_id}", token=psy2), 200)
check("não vê a anamnese do outro profissional", own.get("chiefComplaint") is None, own)
other_timeline = expect("nem a evolução do outro", http("GET", f"{API}/clinic/patient-record/{pat_id}/timeline", token=psy2), 200)
check("linha do tempo dela está vazia", other_timeline == [], len(other_timeline))
expect("ela registra a própria anamnese", http("PUT", f"{API}/clinic/patient-record/{pat_id}", token=psy2, body={"chiefComplaint": "Insônia"}), 204)
mine = expect("a anamnese do primeiro profissional continua a dele", http("GET", f"{API}/clinic/patient-record/{pat_id}", token=psy), 200)
check("anamneses separadas por profissional", mine.get("chiefComplaint") == "Ansiedade", mine.get("chiefComplaint"))
admin_view = expect("administrador vê as duas anamneses", http("GET", f"{API}/clinic/patient-record/{pat_id}", token=admin), 200)
check("administrador vê tudo, só para leitura", admin_view.get("readOnly") is True and len(admin_view["records"]) == 2, admin_view.get("records"))
expect("administrador NÃO altera a anamnese", http("PUT", f"{API}/clinic/patient-record/{pat_id}", token=admin, body={"chiefComplaint": "x"}), 403)
admin_timeline = expect("administrador vê a evolução de todos", http("GET", f"{API}/clinic/patient-record/{pat_id}/timeline", token=admin), 200)
check("linha do tempo do administrador diz de quem é cada sessão", admin_timeline and all(i.get("professionalName") for i in admin_timeline), admin_timeline[:1])

PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
profile = expect("perfil do paciente", http("GET", f"{API}/user/keycloak", token=pat), 200)
profile["profileImage"] = PNG
saved_profile = expect("paciente salva a foto de perfil", http("PUT", f"{API}/user/{pat_id}", token=pat, body=profile), 200)
check("foto volta gravada", saved_profile.get("profileImage") == PNG, str(saved_profile.get("profileImage"))[:30])
again_profile = expect("foto continua lá ao recarregar", http("GET", f"{API}/user/keycloak", token=pat), 200)
check("foto persistida no banco", again_profile.get("profileImage") == PNG, str(again_profile.get("profileImage"))[:30])

# ------------------------------------------- registro clínico da sessão (CFP 001/2009)
rec_id = single["created"][0]
RECORD = {"demand": "Dificuldade para dormir", "observations": "Ansioso, fala acelerada",
          "interventions": "Higiene do sono", "evolution": "Boa adesão", "plan": "Diário do sono por 7 dias",
          "riskAlert": True, "privateNotes": "hipótese a confirmar em supervisão"}
saved_rec = expect("profissional escreve o registro estruturado", http("PUT", f"{API}/clinic/agenda/{rec_id}/record", token=psy, body=RECORD), 200)
check("registro volta com as cinco seções", all(saved_rec.get(k) == RECORD[k] for k in ("demand", "observations", "interventions", "evolution", "plan")), saved_rec)
raw_rec, _ = psql(f"select demand || '|' || plan || '|' || private_notes from {REALM}.session_info where id = '{rec_id}'")
check("seções e nota privada cifradas no banco", all(part.startswith("enc:v1:") for part in raw_rec.split("|")), raw_rec[:40])
expect("outro profissional NÃO lê o registro", http("GET", f"{API}/clinic/agenda/{rec_id}/record", token=psy2), 403)
expect("administrador NÃO escreve o registro", http("PUT", f"{API}/clinic/agenda/{rec_id}/record", token=admin, body={"demand": "x"}), 403)
admin_rec = expect("administrador lê o registro", http("GET", f"{API}/clinic/agenda/{rec_id}/record", token=admin), 200)
check("mas não lê a nota privada do profissional", admin_rec.get("demand") == RECORD["demand"] and "privateNotes" not in admin_rec, admin_rec)
expect("adendo só depois de concluir", http("POST", f"{API}/clinic/agenda/{rec_id}/record/addendum", token=psy, body={"text": "x"}), 409)
signed_rec = expect("profissional conclui o registro", http("PUT", f"{API}/clinic/agenda/{rec_id}/record/sign", token=psy), 200)
check("registro concluído fica travado", signed_rec.get("signedAt") and signed_rec.get("canEdit") is False, signed_rec)
expect("registro concluído não é alterado", http("PUT", f"{API}/clinic/agenda/{rec_id}/record", token=psy, body={"demand": "outra"}), 409)
added = expect("complemento entra como adendo datado", http("POST", f"{API}/clinic/agenda/{rec_id}/record/addendum", token=psy,
               body={"text": "Paciente avisou que iniciou medicação."}), 200)
check("adendo gravado sem mexer no texto", added["addenda"].endswith("iniciou medicação.") and added["demand"] == RECORD["demand"], added)
tl = expect("linha do tempo traz o registro novo", http("GET", f"{API}/clinic/patient-record/{pat_id}/timeline", token=psy), 200)
tl_item = next(i for i in tl if i["id"] == rec_id)
check("linha do tempo mostra seções, alerta e conclusão", tl_item["plan"] == RECORD["plan"] and tl_item["riskAlert"] and tl_item["signedAt"], tl_item)
form_meta = expect("formulário genérico da sessão", http("GET", f"{API}/session/entity-description", token=psy), 200)
check("o formulário genérico não edita mais o texto clínico",
      not any(k in str(form_meta) for k in ('"evolution"', "'evolution'", "privateNotes", "annotation")), str(form_meta)[:200])

# ------------------------------------------------- perfil público e avaliações
BIO = "Atendo adultos e adolescentes com foco em ansiedade e sono, em sessões de 50 minutos, online e presencial."
expect("paciente NÃO edita perfil público", http("PUT", f"{API}/directory/profile", token=pat, body={"bio": BIO}), 403)
expect("publicar sem texto de apresentação é recusado", http("PUT", f"{API}/directory/profile", token=psy, body={"published": True, "bio": "curto"}), 400)
draft = expect("profissional salva rascunho", http("PUT", f"{API}/directory/profile", token=psy, body={
    "headline": "Psicóloga clínica · TCC", "bio": BIO, "specialties": ["Ansiedade", "Sono"], "approaches": ["Terapia Cognitivo-Comportamental"],
    "audiences": ["Adultos", "Adolescentes"], "languages": ["Português"], "education": "Graduação em Psicologia — USP, 2012",
    "practicingSince": 2013, "sessionMinutes": 50, "sessionPrice": "180.00", "attendsOnline": True, "attendsInPerson": True,
    "city": "Barueri, SP", "showReviews": False, "published": False}), 200)
check("rascunho não publicado", draft["published"] is False and draft["specialties"] == ["Ansiedade", "Sono"], draft)
hidden = expect("paciente lista profissionais", http("GET", f"{API}/directory/professionals", token=pat), 200)
check("rascunho não aparece para o paciente", all(c["id"] != psy_id for c in hidden), [c["name"] for c in hidden])
expect("nem abre o rascunho", http("GET", f"{API}/directory/professionals/{psy_id}", token=pat), 404)
admin_list = expect("administrador lista profissionais", http("GET", f"{API}/directory/professionals", token=admin), 200)
check("administrador vê o rascunho", any(c["id"] == psy_id and not c["published"] for c in admin_list), admin_list)
expect("profissional publica o perfil", http("PUT", f"{API}/directory/profile", token=psy, body=dict(draft, published=True)), 200)
listed = expect("paciente busca por área de atuação", http("GET", f"{API}/directory/professionals?search=ansiedade", token=pat), 200)
card = next((c for c in listed if c["id"] == psy_id), None)
check("perfil publicado aparece com CRP", card is not None and card["registration"] == "06/123456", listed)
check("sem nota enquanto o profissional não escolher mostrar", card is not None and "reviews" not in card, card)
seen = expect("paciente abre o perfil", http("GET", f"{API}/directory/professionals/{psy_id}", token=pat), 200)
check("perfil traz apresentação e não expõe dados do cadastro",
      seen["bio"] == BIO and not any(k in seen for k in ("cpf", "email", "phoneNumber")), list(seen))

mine_reviews = expect("profissional vê as avaliações recebidas", http("GET", f"{API}/directory/reviews", token=psy), 200)
check("com nota e recomendação", mine_reviews["summary"]["count"] >= 1 and mine_reviews["summary"]["recommendPercent"] == 100, mine_reviews["summary"])
check("sem saber quem avaliou", all("authorName" not in i and "authorId" not in i for i in mine_reviews["items"]), mine_reviews["items"][:1])
expect("outro profissional NÃO vê as avaliações dela", http("GET", f"{API}/directory/reviews?professionalId={psy_id}", token=psy2), 403)
expect("paciente NÃO vê a fila de moderação", http("GET", f"{API}/directory/reviews/moderation", token=pat), 403)
queue = expect("administrador vê a fila de moderação", http("GET", f"{API}/directory/reviews/moderation", token=admin), 200)
pending = next((q for q in queue if q["subjectId"] == psy_id), None)
check("comentário autorizado pelo paciente aguarda aprovação", pending is not None and pending["authorName"] == "Paciente Teste", queue)
expect("profissional NÃO aprova o próprio comentário", http("PUT", f"{API}/directory/reviews/{pending['id']}/publish", token=psy), 403)
expect("administrador aprova o comentário", http("PUT", f"{API}/directory/reviews/{pending['id']}/publish", token=admin), 204)
still = expect("perfil sem avaliações ligadas", http("GET", f"{API}/directory/professionals/{psy_id}", token=pat), 200)
check("comentário aprovado continua oculto se o profissional não mostra avaliações", "reviews" not in still and "comments" not in still, list(still))
expect("profissional liga a exibição das avaliações", http("PUT", f"{API}/directory/profile", token=psy, body=dict(draft, published=True, showReviews=True)), 200)
opened = expect("paciente abre o perfil com avaliações", http("GET", f"{API}/directory/professionals/{psy_id}", token=pat), 200)
check("nota e comentário aprovado aparecem", opened["reviews"]["count"] >= 1 and len(opened["comments"]) == 1, opened.get("comments"))
check("comentário público é anônimo", not any(k in opened["comments"][0] for k in ("authorId", "authorName", "id")), opened["comments"][0])
expect("rota antiga de avaliação sem verificação não existe mais", http("POST", f"{API}/session-rating", token=pat, body={"rating": 1}), 404)

# -------------------------------------------------------------------- painel
for label, token in (("admin", admin), ("psicóloga", psy), ("paciente", pat)):
    dash = expect(f"painel do perfil {label}", http("GET", f"{API}/clinic/dashboard", token=token), 200)
    check(f"painel {label} tem a lista de hoje", isinstance(dash.get("today"), list), dash)

failed = [name for name, ok in results if not ok]
print(f"\n{len(results) - len(failed)}/{len(results)} verificações passaram")
print(json.dumps({"psy_id": psy_id, "pat_id": pat_id, "pkg_id": pkg_id, "session_id": session_id, "payment_id": payment["id"]}))
sys.exit(1 if failed else 0)
