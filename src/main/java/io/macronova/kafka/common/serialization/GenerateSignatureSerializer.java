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
package io.macronova.kafka.common.serialization;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.Map;

import io.macronova.kafka.common.serialization.config.GenerateSignatureConfig;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import io.macronova.kafka.common.serialization.utils.EncryptionHelper;
import io.macronova.kafka.common.serialization.utils.IOUtils;

/**
 * Sign data with private key (typically RSA or DSA) loaded from keystore.
 * Serializer prepends signature to data byte array.
 * <p/>
 *
 * Example configuration:
 * <blockquote><pre>
 * algorithm = SHA256withRSA
 * key.store.path = /tmp/keystore.jks
 * key.store.password = changeit
 * key.store.alias = key1
 * key.store.alias.password = donotchange
 * </pre></blockquote>
 *
 * Output byte array format:
 * <blockquote><pre>
 * +------------------+
 * | signature | data |
 * +------------------+
 * </pre></blockquote>
 */
public class GenerateSignatureSerializer implements Serializer<byte[]> {
	private GenerateSignatureConfig config = null;
	private PrivateKey privateKey = null;

	@Override
	public void configure(Map<String, ?> configs, boolean isKey) {
		config = new GenerateSignatureConfig( configs );
		try {
			final KeyStore keyStore = EncryptionHelper.loadKeyStore(
					config.getKeyStorePath(), config.getKeyStoreType(), config.getKeyStorePassword().toCharArray()
			);
			final KeyStore.PasswordProtection keyPassword = new KeyStore.PasswordProtection(
					config.getKeyAliasPassword().toCharArray()
			);
			final KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry( config.getKeyAlias(), keyPassword );
			if ( privateKeyEntry == null ) {
				throw new ConfigException(
						String.format( "Could not find alias '%s' in key store '%s'.", config.getKeyAlias(), config.getKeyStorePath() )
				);
			}
			privateKey = privateKeyEntry.getPrivateKey();
		}
		catch ( Exception e ) {
			throw new ConfigException( String.format( "Failed to retrieve private key: %s.", e.getMessage() ), e );
		}
	}

	@Override
	public byte[] serialize(String topic, byte[] data) {
		if ( data == null ) {
			return null;
		}
		InputStream inputStream = null;
		try {
			inputStream = new ByteArrayInputStream( data );
			final Signature signature = EncryptionHelper.initializeSignature( config.getAlgorithm(), privateKey, true );
			byte[] buffer = new byte[ 8192 ];
			int bytesCount = 0;
			while ( (bytesCount = inputStream.read( buffer )) != -1 ) {
				signature.update( buffer, 0, bytesCount );
			}
			final byte[] signatureBytes = signature.sign();

			byte[] output = Arrays.copyOf( signatureBytes, data.length + signatureBytes.length );
			System.arraycopy( data, 0, output, signatureBytes.length, data.length );

			return output;
		}
		catch ( Exception e ) {
			throw new SerializationException( String.format( "Failed to sign content: %s.", e.getMessage() ), e );
		}
		finally {
			IOUtils.closeQuietly( inputStream );
		}
	}

	@Override
	public void close() {
		config = null;
		privateKey = null;
	}
}
