/**
 * Copyright 2021 ClassPass
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.classpass.oss.protobuf.reflection

import com.google.protobuf.Message
import com.google.protobuf.Parser
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

/**
 * Wrapper around [builderWrapper] to allow specifying the type via a reified generic.
 */
public inline fun <reified T : Message, I> builderWrapper(noinline block: (Message.Builder, I) -> Unit): (I) -> T {
    return builderWrapper(T::class.java, block)
}

/**
 * Returns a function that, when called with some input, invokes the provided block on a fresh builder for T.
 * After the block completes, the presumably modified builder then has `build()` called on it.
 *
 * This function is relatively expensive, but the resulting function is cheap to invoke,
 * so the result should be cached and re-used.
 *
 * The resulting function is safe for concurrent invocation if the provided block is.
 */
public fun <T : Message, I> builderWrapper(
    messageType: Class<T>,
    block: (Message.Builder, I) -> Unit
): (I) -> T {
    // only accessing public members
    val lookup = MethodHandles.publicLookup()

    // Every generated Message type T has a T.Builder.
    // The Builder type needs to be loaded from the same classloader as the message type -- this shows up when resuming
    // flink jobs from a savepoint. Locating the class via a reflective method avoids classloader issues.
    val builderMethod = messageType.getMethod("newBuilder")
    val builderClass = builderMethod.returnType

    // we better have gotten a Message.Builder
    require(Message.Builder::class.java.isAssignableFrom(builderClass)) { "$builderClass is not a Message.Builder" }

    // Find T.newBuilder(), which returns T.Builder
    // Unreflecting a Method avoids classloader issues where the message type's loader != builder type's loader
    val newBuilderHandle = lookup.unreflect(builderMethod)

    // find T.Builder.build() which returns T
    val buildHandle = lookup.unreflect(builderClass.getMethod("build"))

    val newBuilder: () -> Message.Builder = {
        MethodHandleHelper.invokeNoArgs(builderClass, newBuilderHandle) as Message.Builder
    }
    val buildInstanceFromBuilder: (Message.Builder) -> T = { builder: Message.Builder ->
        MethodHandleHelper.invokeSingleArg(messageType, buildHandle, builderClass.cast(builder))
    }

    // build a test obj to make sure it's working
    run {
        val builder: Message.Builder = newBuilder()
        require(builderClass.isInstance(builder)) { "$builder is not an instance of $builderClass" }
        val obj = buildInstanceFromBuilder(builder)
        require(messageType.isInstance(obj)) { "$obj is not an instance of $messageType" }
    }

    // all good, return our builder-factory function
    return { input ->
        val builder: Message.Builder = newBuilder()

        block(builder, input)

        buildInstanceFromBuilder(builder)
    }
}

/**
 * Wrapper around [parser] to allow specifying the type via a reified generic.
 */
public inline fun <reified T : Message> parser(): Parser<T> = parser(T::class.java)

/**
 * Invoke the `parser()` static method on a generated Protobuf [Message] type.
 *
 * The result should be cached.
 */
public fun <T : Message> parser(messageType: Class<T>): Parser<T> {
    val lookup = MethodHandles.publicLookup()

    val parseHandle = lookup.findStatic(messageType, "parser", MethodType.methodType(Parser::class.java))

    // T.parser() returns a Parser<T>
    @Suppress("UNCHECKED_CAST")
    return MethodHandleHelper.invokeNoArgs(Parser::class.java, parseHandle) as Parser<T>
}
