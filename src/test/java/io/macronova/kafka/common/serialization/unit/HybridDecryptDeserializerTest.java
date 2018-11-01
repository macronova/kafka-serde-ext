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

import io.macronova.kafka.common.serialization.BaseTestCase;
import org.apache.kafka.common.config.ConfigException;
import io.macronova.kafka.common.serialization.HybridDecryptDeserializer;
import io.macronova.kafka.common.serialization.HybridEncryptSerializer;
import org.apache.kafka.common.errors.SerializationException;
import org.bouncycastle.util.Arrays;

public class HybridDecryptDeserializerTest extends BaseTestCase {
	@Test
	public void testDecryption() {
		checkSerializationDeserialization( "Hello, Kafka!".getBytes() );
	}

	@Test
	public void testLargeContent() {
		byte[] data = new byte[ 5 * 1024 * 1024 ]; // 5 MB of random bytes
		random.nextBytes( data );
		checkSerializationDeserialization( data );
	}

	private void checkSerializationDeserialization(byte[] data) {
		// given
		final HybridEncryptSerializer serializer = new HybridEncryptSerializer();
		serializer.configure( configuration(), false );
		final byte[] encrypted = serializer.serialize( "topic1", data );

		// when
		final HybridDecryptDeserializer deserializer = new HybridDecryptDeserializer();
		deserializer.configure( configuration(), false );
		final byte[] result = deserializer.deserialize( "topic1", encrypted );

		// then
		Assert.assertNotNull( result );
		Assert.assertEquals( data.length, result.length );

		serializer.close();
		deserializer.close();
	}

	@Test( expected = ConfigException.class )
	public void testFailOnMissedPadding() {
		// given
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "symmetric.transformation", "AES/CBC/PKCS5Padding" );
			put( "asymmetric.transformation", "RSA" );
			put( "asymmetric.key.store.path", keyStorePath() );
			put( "asymmetric.key.store.password", keyStorePassword() );
			put( "asymmetric.key.store.alias", keyAlias() );
			put( "asymmetric.key.store.alias.password", keyAliasPassword() );
		} };
		final HybridDecryptDeserializer deserializer = new HybridDecryptDeserializer();

		// when
		deserializer.configure( configuration, false );
	}

	@Test( expected = ConfigException.class )
	public void testFailOnMissingPassword() {
		// given
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "symmetric.transformation", "AES/CBC/PKCS5Padding" );
			put( "asymmetric.transformation", "RSA/None/PKCS1Padding" );
			put( "asymmetric.key.store.path", "/tmp/keystore.jks" );
			put( "asymmetric.key.store.password", "changeit" );
			put( "asymmetric.key.store.alias", "key" );
		} };
		final HybridDecryptDeserializer deserializer = new HybridDecryptDeserializer();

		// when
		deserializer.configure( configuration, false );
	}

	@Test( expected = SerializationException.class )
	public void testFailedDecryption() {
		// given
		final HybridEncryptSerializer serializer = new HybridEncryptSerializer();
		serializer.configure( configuration(), false );
		final byte[] encrypted = serializer.serialize( "topic1", "Hello, Kafka!".getBytes() );

		// when
		final HybridDecryptDeserializer deserializer = new HybridDecryptDeserializer();
		deserializer.configure( configuration(), false );
		deserializer.deserialize( "topic1", Arrays.append( encrypted, (byte) 0 ) );
	}

	private Map<String, Object> configuration() {
		return new HashMap<String, Object>() { {
			put( "symmetric.transformation", "AES/CBC/PKCS5Padding" );
			put( "asymmetric.transformation", "RSA/None/PKCS1Padding" );
			put( "asymmetric.key.store.path", keyStorePath() );
			put( "asymmetric.key.store.password", keyStorePassword() );
			put( "asymmetric.key.store.alias", keyAlias() );
			put( "asymmetric.key.store.alias.password", keyAliasPassword() );
		} };
	}
}
