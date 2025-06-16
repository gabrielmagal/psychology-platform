import { useMemo } from "react";
import { getUserRoles } from "./Auth";
import {Roles} from "./Roles";

export const useRoles = (keycloak) => {
    const roles = useMemo(() => getUserRoles(keycloak), [keycloak]);
    return {
        isAdmin: roles.includes(Roles.ADMIN),
        isPsychologist: roles.includes(Roles.PSYCHOLOGIST),
        isPatient: roles.includes(Roles.PATIENT),
    };
};