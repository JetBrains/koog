package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.annotations.JavaAPI
import java.util.concurrent.CompletableFuture


/**
 * Represents an interceptor that can intercept and process a given context of type [ContextT].
 * This is a functional interface, allowing functional-style usage.
 *
 * @param ContextT the type of the context to be intercepted.
 */
@JavaAPI
public fun interface Interceptor<ContextT> {

    /**
     * Intercepts the given context for custom processing or handling logic.
     *
     * @param contextT the context to be intercepted.
     */
    public fun intercept(contextT: ContextT)
}

/**
 * A functional interface designed to intercept and transform data of type `DataT` during processing,
 * using a provided context of type `ContextT`.
 *
 * Typically used in scenarios where a processing pipeline requires customizable transformations of data.
 *
 * This interface is designed for interoperability with Java, indicated by the `@JavaAPI` annotation.
 *
 * @param ContextT The type of the context passed to the transform function.
 * @param DataT The type of the data being transformed.
 */
@JavaAPI
public fun interface TransformInterceptor<ContextT, DataT> {

    /**
     * Processes and transforms the provided data within the given context.
     *
     * @param contextT The context in which the operation is performed.
     * @param data The data to be transformed.
     * @return The transformed data.
     */
    public fun transform(contextT: ContextT, data: DataT): DataT
}

/**
 * Represents an asynchronous interceptor interface that processes a given context type.
 * This interface is designed to be implemented to provide custom interceptor logic
 * that can operate asynchronously using a `CompletableFuture`.
 *
 * @param ContextT The type of the context object that the interceptor processes.
 */
@JavaAPI
public fun interface AsyncInterceptor<ContextT> {
    /**0
     * Intercepts the given context and performs asynchronous processing.
     *
     * @param contextT the context object to be intercepted and processed
     * @return a CompletableFuture that resolves to a Boolean value indicating the result of the interception
     */
    public fun intercept(contextT: ContextT): CompletableFuture<Boolean>
}

