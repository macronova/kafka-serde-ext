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
import org.apache.kafka.common.errors.SerializationException;
import org.bouncycastle.util.Arrays;
import io.macronova.kafka.common.serialization.DecryptDeserializer;
import io.macronova.kafka.common.serialization.EncryptSerializer;

public class DecryptDeserializerTest extends BaseTestCase {
	@Test
	public void testSecretWithIV() {
		checkSerializationDeserialization( "AES/CBC/PKCS5Padding", "Hello, Kafka!".getBytes() );
	}

	@Test
	public void testLargeContent() {
		byte[] data = new byte[ 5 * 1024 * 1024 ]; // 5 MB of random bytes
		random.nextBytes( data );
		checkSerializationDeserialization( "AES/CBC/PKCS5Padding", data );
	}

	@Test
	public void testSecretWithoutIV() {
		checkSerializationDeserialization( "AES/ECB/PKCS5Padding", "Hello, Kafka!".getBytes() );
	}

	@Test
	public void testCertificate() {
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "transformation", "RSA" );
			put( "key.store.path", keyStorePath() );
			put( "key.store.password", keyStorePassword() );
			put( "key.store.alias", keyAlias() );
			put( "key.store.alias.password", keyAliasPassword() );
		} };
		checkSerializationDeserialization( configuration, "Hello, Kafka!".getBytes() );
	}

	@Test
	public void testDifferentTransformations() {
		final String[] transformations = new String[] {
				"AES/ECB/PKCS5Padding", "AES/CBC/PKCS5Padding", "AES/GCM/NoPadding",
				"AES/OFB/NoPadding", "AES/OCB/NoPadding", "AES/CTR/PKCS5Padding",
				"DESede/CBC/PKCS5Padding", "Blowfish/CBC/PKCS5Padding", "RC2/CBC/PKCS5Padding",
				"RC5/CBC/PKCS5Padding", "DESede/ECB/PKCS5Padding"
		};
		for ( String transformation : transformations ) {
			checkSerializationDeserialization( transformation, "Hello, Macronova!".getBytes() );
		}
	}

	private void checkSerializationDeserialization(String transformation, byte[] data) {
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "transformation", transformation );
			put( "secret", "770A8A65DA156D24EE2A093277530142" );
		} };
		checkSerializationDeserialization( configuration, data );
	}

	private void checkSerializationDeserialization(Map<String, Object> configuration, byte[] data) {
		// given
		final EncryptSerializer serializer = new EncryptSerializer();
		serializer.configure( configuration, false );
		final byte[] encrypted = serializer.serialize( "topic1", data );
		final DecryptDeserializer deserializer = new DecryptDeserializer();
		deserializer.configure( configuration, false );

		// when
		final byte[] decrypted = deserializer.deserialize( "topic1", encrypted );

		// then
		Assert.assertArrayEquals( data, decrypted );

		serializer.close();
		deserializer.close();
	}

	@Test( expected = SerializationException.class )
	public void testFailedDecryption() {
		// given
		final byte[] data = "Hello, Macronova!".getBytes();
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "transformation", "AES/CBC/PKCS5Padding" );
			put( "secret", "770A8A65DA156D24EE2A093277530142" );
		} };
		final EncryptSerializer serializer = new EncryptSerializer();
		serializer.configure( configuration, false );
		final byte[] encrypted = serializer.serialize( "topic1", data );
		final DecryptDeserializer deserializer = new DecryptDeserializer();
		deserializer.configure( configuration, false );

		// when
		deserializer.deserialize( "topic1", Arrays.append( encrypted, (byte) 0 ) );
	}

	@Test( expected = ConfigException.class )
	public void testFailOnMissingPassword() {
		// given
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "transformation", "RSA" );
			put( "key.store.path", "/tmp/keystore.jks" );
			put( "key.store.password", "changeit" );
			put( "key.store.alias", "key" );
		} };
		final DecryptDeserializer deserializer = new DecryptDeserializer();

		// when
		deserializer.configure( configuration, false );
	}
}
