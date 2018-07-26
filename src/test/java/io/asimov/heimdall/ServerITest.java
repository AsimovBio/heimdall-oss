package io.asimov.heimdall;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public final class ServerITest
{
    private org.apache.http.client.HttpClient client;
    private Server server;

    @Before
    public void setup()
    {
        server = new Server("test-audience", (keyId, algorithm) -> Optional.empty());
        server.start();
        client = HttpClients.createMinimal();
    }

    @After
    public void teardown()
    {
        server.stop();
    }

    @Test
    public void testHeimdallHealth() throws IOException
    {
        final var get = new HttpGet("http://localhost:8080/heimdall-health");
        final var response = client.execute(get);
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testLoadBalancerHealth() throws IOException
    {
        final var get = new HttpGet("http://localhost:8080/load-balancer-health");
        final var response = client.execute(get);
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testNoAuth() throws IOException
    {
        // all routes besides load-balancer-health should trigger auth, so we don't really care what the path is
        final var get = new HttpGet("http://localhost:8080/needs-auth");

        // no token
        var response = client.execute(get);
        assertEquals(403, response.getStatusLine().getStatusCode());
    }
}
