/*
  Based on https://gist.github.com/swankjesse/8571a8207a5815cca1fb
*/

package org.nsh07.wikireader.network;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;

/**
 * An interceptor that allows runtime changes to the URL hostname.
 *
 * <p>A request may name its own host with the {@link #HOST_HEADER} header, which is consumed here
 * and never sent over the wire. Anything else goes to the host set by {@link #setHost}. Without
 * the per-request option, work that needs a different wiki than the one being read — saving an
 * article in another language, say — has to swap the shared host and put it back afterwards, and
 * any request in flight meanwhile is sent to the wrong Wikipedia.
 */
public final class HostSelectionInterceptor implements Interceptor {
    public static final String HOST_HEADER = "X-Wiki-Host";

    private volatile String host;

    public void setHost(String host) {
        this.host = host;
    }

    @NonNull
    @Override
    public okhttp3.Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        String requestHost = request.header(HOST_HEADER);
        if (requestHost != null) {
            request = request.newBuilder().removeHeader(HOST_HEADER).build();
        } else {
            requestHost = this.host;
        }

        if (requestHost != null) {
            HttpUrl newUrl = request.url().newBuilder()
                    .host(requestHost)
                    .build();
            request = request.newBuilder()
                    .url(newUrl)
                    .build();
        }
        return chain.proceed(request);
    }
}
