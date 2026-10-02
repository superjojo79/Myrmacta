/**
 * Mode « base embarquée persistante » pour les démos et la QA (profil Spring {@code embedded-db}).
 *
 * <p>Ce paquet ne contient que la partie neutre (paramètres, contrat {@link
 * com.flowforge.infrastructure.embeddeddb.EmbeddedDbLauncher}, branchement du datasource). L'implémentation
 * zonky vit dans {@code src/embedded} et n'est compilée/empaquetée que par le profil Maven
 * {@code embedded-db} : le jar de production ne contient ni zonky ni ses binaires PostgreSQL.
 */
package com.flowforge.infrastructure.embeddeddb;
