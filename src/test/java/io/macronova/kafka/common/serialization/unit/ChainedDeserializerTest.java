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

import io.macronova.kafka.common.serialization.ReverseBytesSerDe;
import io.macronova.kafka.common.serialization.utils.TestUtils;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import io.macronova.kafka.common.serialization.ChainedDeserializer;

public class ChainedDeserializerTest {
	@Test
	public void testDeserializers() {
		// given
		final String data = "Hello, World!";
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "0.deserializer", ReverseBytesSerDe.class.getName() );
			put( "1.deserializer", StringDeserializer.class.getName() );
		} };
		final ChainedDeserializer deserializer = new ChainedDeserializer();
		deserializer.configure( configuration, false );

		// when
		final Object result = deserializer.deserialize( "topic1", TestUtils.reverse( data.getBytes() ) );

		// then
		Assert.assertEquals( data, result );

		deserializer.close();
	}

	@Test
	public void testChildDeserializerConfiguration() {
		// given
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "0.deserializer", StringDeserializer.class.getName() );
			put( "0.schema.registry.url", "http://localhost:8081" );
			put( "1.deserializer", TestDeserializer.class.getName() );
			put( "1.my.property1", "value1" );
			put( "1.your.property2", 2 );
			put( "1.your.flag", true );
		} };
		final ChainedDeserializer deserializer = new ChainedDeserializer();

		// when
		deserializer.configure( configuration, true );

		// then
		Assert.assertTrue( TestDeserializer.isKey );
		Assert.assertNotNull( TestDeserializer.configuration );
		Assert.assertEquals( 4, TestDeserializer.configuration.size() );
		Assert.assertEquals( TestDeserializer.class.getName(), TestDeserializer.configuration.get( "deserializer" ) );
		Assert.assertEquals( "value1", TestDeserializer.configuration.get( "my.property1" ) );
		Assert.assertEquals( 2, TestDeserializer.configuration.get( "your.property2" ) );
		Assert.assertEquals( true, TestDeserializer.configuration.get( "your.flag" ) );

		deserializer.close();
	}

	@Test( expected = ConfigException.class )
	public void testFailOnMissingConfiguration() {
		// given
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "schema.registry.url", "http://localhost:8081" );
		} };
		final ChainedDeserializer deserializer = new ChainedDeserializer();

		// when
		deserializer.configure( configuration, false );
	}

	@Test( expected = ConfigException.class )
	public void testFailOnIncorrectConfiguration() {
		// given
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "0.deserializer", String.class.getName() );
		} };
		final ChainedDeserializer deserializer = new ChainedDeserializer();

		// when
		deserializer.configure( configuration, false );
	}

	public static class TestDeserializer implements Deserializer<String> {
		private static Map<String, ?> configuration = null;
		private static boolean isKey = false;

		@Override
		public void configure(Map<String, ?> configs, boolean isKey) {
			TestDeserializer.configuration = configs;
			TestDeserializer.isKey = isKey;
		}

		@Override
		public String deserialize(String topic, byte[] data) {
			if ( data == null ) {
				return null;
			}
			return new String( data );
		}

		@Override
		public void close() {
		}
	}
}
