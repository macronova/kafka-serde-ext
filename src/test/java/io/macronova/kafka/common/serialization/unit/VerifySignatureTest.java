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
import org.apache.kafka.common.errors.SerializationException;
import org.bouncycastle.util.Arrays;
import io.macronova.kafka.common.serialization.GenerateSignatureSerializer;
import io.macronova.kafka.common.serialization.VerifySignatureDeserializer;

public class VerifySignatureTest extends BaseTestCase {
	@Test
	public void testSuccessfulVerification() {
		checkSignVerify( "SHA256withRSA", "Hello, Kafka!".getBytes() );
	}

	@Test
	public void testLargeContent() {
		byte[] data = new byte[ 5 * 1024 * 1024 ]; // 5 MB of random bytes
		random.nextBytes( data );
		checkSignVerify( "SHA256withRSA", data );
	}

	@Test
	public void testDifferentAlgorithms() {
		final String[] algorithms = new String[] {
				"SHA256withRSA", "MD5withRSA", "RIPEMD256withRSA", "SHA3-512withRSA",
				"SHA512withRSAandMGF1", "SHA1withRSA", "MD2withRSA", "SHA512withRSA/X9.31"
		};
		for ( String algorithm : algorithms ) {
			checkSignVerify( algorithm, "Hello, Macronova!".getBytes() );
		}
	}

	@Test( expected = SerializationException.class )
	public void testFailedVerification() {
		// given
		final byte[] data = "Hello, Kafka!".getBytes();
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "algorithm", "SHA256withRSA" );
			put( "key.store.path", keyStorePath() );
			put( "key.store.password", keyStorePassword() );
			put( "key.store.alias", keyAlias() );
			put( "key.store.alias.password", keyAliasPassword() );
		} };
		final GenerateSignatureSerializer serializer = new GenerateSignatureSerializer();
		serializer.configure( configuration, false );
		final byte[] encrypted = serializer.serialize( "topic1", data );
		final VerifySignatureDeserializer deserializer = new VerifySignatureDeserializer();
		deserializer.configure( configuration, false );

		// when
		deserializer.deserialize( "topic1", Arrays.append( encrypted, (byte) 100 ) );
	}

	private void checkSignVerify(String algorithm, byte[] data) {
		// given
		final Map<String, Object> configuration = new HashMap<String, Object>() { {
			put( "algorithm", algorithm );
			put( "key.store.path", keyStorePath() );
			put( "key.store.password", keyStorePassword() );
			put( "key.store.alias", keyAlias() );
			put( "key.store.alias.password", keyAliasPassword() );
		} };
		final GenerateSignatureSerializer serializer = new GenerateSignatureSerializer();
		serializer.configure( configuration, false );
		final byte[] encrypted = serializer.serialize( "topic1", data );
		final VerifySignatureDeserializer deserializer = new VerifySignatureDeserializer();
		deserializer.configure( configuration, false );

		// when
		final byte[] decrypted = deserializer.deserialize( "topic1", encrypted );

		// then
		Assert.assertArrayEquals( data, decrypted );

		serializer.close();
		deserializer.close();
	}
}
