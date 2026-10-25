package be.myrmacta.domain.document.port.out;

import be.myrmacta.domain.document.model.InvalidTemplateSyntaxException;

@FunctionalInterface
public interface TemplateSyntaxChecker {

    /**
     * Validates Thymeleaf syntax only (no business variables required).
     *
     * @throws InvalidTemplateSyntaxException when markup is invalid
     */
    void check(String htmlContent) throws InvalidTemplateSyntaxException;
}
