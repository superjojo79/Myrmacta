package com.flowforge.infrastructure.pdf;

import com.flowforge.domain.document.model.DocumentRenderException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.openpdf.text.DocumentException;
import org.openpdf.text.pdf.BaseFont;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.xhtmlrenderer.pdf.ITextFontResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

/**
 * Registers TTF/OTF faces from {@code classpath:/fonts/**} into Flying Saucer's font
 * resolver (requirement A8). WOFF/WOFF2 and network {@code @font-face} sources are out
 * of scope — only classpath TrueType/OpenType binaries are loaded.
 */
final class ClasspathFontRegistrar {

    private static final String[] FONT_PATTERNS = {
        "classpath*:/fonts/**/*.ttf",
        "classpath*:/fonts/**/*.otf",
        "classpath*:/fonts/**/*.TTF",
        "classpath*:/fonts/**/*.OTF"
    };

    private final ResourcePatternResolver resources;

    ClasspathFontRegistrar() {
        this(new PathMatchingResourcePatternResolver());
    }

    ClasspathFontRegistrar(ResourcePatternResolver resources) {
        this.resources = resources;
    }

    void registerInto(ITextRenderer renderer) {
        ITextFontResolver fontResolver = renderer.getFontResolver();
        for (Resource font : listFontResources()) {
            String path = toLoadablePath(font);
            try {
                fontResolver.addFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (DocumentException | IOException ex) {
                throw new DocumentRenderException(
                        "Failed to register classpath font: " + path + " — " + ex.getMessage(), ex);
            }
        }
    }

    private List<Resource> listFontResources() {
        List<Resource> fonts = new ArrayList<>();
        try {
            for (String pattern : FONT_PATTERNS) {
                for (Resource resource : resources.getResources(pattern)) {
                    if (resource.exists() && resource.isReadable() && isTtfOrOtf(resource)) {
                        fonts.add(resource);
                    }
                }
            }
        } catch (IOException ex) {
            throw new DocumentRenderException(
                    "Failed to scan classpath fonts under fonts/**: " + ex.getMessage(), ex);
        }
        return fonts;
    }

    private static boolean isTtfOrOtf(Resource resource) {
        String name = resource.getFilename();
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ttf") || lower.endsWith(".otf");
    }

    /**
     * OpenPDF resolves fonts either as filesystem paths or as ClassLoader resource names
     * (e.g. {@code fonts/LiberationSans-Regular.ttf}). Prefer the classpath-relative form
     * so registration works inside Spring Boot fat jars.
     */
    static String toLoadablePath(Resource resource) {
        try {
            if (resource instanceof ClassPathResource classPathResource) {
                return classPathResource.getPath();
            }
            URL url = resource.getURL();
            String external = url.toExternalForm();
            int bang = external.indexOf("!/");
            if (bang >= 0) {
                return external.substring(bang + 2);
            }
            if (resource.isFile()) {
                return resource.getFile().getAbsolutePath();
            }
            throw new DocumentRenderException("Cannot resolve loadable path for font resource: " + resource);
        } catch (IOException ex) {
            throw new DocumentRenderException(
                    "Cannot resolve loadable path for font resource: " + resource + " — " + ex.getMessage(),
                    ex);
        }
    }
}
