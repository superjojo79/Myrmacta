package com.flowforge.domain.workflow.portal.port.in;

import com.flowforge.domain.workflow.portal.model.PortalCredential;
import java.net.URI;

/** TODO §104 (T3) — « S'identifier » : ouvre une tentative et renvoie l'URL d'autorisation du fournisseur choisi. */
public interface StartPortalIdentityUseCase {

    URI start(PortalCredential credential, String provider);
}
