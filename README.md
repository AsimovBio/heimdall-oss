# Heimdall

Heimdall is a simple gateway for authenticating requests that _should_ come from our IAP-enabled load balancer.

It rejects any request which does not have the expected IAP headers, and performs the work of verifying
that those headers are signed by Google.

## Dependencies

* Java 10
* Maven

## Building

    `mvn package`
    `docker build -t heimdall:1.0 .`
    `docker push`

## Runtime Pre-requisites

* Google Cloud Account
* Kubernetes Cluster
* Ambassador installed on cluster
* IAP-secured Ingress
* Load balancer health check set to '/load-balancer-health' (and some default backend supplying 200 OK at this route)

## Deployment

* Update `k8s/deployment.yaml` with your audience and the location of your built image.
* `kubectl apply -f k8s`

Ambassador should start routing requests here before forwarding them to your backend
services. You can test this by port-forwarding ambassador, and then making a request
without a token. For example:

    kubectl port-forward <ambassador-pod> 8080:80&

    curl -i localhost:8080/load-balancer-health
    => 200 OK
    curl -i localhost:8080/heimdall-health
    => 200 OK (assuming a backend serves this)
    curl -i localhost:8080/service-route
    => HTTP 403 forbidden, as there's no token 

## Health Checks

Health checks for this service appear somewhat complicated. I'll try to clarify here:

1. The health check for the service itself is at "/hemidall-health". It's unlikely any inbound requests are expected to be routed to a service with that resource.
2. The "/load-balancer-health" HTTP resource on port 8080 exists for the purposes of Google Cloud's health checks on the load balancer that fronts our ingress. Some notes:
    1. We add this handler first because we don't expect to auth the request with a token
    2. This route is explicitly handled by a backend so that it returns 200 OK.
    3. That route has to be configured in the cloud health checks console. It defaults to "/", which we cannot use for health checks because Heimdall would not be able to differentiate that from a request to "/" for any other host. Nobody should try to utilize that route in their services.

## TODO

* This will probably eventually want an autoscaling deployment.
* We could publicly host an image.
