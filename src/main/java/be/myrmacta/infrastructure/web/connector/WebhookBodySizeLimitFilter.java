package be.myrmacta.infrastructure.web.connector;

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
 * Rejects oversized webhook requests before Spring reads the body into memory. Two layers :
 * an early reject on an honestly declared {@code Content-Length} (avoids even starting the
 * read), and a size-limited {@link ServletInputStream} wrapper that bounds the actual number of
 * bytes read regardless of what the request claims (D-CALLBACK-DOS, found in adversarial security
 * review) -- a request using chunked transfer-encoding, or simply lying about its length, has no
 * {@code Content-Length} to check but would otherwise let Spring's {@code HttpMessageConverter}
 * buffer an unbounded body into a {@code byte[]} before any application-level check runs, on this
 * anonymous, internet-facing chain.
 */
public class WebhookBodySizeLimitFilter extends OncePerRequestFilter {

    private final long maxBodyBytes;

    public WebhookBodySizeLimitFilter(long maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
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
            int b = delegate.read();
            if (b != -1) {
                checkLimit(1);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n > 0) {
                checkLimit(n);
            }
            return n;
        }

        private void checkLimit(int justRead) {
            bytesRead += justRead;
            if (bytesRead > maxBodyBytes) {
                throw new WebhookPayloadTooLargeException(maxBodyBytes);
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
