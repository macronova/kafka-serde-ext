/*
 * Copyright 2018 Macronova.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.macronova.kafka.common.serialization.unit;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import io.macronova.kafka.common.serialization.ChainedSerializer;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import io.macronova.kafka.common.serialization.ReverseBytesSerDe;
import io.macronova.kafka.common.serialization.utils.TestUtils;

public class ChainedSerializerTest {
	@Test
	public void testSerializers() {
		// given
		final String data = "Hello, World!";
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "0.serializer", StringSerializer.class.getName() );
			put( "1.serializer", ReverseBytesSerDe.class.getName() );
		} };
		final ChainedSerializer serializer = new ChainedSerializer();
		serializer.configure( configuration, false );

		// when
		final byte[] result = serializer.serialize( "topic1", data );

		// then
		Assert.assertArrayEquals( TestUtils.reverse( data.getBytes() ), result );

		serializer.close();
	}

	@Test
	public void testChildSerializerConfiguration() {
		// given
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "0.serializer", StringSerializer.class.getName() );
			put( "0.schema.registry.url", "http://localhost:8081" );
			put( "1.serializer", TestSerializer.class.getName() );
			put( "1.my.property1", "value1" );
			put( "1.your.property2", "no-value2" );
		} };
		final ChainedSerializer serializer = new ChainedSerializer();

		// when
		serializer.configure( configuration, true );

		// then
		Assert.assertTrue( TestSerializer.isKey );
		Assert.assertNotNull( TestSerializer.configuration );
		Assert.assertEquals( 3, TestSerializer.configuration.size() );
		Assert.assertEquals( TestSerializer.class.getName(), TestSerializer.configuration.get( "serializer" ) );
		Assert.assertEquals( "value1", TestSerializer.configuration.get( "my.property1" ) );
		Assert.assertEquals( "no-value2", TestSerializer.configuration.get( "your.property2" ) );

		serializer.close();
	}

	@Test( expected = ConfigException.class )
	public void testFailOnMissingConfiguration() {
		// given
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "schema.registry.url", "http://localhost:8081" );
		} };
		final ChainedSerializer serializer = new ChainedSerializer();

		// when
		serializer.configure( configuration, false );
	}

	@Test( expected = ConfigException.class )
	public void testFailOnIncorrectConfiguration() {
		// given
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "0.serializer", String.class.getName() );
		} };
		final ChainedSerializer serializer = new ChainedSerializer();

		// when
		serializer.configure( configuration, false );
	}

	public static class TestSerializer implements Serializer<String> {
		private static Map<String, ?> configuration = null;
		private static boolean isKey = false;

		@Override
		public void configure(Map<String, ?> configs, boolean isKey) {
			TestSerializer.configuration = configs;
			TestSerializer.isKey = isKey;
		}

		@Override
		public byte[] serialize(String topic, String data) {
			if ( data == null ) {
				return null;
			}
			return data.getBytes();
		}

		@Override
		public void close() {
		}
	}
}
