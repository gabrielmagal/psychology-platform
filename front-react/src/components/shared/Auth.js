export function getUserRoles(keycloak) {
    return keycloak?.tokenParsed?.realm_access?.roles || [];
}

export function hasRole(keycloak, role) {
    return getUserRoles(keycloak).includes(role);
}