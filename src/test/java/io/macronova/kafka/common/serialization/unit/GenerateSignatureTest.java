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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import io.macronova.kafka.common.serialization.BaseTestCase;
import io.macronova.kafka.common.serialization.GenerateSignatureSerializer;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.SerializationException;

public class GenerateSignatureTest extends BaseTestCase {
	@Test
	public void testSuccessfulSign() {
		// given
		final byte[] data = "Hello, World!".getBytes();
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "algorithm", "SHA256withRSA" );
			put( "key.store.path", keyStorePath() );
			put( "key.store.password", keyStorePassword() );
			put( "key.store.alias", keyAlias() );
			put( "key.store.alias.password", keyAliasPassword() );
		} };
		final GenerateSignatureSerializer serializer = new GenerateSignatureSerializer();
		serializer.configure( configuration, false );

		// when
		final byte[] result = serializer.serialize( "topic1", data );

		// then
		Assert.assertNotNull( result );
		Assert.assertNotEquals( data.length, result.length );
		byte[] resultData = Arrays.copyOfRange( result, 256, result.length );
		Assert.assertArrayEquals( data, resultData );

		serializer.close();
	}

	@Test( expected = SerializationException.class )
	public void testFailedSignature() {
		// given
		final byte[] data = "Hello, World!".getBytes();
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "algorithm", "unknown" );
			put( "key.store.path", keyStorePath() );
			put( "key.store.password", keyStorePassword() );
			put( "key.store.alias", keyAlias() );
			put( "key.store.alias.password", keyAliasPassword() );
		} };
		final GenerateSignatureSerializer serializer = new GenerateSignatureSerializer();
		serializer.configure( configuration, false );

		// when
		serializer.serialize( "topic1", data );
	}

	@Test( expected = ConfigException.class )
	public void testFailOnMissingPassword() {
		// given
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "algorithm", "SHA256withRSA" );
			put( "key.store.path", keyStorePath() );
			put( "key.store.password", keyStorePassword() );
			put( "key.store.alias", keyAlias() );
		} };
		final GenerateSignatureSerializer serializer = new GenerateSignatureSerializer();

		// when
		serializer.configure( configuration, false );
	}
}
