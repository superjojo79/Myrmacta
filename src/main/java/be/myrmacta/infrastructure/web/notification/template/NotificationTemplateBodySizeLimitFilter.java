package be.myrmacta.infrastructure.web.notification.template;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Borne le corps des POST et PUT {@code /api/notification-templates} avant la désérialisation Jackson (issue #43).
 * Un {@code Content-Length} trop grand est rejeté tout de suite. Un transfert morcelé, ou un en-tête qui ment, n'a pas
 * de longueur fiable : le flux lu est quand même plafonné.
 */
public class NotificationTemplateBodySizeLimitFilter extends OncePerRequestFilter {

    private static final String PREFIX = "/api/notification-templates";

    private final long maxBodyBytes;

    public NotificationTemplateBodySizeLimitFilter(long maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"POST".equalsIgnoreCase(method) && !"PUT".equalsIgnoreCase(method)) {
            return true;
        }
        String path = applicationPath(request);
        return !(path.equals(PREFIX) || path.startsWith(PREFIX + "/"));
    }

    /**
     * Chemin vu par le contrôleur. {@code getRequestURI()} garde le contexte ({@code /tenant_dev}) et les paramètres
     * de matrice ({@code ;x=1}) que Tomcat retire avant le routage : s'y fier laisse passer le corps (issue #43).
     */
    static String applicationPath(HttpServletRequest request) {
        String servletPath = request.getServletPath() == null ? "" : request.getServletPath();
        String pathInfo = request.getPathInfo();
        String path = pathInfo == null || pathInfo.isEmpty() ? servletPath : servletPath + pathInfo;
        if (path.isEmpty()) {
            String uri = request.getRequestURI() == null ? "" : request.getRequestURI();
            String context = request.getContextPath();
            if (context != null && !context.isEmpty() && uri.startsWith(context)) {
                uri = uri.substring(context.length());
            }
            path = uri;
        }
        int matrix = path.indexOf(';');
        return matrix < 0 ? path : path.substring(0, matrix);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long declared = request.getContentLengthLong();
        if (declared > maxBodyBytes) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        filterChain.doFilter(new SizeLimitedRequest(request, maxBodyBytes), response);
    }

    private static final class SizeLimitedRequest extends HttpServletRequestWrapper {

        private final long maxBodyBytes;

        SizeLimitedRequest(HttpServletRequest request, long maxBodyBytes) {
            super(request);
            this.maxBodyBytes = maxBodyBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new SizeLimitedServletInputStream(super.getInputStream(), maxBodyBytes);
        }
    }

    private static final class SizeLimitedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBodyBytes;
        private long bytesRead;

        SizeLimitedServletInputStream(ServletInputStream delegate, long maxBodyBytes) {
            this.delegate = delegate;
            this.maxBodyBytes = maxBodyBytes;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                checkLimit(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                checkLimit(count);
            }
            return count;
        }

        private void checkLimit(int justRead) {
            bytesRead += justRead;
            if (bytesRead > maxBodyBytes) {
                throw new NotificationTemplatePayloadTooLargeException(maxBodyBytes);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
