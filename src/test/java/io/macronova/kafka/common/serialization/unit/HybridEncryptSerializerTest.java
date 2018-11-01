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
import io.macronova.kafka.common.serialization.HybridEncryptSerializer;
import org.apache.kafka.common.config.ConfigException;

public class HybridEncryptSerializerTest extends BaseTestCase {
	@Test
	public void testEncryption() {
		// given
		final byte[] data = "Hello, Kafka!".getBytes();
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "symmetric.transformation", "AES/CBC/PKCS5Padding" );
			put( "asymmetric.transformation", "RSA/None/PKCS1Padding" );
			put( "asymmetric.key.store.path", keyStorePath() );
			put( "asymmetric.key.store.alias", keyAlias() );
			put( "asymmetric.key.store.password", keyStorePassword() );
		} };
		final HybridEncryptSerializer serializer = new HybridEncryptSerializer();
		serializer.configure( configuration, false );

		// when
		final byte[] result = serializer.serialize( "topic1", data );

		// then
		Assert.assertNotNull( result );
		Assert.assertNotEquals( data.length, result.length );

		serializer.close();
	}

	@Test( expected = ConfigException.class )
	public void testFailOnMissingPassword() {
		// given
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "symmetric.transformation", "AES/CBC/PKCS5Padding" );
			put( "asymmetric.transformation", "RSA/None/PKCS1Padding" );
			put( "asymmetric.key.store.path", "/tmp/keystore.jks" );
			put( "asymmetric.key.store.alias", "key" );
		} };
		final HybridEncryptSerializer serializer = new HybridEncryptSerializer();

		// when
		serializer.configure( configuration, false );
	}
}
