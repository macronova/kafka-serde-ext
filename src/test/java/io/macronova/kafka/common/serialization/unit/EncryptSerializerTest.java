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

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import io.macronova.kafka.common.serialization.BaseTestCase;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.SerializationException;
import io.macronova.kafka.common.serialization.EncryptSerializer;
import io.macronova.kafka.common.serialization.utils.TestUtils;

public class EncryptSerializerTest extends BaseTestCase {
	@Test
	public void testSecretWithIV() throws Exception {
		// given
		final byte[] data = "Hello, World!".getBytes();
		final String transformation = "AES/CBC/PKCS5Padding";
		final String secret = "770A8A65DA156D24EE2A093277530142";
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "transformation", transformation );
			put( "secret", secret );
		} };
		final EncryptSerializer serializer = new EncryptSerializer();
		serializer.configure( configuration, false );

		// when
		final byte[] result = serializer.serialize( "topic1", data );

		// then
		Assert.assertNotNull( result );
		Assert.assertNotEquals( data.length, result.length );
		byte[] iv = Arrays.copyOfRange( result, 0, 16 );
		byte[] encrypted = Arrays.copyOfRange( result, iv.length, result.length );
		Assert.assertNotEquals( data.length, encrypted.length );
		Assert.assertArrayEquals( data, TestUtils.decrypt( encrypted, iv, secret, transformation ) );

		serializer.close();
	}

	@Test
	public void testSecretWithoutIV() throws Exception {
		// given
		final byte[] data = "Hello, Macronova!".getBytes();
		final String transformation = "AES/ECB/PKCS5Padding";
		final String secret = "770A8A65DA156D24EE2A093277530142";
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "transformation", transformation );
			put( "secret", secret );
		} };
		final EncryptSerializer serializer = new EncryptSerializer();
		serializer.configure( configuration, false );

		// when
		final byte[] result = serializer.serialize( "topic1", data );

		// then
		Assert.assertNotNull( result );
		Assert.assertNotEquals( data.length, result.length );
		Assert.assertArrayEquals( data, TestUtils.decrypt( result, null, secret, transformation ) );

		serializer.close();
	}

	@Test
	public void testCertificate() throws Exception {
		// given
		final byte[] data = "Hello, Kafka!".getBytes();
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "transformation", "RSA/None/PKCS1Padding" );
			put( "key.store.path", keyStorePath() );
			put( "key.store.password", keyStorePassword() );
			put( "key.store.alias", keyAlias() );
		} };
		final EncryptSerializer serializer = new EncryptSerializer();
		serializer.configure( configuration, false );

		// when
		final byte[] result = serializer.serialize( "topic1", data );

		// then
		Assert.assertNotNull( result );
		Assert.assertNotEquals( data.length, result.length );
		// JVM RSA = BouncyCastle RSA/None/PKCS1Padding
		Assert.assertArrayEquals(
				data, TestUtils.decrypt( result, null, new File( keyStorePath() ), keyAlias(), keyStorePassword(), keyAliasPassword(), "RSA" )
		);

		serializer.close();
	}

	@Test( expected = SerializationException.class )
	public void testFailedEncryption() throws Exception {
		// given
		final byte[] data = "Hello, World!".getBytes();
		final String transformation = "AES/CBC/PKCS5Padding";
		final String secret = "invalid";
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "transformation", transformation );
			put( "secret", secret );
		} };
		final EncryptSerializer serializer = new EncryptSerializer();
		serializer.configure( configuration, false );

		// when
		serializer.serialize( "topic1", data );
	}

	@Test( expected = ConfigException.class )
	public void testFailOnMissingSecret() {
		// given
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "transformation", "AES/ECB/PKCS5PADDING" );
		} };
		final EncryptSerializer serializer = new EncryptSerializer();

		// when
		serializer.configure( configuration, false );
	}

	@Test( expected = ConfigException.class )
	public void testFailOnMissingPassword() {
		// given
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "transformation", "RSA" );
			put( "key.store.path", "/tmp/keystore.jks" );
			put( "key.store.alias", "key" );
		} };
		final EncryptSerializer serializer = new EncryptSerializer();

		// when
		serializer.configure( configuration, false );
	}

	@Test( expected = ConfigException.class )
	public void testFailOnWrongConfiguration() {
		// given
		// Specify both - secret passphrase and key store location.
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "transformation", "whatever:)" );
			put( "secret", "770A8A65DA156D24EE2A093277530142" );
			put( "key.store.path", "/tmp/keystore.jks" );
			put( "key.store.password", "changeit" );
			put( "key.store.alias", "key" );
		} };
		final EncryptSerializer serializer = new EncryptSerializer();

		// when
		serializer.configure( configuration, false );
	}
}
