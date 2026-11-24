package com.waylo.serviceauth;

import java.security.PublicKey;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Receiver-side decision for one service: which callers may reach which route group, with which credential.
 * Pure logic (no servlet types) so it can be unit tested directly. An accepted (iss, jti) is remembered until
 * exp + skew, so a token can be used once (see {@link ReplayCache}).
 */
public class ServiceAuthenticator {

    /**
     * A route group: path prefix (segment boundary) and the services allowed to call it.
     * required = false means a missing or invalid credential is not rejected here (the service's own filter then
     * decides, e.g. gateway-authenticated users on /api/customers); a valid one just marks the request.
     */
    public record Route(String path, Set<String> callers, boolean required) {
        boolean matches(String uri) {
            return uri.equals(path) || uri.startsWith(path.endsWith("/") ? path : path + "/");
        }
    }

    /** Outcome. caller is the verified service name, (null when rejected). */
    public record Decision(int status, String caller, String reason) {
        public boolean allowed() {
            return status == 0;
        }
    }

    static final Route INTERNAL_DEFAULT = new Route("/internal", Set.of(), true);

    private final String self;
    private final List<Route> routes;
    private final Function<String, PublicKey> keyOfIssuer;
    private final Clock clock;
    private final ReplayCache replays;

    public ServiceAuthenticator(String self, List<Route> routes, Function<String, PublicKey> keyOfIssuer, Clock clock) {
        this(self, routes, keyOfIssuer, clock, ReplayCache.MAX_ENTRIES);
    }

    ServiceAuthenticator(String self, List<Route> routes, Function<String, PublicKey> keyOfIssuer,
                         Clock clock, int replayCapacity) {
        this.replays = new ReplayCache(replayCapacity);
        this.self = self;
        this.routes = routes;
        this.keyOfIssuer = keyOfIssuer;
        this.clock = clock;
    }

    /** First matching configured route, else the fail-closed /internal default, else null (not protected). */
    public Route routeFor(String uri) {
        for (Route r : routes) {
            if (r.matches(uri)) return r;
        }
        return INTERNAL_DEFAULT.matches(uri) ? INTERNAL_DEFAULT : null;
    }

    public Decision decide(Route route, String serviceToken) {
        String reason = "missing credential";
        if (serviceToken != null) {
            try {
                ServiceTokens.Claims c = ServiceTokens.verify(serviceToken, self, keyOfIssuer, clock.instant());
                if (!route.callers().contains(c.issuer())) {
                    reason = "caller " + c.issuer() + " not allowed on " + route.path();
                    return new Decision(403, c.issuer(), reason);
                }
                ReplayCache.Result r = replays.register(c.issuer(), c.jti(), c.expiresAt(), clock.instant().getEpochSecond());
                if (r != ReplayCache.Result.ACCEPTED) {
                    reason = r == ReplayCache.Result.FULL ? "replay cache full" : "replayed token";
                    return new Decision(401, c.issuer(), reason);
                }
                return new Decision(0, c.issuer(), null);
            } catch (ServiceTokens.InvalidServiceToken e) {
                reason = e.getMessage();
            }
        }
        return new Decision(401, null, reason);
    }
}
