"""Teste de fumaça das telas.

Entra como cada perfil (administrador, profissional, paciente), abre cada tela e confere que
ela carregou: o título esperado aparece e o navegador não registrou erro. Salva uma captura
de cada tela em e2e/screenshots, para conferência visual.

Pré-requisitos: API e front no ar, e os usuários de teste criados por `api_flow.py`.
"""
import os
import sys

from playwright.sync_api import sync_playwright

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "screenshots")
os.makedirs(OUT, exist_ok=True)


def env(name, default=None):
    value = os.environ.get(name, default)
    if value is None:
        raise SystemExit(f"Defina a variável {name} (veja e2e/.env.example).")
    return value


BASE = env("E2E_FRONT_URL", "http://localhost:3000") + "/" + env("E2E_REALM", "barueri")
PASSWORD = env("E2E_TEST_USER_PASSWORD")
USERS = {"admin": "teste.admin", "profissional": "90000000002", "paciente": "90000000003"}

# (perfil, rota, texto que tem de aparecer, cliques opcionais antes da captura)
SCREENS = [
    ("profissional", "/", "Atalhos", []),
    ("profissional", "/agenda", "Agenda", []),
    ("profissional", "/agenda", "Agenda", ["role=tab[name='Mês']"]),
    ("profissional", "/agenda", "Agenda", ["role=tab[name='Dia']"]),
    ("profissional", "/agenda", "Horário de atendimento", ["text=Horários e bloqueios"]),
    ("profissional", "/agenda", "Sessão avulsa", ["text=Agendar sessão"]),
    ("profissional", "/usuarios", "Pacientes e usuários", []),
    ("profissional", "/usuarios", "Dados pessoais", ["text=Novo usuário"]),
    ("profissional", "/sessions-package", "Pacotes de sessões", []),
    ("profissional", "/financeiro", "Recebido no mês", []),
    ("profissional", "/financeiro", "Lançar despesa", ["role=tab[name='Despesas']"]),
    ("profissional", "/financeiro", "Base Carnê-Leão", ["role=tab[name='Ano e Carnê-Leão']"]),
    ("profissional", "/financeiro", "Falta lançar", ["role=tab[name='Receita Saúde']"]),
    ("profissional", "/convenios", "Sessões realizadas no mês", []),
    ("profissional", "/convenios", "Convênios da clínica", ["role=tab[name='Convênios']"]),
    ("profissional", "/lista-de-espera", "Lista de espera", []),
    ("admin", "/lista-de-espera", "Lista de espera", []),
    ("profissional", "/recebimento-online", "Recebimento online", []),
    ("profissional", "/perfil", "Meu perfil", []),
    ("admin", "/usuarios", "Pacientes e usuários", []),
    ("admin", "/audit-log", "Auditoria", []),
    ("admin", "/audit-log", "Antes", ["text=Ver detalhes >> nth=0"]),
    ("admin", "/guarda", "Guarda de prontuários", []),
    ("paciente", "/", "Atalhos", []),
    ("paciente", "/agenda", "Agenda", []),
    ("paciente", "/sessions-package", "Meus pacotes", []),
    ("paciente", "/diario", "Diário de humor", []),
]


def login(page, username):
    page.goto(BASE + "/", wait_until="domcontentloaded")
    page.wait_for_selector("#username", timeout=30000)
    page.fill("#username", username)
    page.fill("#password", PASSWORD)
    page.click("#kc-login")
    page.wait_for_selector("nav[aria-label='Menu principal']", timeout=30000)


def main():
    failures = []
    only = sys.argv[1:]  # opcional: filtrar por perfil
    with sync_playwright() as p:
        browser = p.chromium.launch()
        sessions = {}
        for index, (role, route, expected, clicks) in enumerate(SCREENS):
            if only and role not in only:
                continue
            if role not in sessions:
                page = browser.new_context(viewport={"width": 1366, "height": 800}, locale="pt-BR").new_page()
                errors = []
                page.on("console", lambda m, errors=errors: errors.append(m.text) if m.type == "error" else None)
                page.on("pageerror", lambda e, errors=errors: errors.append("ERRO DE PÁGINA " + str(e)))
                login(page, USERS[role])
                sessions[role] = (page, errors)
            page, errors = sessions[role]
            errors.clear()
            name = f"{index:02d}-{role}{route.replace('/', '_') or '_inicio'}{'-' + str(len(clicks)) if clicks else ''}"
            problem = None
            try:
                page.goto(BASE + route, wait_until="networkidle")
                for selector in clicks:
                    page.click(selector, timeout=8000)
                    page.wait_for_timeout(700)
                page.wait_for_selector(f"text={expected}", timeout=8000)
            except Exception as e:  # noqa: BLE001 - qualquer falha de tela conta
                problem = str(e).splitlines()[0][:160]
            page.wait_for_timeout(400)
            page.screenshot(path=os.path.join(OUT, name + ".png"))
            # 4xx esperados (ex.: paciente sem conta de pagamento) não são erro de tela.
            real_errors = [e for e in errors if "status of 4" not in e]
            if problem or real_errors:
                failures.append((name, problem, real_errors[:2]))
                print("FALHOU", name, problem or "", real_errors[:2])
            else:
                print("ok    ", name)
        browser.close()
    print(f"\n{len(SCREENS) - len(failures)} de {len(SCREENS)} telas sem problema. Capturas em {OUT}")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
