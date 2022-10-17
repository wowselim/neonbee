package io.neonbee;

import static io.neonbee.test.helper.ReflectionHelper.setValueOfPrivateStaticField;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import io.neonbee.entity.EntityVerticle;

/**
 * This extension resets the static filed called "registry", that is used in the {@link EntityVerticle} to null before
 * each test execution.
 */
public class EntityVerticleExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        resetRegistry();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        resetRegistry();
    }

    /**
     * Reset the registry filed, that is used in the {@link EntityVerticle}.
     *
     * @throws NoSuchFieldException   If no such field exists in the fieldHolder
     * @throws IllegalAccessException If JVM doesn't grant access to the field
     */
    public static void resetRegistry() throws NoSuchFieldException, IllegalAccessException {
        setValueOfPrivateStaticField(EntityVerticle.class, "registry", null);
    }
}
