package io.neonbee.entity;

import static io.neonbee.hook.HookType.CLUSTER_NODE_ID;
import static io.neonbee.internal.helper.AsyncHelper.joinComposite;
import static io.vertx.core.Future.succeededFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.neonbee.NeonBee;
import io.neonbee.hook.Hook;
import io.neonbee.hook.HookContext;
import io.neonbee.hook.HookType;
import io.neonbee.internal.cluster.ClusterHelper;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;

/**
 * Hooks for unregistering verticle models.
 */
public class UnregisterEntityVerticlesHook {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnregisterEntityVerticlesHook.class);

    /**
     * This method is called when a NeonBee instance shutdown gracefully.
     *
     * @param neonBee     the {@link NeonBee} instance
     * @param hookContext the {@link HookContext}
     * @param promise     {@link Promise} to completed the function.
     */
    @Hook(HookType.BEFORE_SHUTDOWN)
    public void unregisterOnShutdown(NeonBee neonBee, HookContext hookContext, Promise<Void> promise) {
        LOGGER.info("Unregistering models on shutdown");
        Vertx vertx = neonBee.getVertx();
        String nodeId = ClusterHelper.getNodeId(neonBee.getVertx());
        unregister(vertx, nodeId).onComplete(promise)
                .onSuccess(unused -> LOGGER.info("models unregistered successfully"))
                .onFailure(ignoredCause -> LOGGER.error("failed to unregister models on shutdown"));
    }

    /**
     * Unregister the entity qualified names for the node by ID.
     *
     * @param vertx  The Vert.x instance
     * @param nodeId hazelcast node ID
     * @return Future
     */
    public static Future<Void> unregister(Vertx vertx, String nodeId) {
        if (!vertx.isClustered()) {
            return succeededFuture();
        }

        LOGGER.info("unregistering entity verticle models for node ID {} ...", nodeId);
        Future<Void> unregisterFuture = EntityVerticle.getRegistry(vertx).getSharedMap().compose(map -> map.get(nodeId))
                .map(jsonArray -> jsonArray == null ? new JsonArray() : (JsonArray) jsonArray)
                .compose(entries -> joinComposite(EntityVerticle.removeSharedMapEntries(vertx, entries)))
                .compose(cf -> EntityVerticle.getRegistry(vertx).getSharedMap().compose(map -> map.remove(nodeId)))
                .mapEmpty();

        unregisterFuture
                .onSuccess(unused -> LOGGER.info("unregistered entity verticle models for node ID {} ...", nodeId))
                .onFailure(cause -> LOGGER.error("failed to unregistered entity verticle models for node ID {} ...",
                        nodeId, cause));

        return unregisterFuture;
    }

    /**
     * This method is called when a NeonBee node has left the cluster.
     *
     * @param neonBee     the {@link NeonBee} instance
     * @param hookContext the {@link HookContext}
     * @param promise     {@link Promise} to completed the function.
     */
    @Hook(HookType.NODE_LEFT)
    public void cleanup(NeonBee neonBee, HookContext hookContext, Promise<Void> promise) {
        String nodeId = hookContext.get(CLUSTER_NODE_ID);
        LOGGER.info("cleanup qualified names for node {}", nodeId);
        if (ClusterHelper.isLeader(neonBee.getVertx())) {
            LOGGER.info("cleaning registered qualified names ...");
            unregister(neonBee.getVertx(), nodeId).onComplete(promise)
                    .onSuccess(unused -> LOGGER.info("qualified names successfully cleaned up"))
                    .onFailure(ignoredCause -> LOGGER.error("failed to cleanup qualified names"));
        }
    }

}
