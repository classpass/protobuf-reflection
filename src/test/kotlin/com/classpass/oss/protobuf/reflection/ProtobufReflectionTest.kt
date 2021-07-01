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

import com.classpass.protobuf.reflect.test.Test.FavoriteCar
import com.classpass.protobuf.reflect.test.Test.FavoriteColor
import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

internal class ProtobufReflectionTest {
    @Test
    internal fun canBuildWithUnmodifiedBuilder() {
        val buildify = builderWrapper<FavoriteColor, String> { _, _ ->
            // no op
        }

        val favColor = buildify("input is ignored")

        assertEquals("", favColor.color)
        assertEquals(0, favColor.priority)
    }

    @Test
    internal fun canBuildWithJsonParser() {
        val favColor = jsonParser<FavoriteColor>()("""{"color": "blue", "priority": 17}""")

        assertEquals("blue", favColor.color)
        assertEquals(17, favColor.priority)
    }

    @Test
    internal fun canCreateBuildersFromRuntimeType() {
        val builders = mutableListOf<Class<out Message.Builder>>()

        // capture the builder used as a side effect
        listOf(FavoriteColor::class.java, FavoriteCar::class.java)
            .forEach { klass ->
                val instatiator = builderWrapper(klass) { builder, _: String ->
                    builders.add(builder.javaClass)
                    Unit
                }
                // create an ignored instance so we snag the builder
                instatiator("")
            }

        val expected: List<Class<out Message.Builder>> =
            listOf(FavoriteColor.Builder::class.java, FavoriteCar.Builder::class.java)
        assertEquals(expected, builders)
    }

    @Test
    internal fun parserCanParseDelimited() {
        val baos = ByteArrayOutputStream()
        val orig = FavoriteColor.newBuilder()
            .setColor("red")
            .setPriority(3)
            .build()
        orig.writeDelimitedTo(baos)

        val parser = parser<FavoriteColor>()
        val deserialized = parser.parseDelimitedFrom(baos.toByteArray().inputStream())

        assertEquals(orig, deserialized)
    }

    @Test
    internal fun parserCanParseStandalone() {
        val baos = ByteArrayOutputStream()
        val orig = FavoriteColor.newBuilder()
            .setColor("red")
            .setPriority(3)
            .build()
        orig.writeTo(baos)

        val deserialized = parser<FavoriteColor>().parseFrom(baos.toByteArray().inputStream())

        assertEquals(orig, deserialized)
    }

    private inline fun <reified T : Message> jsonParser(): (String) -> T {
        val jsonParser = JsonFormat.parser()

        return builderWrapper { builder, json ->
            jsonParser.merge(json, builder)
        }
    }
}
