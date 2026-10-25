/**
 * Annuaire local des utilisateurs (D-ASSIGN-DIR) : alimenté à chaque connexion OIDC, il sert à
 * proposer des assignés lisibles. Ce n'est pas une source d'autorité — les rôles enregistrés sont
 * ceux du dernier jeton vu, l'IdP reste la référence.
 */
package be.myrmacta.domain.user;
