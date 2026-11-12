# /// script
# requires-python = ">=3.11"
# ///
"""Envoie un e-mail du tournage de la capsule « Digital Mail Room » (TODO §128, D-VIDEO-MAIL, issue #56).

L'e-mail part en SMTP vers le GreenMail local (`scripts/video.sh scene`), boîte courrier@demo.example, que le connecteur IMAP
de la démo relève : il suit le vrai chemin (relève, routage, accusé de réception). L'en-tête Authentication-Results porte
l'authserv-id de confiance du connecteur : l'expéditeur est attesté, comme derrière un vrai serveur de réception.

    uv run scripts/video/send_mail.py facture|inconnu|reponse [--lang fr|en] [--smtp localhost:3025]
"""

from __future__ import annotations

import argparse
import smtplib
import sys
from email.message import EmailMessage
from email.utils import formatdate

TRUSTED_AUTHSERV_ID = "mx.demo.example"
MAILBOX = "courrier@demo.example"
SUPPLIER = "compta@fournisseur.example"
INVOICE_ID = "<f117-video@fournisseur.example>"

TEXTS = {
    "fr": {
        "facture": ("Facture 2026-117", "Bonjour,\n\nVeuillez trouver ci-joint notre facture 2026-117, payable à 30 jours.\n\n"
                    "Cordialement,\nLa comptabilité de Fournisseur SA", "facture-2026-117.pdf"),
        "inconnu": ("Bon de commande pour votre facture 2026-117",
                    "Bonjour,\n\nVoici le bon de commande qui correspond à la facture 2026-117 de Fournisseur SA.\n\n"
                    "Bien à vous,\nService achats", "bon-de-commande-4471.pdf"),
        "reponse": ("Re: Facture 2026-117", "Bonjour,\n\nSuite à votre demande, voici la note de crédit pour la facture "
                    "2026-117.\n\nCordialement,\nLa comptabilité de Fournisseur SA", "note-de-credit-2026-117.pdf"),
    },
    "en": {
        "facture": ("Invoice 2026-117", "Hello,\n\nPlease find attached our invoice 2026-117, payable within 30 days.\n\n"
                    "Kind regards,\nSupplier Ltd accounts", "invoice-2026-117.pdf"),
        "inconnu": ("Purchase order for your invoice 2026-117",
                    "Hello,\n\nHere is the purchase order matching Supplier Ltd's invoice 2026-117.\n\n"
                    "Best regards,\nPurchasing", "purchase-order-4471.pdf"),
        "reponse": ("Re: Invoice 2026-117", "Hello,\n\nAs requested, here is the credit note for invoice 2026-117.\n\n"
                    "Kind regards,\nSupplier Ltd accounts", "credit-note-2026-117.pdf"),
    },
}

SENDERS = {"facture": SUPPLIER, "inconnu": "achats@client.example", "reponse": SUPPLIER}
MESSAGE_IDS = {"facture": INVOICE_ID, "inconnu": "<po4471-video@client.example>", "reponse": "<f117-re-video@fournisseur.example>"}


def pdf(text: str) -> bytes:
    """Un PDF minimal mais bien formé (une page, un texte), comme MailRoomDemoPack.pdf."""
    safe = text.replace("\\", "").replace("(", "").replace(")", "")
    content = f"BT /F1 18 Tf 72 720 Td ({safe}) Tj ET"
    body = ("%PDF-1.4\n"
            "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n"
            "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n"
            "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R "
            "/Resources << /Font << /F1 5 0 R >> >> >> endobj\n"
            f"4 0 obj << /Length {len(content)} >> stream\n{content}\nendstream endobj\n"
            "5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n"
            "trailer << /Root 1 0 R >>\n%%EOF\n")
    return body.encode("ascii", errors="ignore")


def message(scenario: str, lang: str) -> EmailMessage:
    subject, body, attachment = TEXTS[lang][scenario]
    mail = EmailMessage()
    # En tête, comme l'écrirait le serveur de réception de confiance (le connecteur ne lit que le premier).
    mail["Authentication-Results"] = f"{TRUSTED_AUTHSERV_ID}; dkim=pass header.d={SENDERS[scenario].split('@')[1]}"
    mail["From"] = SENDERS[scenario]
    mail["To"] = MAILBOX
    mail["Subject"] = subject
    mail["Date"] = formatdate(localtime=True)
    mail["Message-ID"] = MESSAGE_IDS[scenario]
    if scenario == "reponse":
        mail["In-Reply-To"] = INVOICE_ID
        mail["References"] = INVOICE_ID
    mail.set_content(body)
    mail.add_attachment(pdf(subject), maintype="application", subtype="pdf", filename=attachment)
    return mail


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("scenario", choices=sorted(SENDERS))
    parser.add_argument("--lang", choices=sorted(TEXTS), default="fr")
    parser.add_argument("--smtp", default="localhost:3025", help="hôte:port du GreenMail (défaut localhost:3025)")
    args = parser.parse_args(argv)
    host, _, port = args.smtp.partition(":")
    mail = message(args.scenario, args.lang)
    with smtplib.SMTP(host, int(port or 25), timeout=10) as smtp:
        smtp.send_message(mail)
    print(f"sent {args.scenario} ({args.lang}): {mail['Subject']} -> {MAILBOX}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
