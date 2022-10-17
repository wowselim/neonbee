package io.neonbee.internal;

import static io.vertx.core.Future.succeededFuture;

import java.util.function.Supplier;

import io.neonbee.NeonBee;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.shareddata.AsyncMap;

/**
 * A registry to manage values in the {@link SharedDataAccessor} shared map.
 * <p>
 * The values under the key are stored in a JsonArray.
 */
public class WriteSafeRegistry {

    private final LoggingFacade logger = LoggingFacade.create();

    private final Vertx vertx;

    private final SharedDataAccessor sharedDataAccessor;

    private final String registryName;

    /**
     * Create a new {@link WriteSafeRegistry}.
     *
     * @param vertx        the {@link Vertx} instance
     * @param registryName the name of the map registry
     */
    public WriteSafeRegistry(Vertx vertx, String registryName) {
        this.vertx = vertx;
        this.registryName = registryName;
        this.sharedDataAccessor = new SharedDataAccessor(vertx, WriteSafeRegistry.class);
    }

    /**
     * Register the value in the {@link NeonBee} async shared map under the sharedMapKey.
     *
     * @param sharedMapKey the shared map key
     * @param value        the value to register.
     * @return the future
     */
    public Future<Void> register(String sharedMapKey, Object value) {
        return register(sharedMapKey, value, Promise::complete);
    }

    /**
     * Register the value in the {@link NeonBee} async shared map under the sharedMapKey.
     *
     * @param sharedMapKey      the shared map key
     * @param value             the value to register
     * @param valueAddedHandler a handler to handle additional things when the value is registered in the map. The
     *                          handler isn't executed if the value is already registered in the shared map.
     * @return the future
     */
    public Future<Void> register(String sharedMapKey, Object value, Handler<Promise<Void>> valueAddedHandler) {
        logger.info("register value: \"{}\" in shared map: \"{}\"", sharedMapKey, value);

        return lock(sharedMapKey, () -> addValue(sharedMapKey, value, valueAddedHandler));
    }

    /**
     * Method that acquires a lock for the sharedMapKey and released the lock after the futureSupplier is executed.
     *
     * @param sharedMapKey   the shared map key
     * @param futureSupplier supplier for the future to be secured by the lock
     * @return the futureSupplier
     */
    private Future<Void> lock(String sharedMapKey, Supplier<Future<Void>> futureSupplier) {
        logger.debug("Get lock for {}", sharedMapKey);
        return sharedDataAccessor.getLock(sharedMapKey).onFailure(throwable -> {
            logger.error("Error acquiring lock for {}", sharedMapKey, throwable);
        }).compose(lock -> futureSupplier.get().onComplete(anyResult -> {
            logger.debug("Releasing lock for {}", sharedMapKey);
            lock.release();
        }));
    }

    private Future<Void> addValue(String sharedMapKey, Object value, Handler<Promise<Void>> valueAddedHandler) {
        Future<AsyncMap<String, Object>> sharedMap = getSharedMap();

        return sharedMap.compose(map -> map.get(sharedMapKey))
                .map(valueOrNull -> valueOrNull != null ? (JsonArray) valueOrNull : new JsonArray())
                .compose(valueArray -> {
                    Future<Void> future = succeededFuture();
                    if (!valueArray.contains(value)) {
                        valueArray.add(value);
                        future = Future.future(valueAddedHandler);
                    }

                    if (logger.isInfoEnabled()) {
                        logger.info("Registered verticle {} of node {} in shared map.", value,
                                NeonBee.get(vertx).getNodeId());
                    }

                    return CompositeFuture.all(sharedMap.compose(map -> map.put(sharedMapKey, valueArray)), future)
                            .mapEmpty();
                });
    }

    /**
     * Unregister the value in the {@link NeonBee} async shared map from the sharedMapKey.
     *
     * @param sharedMapKey the shared map key
     * @param value        the value to register
     * @return the future
     */
    public Future<Void> unregister(String sharedMapKey, String value) {
        logger.info("unregister value: \"{}\" from shared map: \"{}\"", sharedMapKey, value);

        return lock(sharedMapKey, () -> removeValue(sharedMapKey, value));
    }

    private Future<Void> removeValue(String sharedMapKey, Object value) {
        Future<AsyncMap<String, Object>> sharedMap = getSharedMap();

        return sharedMap.compose(map -> map.get(sharedMapKey)).map(jsonArray -> (JsonArray) jsonArray)
                .compose(values -> {
                    if (values == null) {
                        return succeededFuture();
                    }

                    if (logger.isInfoEnabled()) {
                        logger.info("Unregistered verticle {} of node {} in shared map.", value,
                                NeonBee.get(vertx).getNodeId());
                    }

                    values.remove(value);
                    return sharedMap.compose(map -> map.put(sharedMapKey, values));
                });
    }

    /**
     * Shared map that is used as registry.
     *
     * @return Future to the shared map
     */
    public Future<AsyncMap<String, Object>> getSharedMap() {
        return sharedDataAccessor.getAsyncMap(registryName);
    }
}
