package io.asimov.heimdall.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.security.interfaces.ECPublicKey;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * It might be desirable to circuit-break or otherwise throttle the act of regenerating the
 * cache in cases where the key is not found. For now, a simple implementation of a key store.
 */
public final class CachedJWTKeyStore implements JWTKeyStore
{
    private static final Logger logger = LogManager.getLogger(CachedJWTKeyStore.class);
    private static final String PUBLIC_KEY_VERIFICATION_URL = "https://www.gstatic.com/iap/verify/public_key-jwk";
    // used to synchronize access to the keyCache
    private final Object lock = new Object();
    // Set this to an empty map - if initial initialization fails, we'll try again by not finding the key
    private volatile Map<String, JWK> keyCache = Map.copyOf(new HashMap<>());

    public CachedJWTKeyStore()
    {
        buildCache(cache -> true);
    }

    @Override
    public Optional<ECPublicKey> getKey(final String keyId, final String algorithm)
    {
        if (!keyCache.containsKey(keyId))
        {
            buildCache(cache -> !cache.containsKey(keyId));
        }
        final JWK jwk = keyCache.get(keyId);
        if (jwk == null || !jwk.getAlgorithm().getName().equals(algorithm))
        {
            return Optional.empty();
        }

        try
        {
            return Optional.of(ECKey.parse(jwk.toJSONString()).toECPublicKey());
        }
        catch (final ParseException | JOSEException e)
        {
            logger.error("Exception parsing key: ", e);
            return Optional.empty();
        }
    }

    /** This is synchronized because if multiple threads miss at once, there's no point in having them all
     * refresh the cache.
     * @param stillNeeded submits the current map for verification that we should proceed with the refresh.
     *                    Useful in case another thread fixed our problem while we were waiting on a lock.
     */

    private void buildCache(final Predicate<Map<String, JWK>> stillNeeded)
    {
        synchronized(lock)
        {
            if (!stillNeeded.test(keyCache))
            {
                return;
            }

            try
            {
                final JWKSet jwkSet = JWKSet.load(new URL(PUBLIC_KEY_VERIFICATION_URL));

                final Map<String, JWK> newCache = new HashMap<>();
                for (final JWK key : jwkSet.getKeys())
                {
                    newCache.put(key.getKeyID(), key);
                }
                keyCache = Map.copyOf(newCache);
            }
            catch (final IOException | ParseException e)
            {
                logger.error("Error downloading and parsing key set: ", e);
            }
        }
    }
}
