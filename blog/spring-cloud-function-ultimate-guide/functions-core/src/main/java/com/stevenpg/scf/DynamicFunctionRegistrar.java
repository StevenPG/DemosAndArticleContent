package com.stevenpg.scf;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.reflect.Type;
import java.util.function.Function;

/**
 * DYNAMIC function registration — adding a function to the catalog at runtime,
 * not with a {@code @Bean}.
 *
 * <p>The {@code FunctionCatalog} is also a {@link FunctionRegistry}: you can push
 * new {@link FunctionRegistration}s into it while the application is running.
 * This is the seed of the "function deployer" idea — accept a function's
 * definition (or even its jar) over an API and make it invocable immediately,
 * without a redeploy. Here we register a trivial {@code dynamicUppercase}
 * function once all singletons are ready, and it becomes callable through every
 * surface exactly like the compile-time beans.
 */
@Component
public class DynamicFunctionRegistrar implements SmartInitializingSingleton {

    private static final Logger LOG = System.getLogger(DynamicFunctionRegistrar.class.getName());

    private final FunctionRegistry functionRegistry;

    public DynamicFunctionRegistrar(FunctionRegistry functionRegistry) {
        this.functionRegistry = functionRegistry;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Function<String, String> fn = input -> input == null ? null : input.toUpperCase();

        // SCF needs the generic signature to know how to convert inputs/outputs.
        Type functionType = ResolvableType
                .forClassWithGenerics(Function.class, String.class, String.class)
                .getType();

        FunctionRegistration<Function<String, String>> registration =
                new FunctionRegistration<>(fn, "dynamicUppercase").type(functionType);

        functionRegistry.register(registration);
        LOG.log(Level.INFO, "Registered 'dynamicUppercase' at runtime; catalog now has: {0}",
                functionRegistry.getNames(null));
    }
}
