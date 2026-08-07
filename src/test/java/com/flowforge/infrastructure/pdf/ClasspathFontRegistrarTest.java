package com.flowforge.infrastructure.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.flowforge.domain.document.model.DocumentRenderException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

class ClasspathFontRegistrarTest {

    @Test
    void registersShippedLiberationSansFromClasspathFontsDirectory() {
        ITextRenderer renderer = new ITextRenderer();
        new ClasspathFontRegistrar().registerInto(renderer);
        assertThat(renderer.getFontResolver().getFonts()).containsKey("Liberation Sans");
    }

    @Test
    void ignoresNonTtfOtfResourcesEvenIfReturnedByScanner() throws IOException {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        Resource woff = new ByteArrayResource("not-a-font".getBytes()) {
            @Override
            public String getFilename() {
                return "ignored.woff";
            }

            @Override
            public boolean exists() {
                return true;
            }

            @Override
            public boolean isReadable() {
                return true;
            }
        };
        Resource ttf = new ClassPathResource("fonts/LiberationSans-Regular.ttf");
        when(resolver.getResources("classpath*:/fonts/**/*.ttf")).thenReturn(new Resource[] {ttf});
        when(resolver.getResources("classpath*:/fonts/**/*.otf")).thenReturn(new Resource[0]);
        when(resolver.getResources("classpath*:/fonts/**/*.TTF")).thenReturn(new Resource[0]);
        when(resolver.getResources("classpath*:/fonts/**/*.OTF")).thenReturn(new Resource[] {woff});

        ITextRenderer renderer = new ITextRenderer();
        new ClasspathFontRegistrar(resolver).registerInto(renderer);

        assertThat(renderer.getFontResolver().getFonts()).containsKey("Liberation Sans");
        assertThat(renderer.getFontResolver().getFonts().keySet())
                .noneMatch(name -> name.toLowerCase().contains("woff"));
    }

    @Test
    void unreadableFont_failsClearly() throws IOException {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        Resource broken = mock(Resource.class);
        when(broken.exists()).thenReturn(true);
        when(broken.isReadable()).thenReturn(true);
        when(broken.getFilename()).thenReturn("broken.ttf");
        when(broken.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        when(broken.getURL()).thenReturn(java.net.URI.create("file:///tmp/broken.ttf").toURL());
        when(broken.isFile()).thenReturn(true);
        when(broken.getFile()).thenReturn(new java.io.File("/tmp/definitely-missing-font-a8.ttf"));
        when(resolver.getResources("classpath*:/fonts/**/*.ttf")).thenReturn(new Resource[] {broken});
        when(resolver.getResources("classpath*:/fonts/**/*.otf")).thenReturn(new Resource[0]);
        when(resolver.getResources("classpath*:/fonts/**/*.TTF")).thenReturn(new Resource[0]);
        when(resolver.getResources("classpath*:/fonts/**/*.OTF")).thenReturn(new Resource[0]);

        assertThatThrownBy(() -> new ClasspathFontRegistrar(resolver).registerInto(new ITextRenderer()))
                .isInstanceOf(DocumentRenderException.class)
                .hasMessageContaining("Failed to register classpath font");
    }

    @Test
    void toLoadablePath_prefersClassPathResourcePath() {
        assertThat(ClasspathFontRegistrar.toLoadablePath(new ClassPathResource("fonts/LiberationSans-Regular.ttf")))
                .isEqualTo("fonts/LiberationSans-Regular.ttf");
    }
}
