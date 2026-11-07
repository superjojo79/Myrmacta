package be.myrmacta.domain.deadline.port.out;

/**
 * TODO §121 (D-CAL-ICS-URL) — télécharge le fichier d'une source ICS par URL. L'implémentation n'appelle que ce que la
 * garde réseau admet (https, jamais une adresse interne), sans redirection ni authentification, dans un délai et une
 * taille bornés. L'URL est un secret : aucune exception ni aucun journal ne la reprend, au plus son hôte.
 */
public interface IcsFetcher {

    /** @throws IllegalArgumentException URL refusée, hôte injoignable, réponse en erreur, trop lente ou de plus de 1 Mio */
    byte[] fetch(String url);
}
