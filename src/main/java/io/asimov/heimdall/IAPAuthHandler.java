package io.asimov.heimdall;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.asimov.heimdall.jwt.JWTKeyStore;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.interfaces.ECPublicKey;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * A handler which verifies that a request came from Google Identity-Aware Proxy. If
 * the request is successfully verified, a 200 OK is returned, as well as a header containing the
 * identity of the requesting user.
 */
public final class IAPAuthHandler implements HttpHandler
{
    private static final Logger logger = LogManager.getLogger(IAPAuthHandler.class);

    private static final String IAP_ISSUER_URL = "https://cloud.google.com/iap";
    private final String expectedAudience;
    private final JWTKeyStore keyStore;
    private final Clock clock;

    IAPAuthHandler(final JWTKeyStore keyStore, final String expectedAudience, final Clock clock)
    {
        this.keyStore = keyStore;
        this.expectedAudience = expectedAudience;
        this.clock = clock;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange)
    {
        final String token = exchange.getRequestHeaders().getFirst("x-goog-iap-jwt-assertion");
        if (token == null)
        {
            failAuth(exchange, "null token");
        }
        else
        {
            verifyJwt(exchange, token);
        }
    }

    private void verifyJwt(final HttpServerExchange exchange, final String token)
    {
        try
        {
            final SignedJWT signedJWT = SignedJWT.parse(token);
            final JWSHeader header = signedJWT.getHeader();

            if (header.getAlgorithm() == null)
            {
                failAuth(exchange, "null algorithm");
                return;
            }
            if (header.getKeyID() == null)
            {
                failAuth(exchange, "null key id");
                return;
            }

            final JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            if (!claims.getAudience().contains(expectedAudience))
            {
                failAuth(exchange, "does not claim correct audience");
                return;
            }
            if (!claims.getIssuer().equals(IAP_ISSUER_URL))
            {
                failAuth(exchange, "does not match issuer URL");
                return;
            }

            final Date now = Date.from(Instant.now(clock));
            if (!claims.getIssueTime().before(now))
            {
                failAuth(exchange, "claim issued in future");
                return;
            }
            if (!claims.getExpirationTime().after(now))
            {
                failAuth(exchange, "claim expires in past");
                return;
            }

            if (claims.getSubject() == null)
            {
                failAuth(exchange, "null claims subject");
                return;
            }
            if (claims.getClaim("email") == null)
            {
                failAuth(exchange, "null claims email");
            }

            final Optional<ECPublicKey> publicKey = keyStore.getKey(header.getKeyID(), header.getAlgorithm().getName());
            if (!publicKey.isPresent())
            {
                failAuth(exchange, "no key matching key id and algorithm");
                return;
            }
            final JWSVerifier verifier = new ECDSAVerifier(publicKey.get());
            if (signedJWT.verify(verifier))
            {
                exchange.getResponseHeaders().put(new HttpString("x-iap-user-email"), claims.getClaim("email").toString());
            }
            else
            {
                failAuth(exchange, "could not verify token");
            }
        }
        catch (final ParseException | JOSEException e)
        {
            logger.error("Exception verifying token: ", e);
            failAuth(exchange, "encountered exception");
        }
    }

    private void failAuth(final HttpServerExchange exchange, final String reason)
    {
        logger.debug("Rejecting request. Reason: {}", reason);
        exchange.setStatusCode(403);
    }
}
