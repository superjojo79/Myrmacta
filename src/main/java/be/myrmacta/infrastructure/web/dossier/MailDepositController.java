package be.myrmacta.infrastructure.web.dossier;

import be.myrmacta.domain.connector.model.ConnectorInstanceNotFoundException;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierFieldValidationException;
import be.myrmacta.domain.dossier.model.InactiveDossierTypeException;
import be.myrmacta.domain.mailroom.model.DepositFile;
import be.myrmacta.domain.mailroom.model.DepositPoint;
import be.myrmacta.domain.mailroom.model.DepositRefusedException;
import be.myrmacta.domain.mailroom.model.DepositResult;
import be.myrmacta.domain.mailroom.port.in.DepositMailUseCase;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * TODO §113 (D-MAILROOM-DEPOSIT) — écran « Déposer » : un agent dépose un ou plusieurs PDF ; chacun devient un courrier du point de
 * dépôt choisi, dans sa propre transaction. Un fichier refusé n'empêche pas les autres ; la réponse donne le sort de chacun.
 * Droit requis : EDIT sur le type de dossier du point de dépôt (ACL du type), comme pour créer un dossier.
 */
@RestController
@RequestMapping("/api/mail-deposit-points")
public class MailDepositController {

    private static final Logger log = LoggerFactory.getLogger(MailDepositController.class);

    private final DepositMailUseCase deposits;
    private final AccessContextFactory accessContextFactory;
    private final TransactionTemplate transaction;

    public MailDepositController(DepositMailUseCase deposits, AccessContextFactory accessContextFactory,
            PlatformTransactionManager transactionManager) {
        this.deposits = deposits;
        this.accessContextFactory = accessContextFactory;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @GetMapping
    public List<DepositPointResponse> listMailDepositPoints(Authentication authentication) {
        return deposits.points(accessContextFactory.user(authentication)).stream().map(DepositPointResponse::from).toList();
    }

    @PostMapping(path = "/{id}/deposits", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DepositResponse depositMail(@PathVariable UUID id, @RequestParam("files") List<MultipartFile> files,
            @RequestParam(name = "expediteur", required = false) String sender,
            @RequestParam(name = "objet", required = false) String subject, Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        deposits.point(id, actor);
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one file is required");
        }
        if (files.size() > DepositMailUseCase.MAX_FILES) {
            throw new IllegalArgumentException("At most " + DepositMailUseCase.MAX_FILES + " files per deposit");
        }
        List<DepositResult> results = new ArrayList<>();
        for (MultipartFile file : files) {
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            try {
                DepositFile deposit = new DepositFile(name, file.getBytes());
                results.add(transaction.execute(status -> deposits.deposit(id, deposit, sender, subject, actor)));
            } catch (DepositRefusedException | DossierFieldValidationException | InactiveDossierTypeException
                    | ConnectorValidationException | IllegalArgumentException refused) {
                results.add(DepositResult.refused(name, refused.getMessage()));
            } catch (IOException unreadable) {
                throw new UncheckedIOException(unreadable);
            } catch (RuntimeException failure) {
                // Message neutre vers le navigateur : le détail technique reste dans le journal.
                log.warn("mail deposit: file could not be taken in charge ({})", failure.getClass().getSimpleName(), failure);
                results.add(DepositResult.refused(name, "prise en charge impossible"));
            }
        }
        return new DepositResponse(results.stream().map(DepositResultResponse::from).toList());
    }

    @ExceptionHandler(ConnectorInstanceNotFoundException.class)
    ProblemDetail depositPointNotFound(ConnectorInstanceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Deposit point not found");
        problem.setTitle("Not found");
        return problem;
    }

    record DepositPointResponse(UUID id, String name, String dossierTypeCode) {

        static DepositPointResponse from(DepositPoint point) {
            return new DepositPointResponse(point.id(), point.name(), point.dossierTypeCode());
        }
    }

    record DepositResultResponse(String filename, UUID dossierId, String reference, String refusal) {

        static DepositResultResponse from(DepositResult result) {
            return new DepositResultResponse(result.filename(), result.dossierId(), result.reference(), result.refusal());
        }
    }

    record DepositResponse(List<DepositResultResponse> results) {}
}
