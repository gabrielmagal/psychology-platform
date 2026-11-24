import { useCallback, useEffect, useRef } from "react";

/**
 * Devolve uma versão da função que só executa depois de `delay` ms sem novas chamadas.
 *
 * Usado nos campos de autocomplete: a busca era disparada a cada tecla digitada, então
 * escrever "Maria" gerava quatro requisições ao backend — três delas já obsoletas quando
 * chegavam — e cada uma disputava para ser a última a atualizar a lista de opções.
 */
export function useDebouncedCallback(fn, delay = 350) {
    const timeoutRef = useRef(null);
    const fnRef = useRef(fn);

    useEffect(() => {
        fnRef.current = fn;
    }, [fn]);

    useEffect(() => () => clearTimeout(timeoutRef.current), []);

    return useCallback((...args) => {
        clearTimeout(timeoutRef.current);
        timeoutRef.current = setTimeout(() => fnRef.current(...args), delay);
    }, [delay]);
}
