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
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Map;

import io.macronova.kafka.common.serialization.config.VerifySignatureConfig;
import io.macronova.kafka.common.serialization.utils.EncryptionHelper;
import io.macronova.kafka.common.serialization.utils.IOUtils;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * Verifies correctness of signature, and throws {@link SerializationException} if it turns out invalid.
 * Deserializer assumes that input byte array contains first signature, followed by data itself.
 * <p/>
 *
 * Example configuration:
 * <blockquote><pre>
 * algorithm = SHA256withRSA
 * key.store.path = /tmp/keystore.jks
 * key.store.password = changeit
 * key.store.alias = key1
 * </pre></blockquote>
 *
 * Input byte array format:
 * <blockquote><pre>
 * +------------------+
 * | signature | data |
 * +------------------+
 * </pre></blockquote>
 */
public class VerifySignatureDeserializer implements Deserializer<byte[]> {
	private VerifySignatureConfig config = null;
	private PublicKey publicKey = null;

	@Override
	public void configure(Map<String, ?> configs, boolean isKey) {
		config = new VerifySignatureConfig( configs );
		try {
			final KeyStore keyStore = EncryptionHelper.loadKeyStore(
					config.getKeyStorePath(), config.getKeyStoreType(), config.getKeyStorePassword().toCharArray()
			);
			final Certificate certificate = keyStore.getCertificate( config.getKeyAlias() );
			if ( certificate == null ) {
				throw new ConfigException(
						String.format( "Could not find alias '%s' in key store '%s'.", config.getKeyAlias(), config.getKeyStorePath() )
				);
			}
			publicKey = certificate.getPublicKey();
		}
		catch ( Exception e ) {
			throw new ConfigException( String.format( "Failed to retrieve public key: %s.", e.getMessage() ), e );
		}
	}

	@Override
	public byte[] deserialize(String topic, byte[] data) {
		if ( data == null ) {
			return null;
		}
		final InputStream inputStream = new ByteArrayInputStream( data );
		try {
			final Signature signature = EncryptionHelper.initializeSignature( config.getAlgorithm(), publicKey, false );
			byte[] proposedSignature = new byte[ EncryptionHelper.getKeyLength( publicKey ) / 8 ];
			if ( inputStream.read( proposedSignature ) != proposedSignature.length ) {
				throw new SerializationException( "Unexpected end of encrypted content." );
			}

			byte[] buffer = new byte[ 8192 ];
			int bytesCount = 0;
			while ( (bytesCount = inputStream.read( buffer )) != -1 ) {
				signature.update( buffer, 0, bytesCount );
			}

			boolean valid = signature.verify( proposedSignature );
			if ( ! valid ) {
				throw new SerializationException( "Incorrect signature." );
			}

			return Arrays.copyOfRange( data, proposedSignature.length, data.length );
		}
		catch ( Exception e ) {
			throw new SerializationException( String.format( "Failed to verify signature: %s.", e.getMessage() ), e );
		}
		finally {
			IOUtils.closeQuietly( inputStream );
		}
	}

	@Override
	public void close() {
		config = null;
		publicKey = null;
	}
}
