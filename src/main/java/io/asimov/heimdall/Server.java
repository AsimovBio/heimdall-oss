package io.asimov.heimdall;

import io.asimov.heimdall.jwt.CachedJWTKeyStore;
import io.asimov.heimdall.jwt.JWTKeyStore;
import io.undertow.Undertow;
import io.undertow.server.RoutingHandler;

import java.time.Clock;
import java.util.Objects;

/**
 * Lean server which performs authentication of requests.
 */
public final class Server
{
    private final Undertow undertow;

    Server(final String audience, final JWTKeyStore keyStore)
    {
        undertow = Undertow.builder()
                .addHttpListener(8080, "0.0.0.0")
                .setHandler(new RoutingHandler()
                        .get("/load-balancer-health", exchange -> exchange.setStatusCode(200))
                        .get("/heimdall-health", exchange -> exchange.setStatusCode(200))
                        .setFallbackHandler(new IAPAuthHandler(keyStore, audience, Clock.systemUTC())))
                .build();
    }

    void start()
    {
        undertow.start();
    }

    void stop()
    {
        undertow.stop();
    }

    public static void main(final String[] args)
    {
        final String audience = Objects.requireNonNull(System.getenv("com.google.iap.audience"),
                "Set property 'com.google.iap.audience'");
        final Server server = new Server(audience, new CachedJWTKeyStore());
        server.start();
    }
}
