package com.flowforge.infrastructure.connector.builtin;

/**
 * TODO §87 (D3c-bis, revue D-INTEG-MAIL-PROTOCOL) — accès à une boîte Exchange Online par Microsoft Graph : mêmes règles que
 * {@link ImapMailbox} (lit les messages d'un dossier, les confie au gestionnaire, puis range chacun dans « traités » ou « refusés »,
 * jamais supprimé), mêmes types de message ({@code ImapMailbox.Mail}, {@code Limits}, {@code Outcome}, {@code Handler}).
 */
interface GraphMailbox {

    /**
     * @throws RuntimeException connexion, authentification, limitation ou panne du service : rien n'est rangé, la prochaine
     *     interrogation reprendra les mêmes messages
     */
    void process(Settings settings, int maxMessages, ImapMailbox.Limits limits, ImapMailbox.Handler handler);

    record Settings(
            /** Racine de l'API, par exemple {@code https://graph.microsoft.com/v1.0}. */
            String baseUrl,
            /** Boîte lue : nom d'utilisateur principal ou identifiant d'objet. */
            String mailbox,
            String accessToken,
            String folder,
            String processedFolder,
            String rejectedFolder,
            int timeoutMillis) {

        @Override
        public String toString() {
            return "Settings[baseUrl=" + baseUrl + ", mailbox=" + mailbox + ", folder=" + folder + "]";
        }
    }
}
