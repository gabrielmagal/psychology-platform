/**
 * Os passos do plano de segurança para crises, no modelo de Stanley e Brown: a pessoa segue
 * em ordem, do que consegue fazer sozinha até acionar ajuda profissional.
 */
export const SAFETY_STEPS = [
    { key: "warningSigns", label: "1. Sinais de alerta",
        hint: "Pensamentos, imagens, humor, situações ou comportamentos que mostram que uma crise pode estar começando.",
        placeholder: "Ex.: Ficar duas noites sem dormir; pensar que sou um peso; me isolar no quarto." },
    { key: "coping", label: "2. O que posso fazer sozinho(a)",
        hint: "Coisas que ajudam a atravessar o momento sem precisar de outra pessoa.",
        placeholder: "Ex.: Respirar contando até 4; tomar um banho; caminhar no quarteirão; ouvir minha playlist." },
    { key: "distractions", label: "3. Pessoas e lugares que me distraem",
        hint: "Companhias e ambientes que tiram o foco da crise, sem precisar falar dela.",
        placeholder: "Ex.: Ir à padaria da esquina; ligar para o João para falar de futebol; ficar na praça." },
    { key: "supportPeople", label: "4. Pessoas a quem posso pedir ajuda",
        hint: "Quem sabe do plano e pode ser procurado. Anote o nome e o telefone.",
        placeholder: "Ex.: Ana (irmã) — (11) 91111-2222" },
    { key: "professionals", label: "5. Profissionais e serviços que posso acionar",
        hint: "Seu psicólogo, psiquiatra e os serviços de urgência da sua cidade.",
        placeholder: "Ex.: Dra. Marta (psiquiatra) — (11) 3333-4444; UPA da Vila Nova, Rua das Flores, 100." },
    { key: "safeEnvironment", label: "6. Como deixar meu ambiente seguro",
        hint: "O que afastar ou guardar com outra pessoa nos momentos difíceis.",
        placeholder: "Ex.: Deixar os remédios com minha irmã; não ficar sozinho(a) em casa à noite." },
    { key: "reasonsToLive", label: "O que vale a pena para mim",
        hint: "Pessoas, planos e coisas que importam e que quero lembrar nessa hora.",
        placeholder: "Ex.: Meus filhos; terminar a faculdade; meu cachorro." },
];

/** Serviços públicos e gratuitos, disponíveis 24 horas em todo o Brasil. */
export const EMERGENCY_SERVICES = [
    { name: "CVV", number: "188", description: "Apoio emocional, 24 horas, ligação gratuita. Também por chat em cvv.org.br." },
    { name: "SAMU", number: "192", description: "Urgência médica, 24 horas." },
];

export const telLink = (phone) => `tel:${String(phone || "").replace(/[^\d+]/g, "")}`;
