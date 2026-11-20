package be.myrmacta.domain.ebox.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * TODO §155 (D-EBOX-SEND-MODEL) — invitation à payer jointe à un message citoyen (extension citoyen). Champs repris de
 * {@code PaymentDataToPublish} (e-Box REST 3.8) : montant total, devise ISO 4217, IBAN, BIC, bénéficiaire, communication
 * structurée belge, échéance. IBAN et communication structurée sont vérifiés par leur clé modulo 97.
 */
public record EboxPayment(
        BigDecimal totalAmount,
        String currency,
        String iban,
        String bic,
        String beneficiary,
        String structuredReference,
        OffsetDateTime dueDate) {

    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    private static final Pattern IBAN = Pattern.compile("[A-Z]{2}\\d{2}[A-Z0-9]{11,30}");
    private static final Pattern BIC = Pattern.compile("[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?");
    private static final Pattern STRUCTURED = Pattern.compile("\\+\\+\\+(\\d{3})/(\\d{4})/(\\d{5})\\+\\+\\+");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.99");

    public EboxPayment {
        Objects.requireNonNull(totalAmount, "totalAmount");
        if (totalAmount.signum() <= 0 || totalAmount.scale() > 2 || totalAmount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("payment totalAmount must be positive, with at most two decimals");
        }
        currency = currency == null || currency.isBlank() ? "EUR" : currency.strip();
        if (!CURRENCY.matcher(currency).matches()) {
            throw new IllegalArgumentException("payment currency must be an ISO 4217 alpha code");
        }
        iban = iban == null ? "" : iban.replace(" ", "").toUpperCase(Locale.ROOT);
        if (!IBAN.matcher(iban).matches() || !ibanChecksumMatches(iban)) {
            throw new IllegalArgumentException("payment iban is not a valid IBAN");
        }
        if (bic != null && !bic.isBlank()) {
            bic = bic.strip().toUpperCase(Locale.ROOT);
            if (!BIC.matcher(bic).matches()) {
                throw new IllegalArgumentException("payment bic is not a valid BIC");
            }
        } else {
            bic = null;
        }
        beneficiary = EboxTexts.line(beneficiary, "payment beneficiary", 140);
        if (structuredReference != null && !structuredReference.isBlank()) {
            structuredReference = structuredReference.strip();
            if (!structuredReferenceMatches(structuredReference)) {
                throw new IllegalArgumentException("payment structuredReference is not a valid Belgian structured communication");
            }
        } else {
            structuredReference = null;
        }
    }

    private static boolean ibanChecksumMatches(String iban) {
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            digits.append(Character.getNumericValue(c));
        }
        return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    private static boolean structuredReferenceMatches(String reference) {
        var matcher = STRUCTURED.matcher(reference);
        if (!matcher.matches()) {
            return false;
        }
        String digits = matcher.group(1) + matcher.group(2) + matcher.group(3);
        long base = Long.parseLong(digits.substring(0, 10));
        int check = Integer.parseInt(digits.substring(10));
        long expected = base % 97 == 0 ? 97 : base % 97;
        return expected == check;
    }
}
