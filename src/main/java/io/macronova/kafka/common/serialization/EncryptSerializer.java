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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;

import io.macronova.kafka.common.serialization.config.EncryptSerializerConfig;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import io.macronova.kafka.common.serialization.utils.EncryptionHelper;
import io.macronova.kafka.common.serialization.utils.IOUtils;

/**
 * Encrypt data with secret key or certificate loaded from keystore. Whenever required, serializer generates
 * random initialization vector and prepends it to the output byte array. Length of initialization vector
 * is dynamically calculated based on chosen cipher suite (see {@link EncryptionHelper#ivLength(String, Cipher)}).
 * <p/>
 *
 * Example configuration (secret key):
 * <blockquote><pre>
 * transformation = AES/ECB/PKCS5Padding
 * secret = 770A8A65DA156D24EE2A093277530142
 * </pre></blockquote>
 *
 * Example configuration (public key encryption):
 * <blockquote><pre>
 * transformation = RSA/None/PKCS1Padding
 * key.store.path = /tmp/keystore.jks
 * key.store.password = changeit
 * key.store.alias = key1
 * </pre></blockquote>
 *
 * Output data representation:
 * <blockquote><pre>
 * +------------------------------------+
 * | initialization vector  | encrypted |
 * | (optional, 8-16 bytes) |   data    |
 * +------------------------------------+
 * </pre></blockquote>
 */
public class EncryptSerializer implements Serializer<byte[]> {
	private EncryptSerializerConfig config = null;
	private PublicKey publicKey = null;

	@Override
	public void configure(Map<String, ?> configs, boolean isKey) {
		config = new EncryptSerializerConfig( configs );
		if ( config.useCertificate() ) {
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
	}

	@Override
	public byte[] serialize(String topic, byte[] data) {
		if ( data == null ) {
			return null;
		}
		final InputStream inputStream = new ByteArrayInputStream( data );
		final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try {
			final Cipher cipher = config.useCertificate()
					? EncryptionHelper.initializeCipher(
							Cipher.ENCRYPT_MODE, config.getTransformation(), publicKey,
							null, outputStream
					)
					: EncryptionHelper.initializeCipher(
							Cipher.ENCRYPT_MODE, config.getTransformation(), config.getSecret(),
							null, outputStream
					);

			final CipherInputStream cipherInputStream = new CipherInputStream( inputStream, cipher );
			IOUtils.copy( cipherInputStream, outputStream );
			cipherInputStream.close();

			return outputStream.toByteArray();
		}
		catch ( Exception e ) {
			throw new SerializationException( String.format( "Failed to encrypt content: %s.", e.getMessage() ), e );
		}
		finally {
			IOUtils.closeQuietly( inputStream );
			IOUtils.closeQuietly( outputStream );
		}
	}

	@Override
	public void close() {
		config = null;
		publicKey = null;
	}
}
