package com.waylo.serviceauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

    public enum Mode { DUAL, STRICT }

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

    /** Outcome. caller is the verified service name, or "legacy" for an accepted X-Internal-Token (dual mode only). */
    public record Decision(int status, String caller, String reason) {
        public boolean allowed() {
            return status == 0;
        }
    }

    static final Route INTERNAL_DEFAULT = new Route("/internal", Set.of(), true);

    private final String self;
    private final Mode mode;
    private final List<Route> routes;
    private final Function<String, PublicKey> keyOfIssuer;
    private final byte[] legacyToken;
    private final Clock clock;
    private final ReplayCache replays;

    public ServiceAuthenticator(String self, Mode mode, List<Route> routes, Function<String, PublicKey> keyOfIssuer,
                                String legacyToken, Clock clock) {
        this(self, mode, routes, keyOfIssuer, legacyToken, clock, ReplayCache.MAX_ENTRIES);
    }

    ServiceAuthenticator(String self, Mode mode, List<Route> routes, Function<String, PublicKey> keyOfIssuer,
                         String legacyToken, Clock clock, int replayCapacity) {
        this.replays = new ReplayCache(replayCapacity);
        this.self = self;
        this.mode = mode;
        this.routes = routes;
        this.keyOfIssuer = keyOfIssuer;
        this.legacyToken = legacyToken == null || legacyToken.length() < 32 ? new byte[0] : legacyToken.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    /** First matching configured route, else the fail-closed /internal default, else null (not protected). */
    public Route routeFor(String uri) {
        for (Route r : routes) {
            if (r.matches(uri)) return r;
        }
        return INTERNAL_DEFAULT.matches(uri) ? INTERNAL_DEFAULT : null;
    }

    public Decision decide(Route route, String serviceToken, String legacy) {
        String reason = "missing credential";
        if (serviceToken != null) {
            try {
                ServiceTokens.Claims c = ServiceTokens.verify(serviceToken, self, keyOfIssuer, clock.instant());
                if (!route.callers().contains(c.issuer())) {
                    reason = "caller " + c.issuer() + " not allowed on " + route.path();
                    if (mode == Mode.DUAL && legacyOk(legacy)) return new Decision(0, "legacy", reason);
                    return new Decision(403, c.issuer(), reason);
                }
                ReplayCache.Result r = replays.register(c.issuer(), c.jti(), c.expiresAt(), clock.instant().getEpochSecond());
                if (r != ReplayCache.Result.ACCEPTED) {
                    reason = r == ReplayCache.Result.FULL ? "replay cache full" : "replayed token";
                    if (mode == Mode.DUAL && legacyOk(legacy)) return new Decision(0, "legacy", reason);
                    return new Decision(401, c.issuer(), reason);
                }
                return new Decision(0, c.issuer(), null);
            } catch (ServiceTokens.InvalidServiceToken e) {
                reason = e.getMessage();
            }
        }
        if (mode == Mode.DUAL && legacyOk(legacy)) return new Decision(0, "legacy", serviceToken == null ? null : reason);
        if (legacy != null && mode == Mode.STRICT && serviceToken == null) reason = "legacy token not accepted in strict mode";
        return new Decision(401, null, reason);
    }

    private boolean legacyOk(String legacy) {
        return legacy != null && legacyToken.length > 0
                && MessageDigest.isEqual(legacyToken, legacy.getBytes(StandardCharsets.UTF_8));
    }
}
