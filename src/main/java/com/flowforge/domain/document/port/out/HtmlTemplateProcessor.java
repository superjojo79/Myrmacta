package com.flowforge.domain.document.port.out;

import com.flowforge.domain.document.model.TemplateProcessingException;
import java.util.Map;

@FunctionalInterface
public interface HtmlTemplateProcessor {

    /**
     * Merges Thymeleaf HTML with variables (full evaluation).
     *
     * @throws TemplateProcessingException when evaluation fails
     */
    String process(String htmlContent, Map<String, Object> variables) throws TemplateProcessingException;
}
