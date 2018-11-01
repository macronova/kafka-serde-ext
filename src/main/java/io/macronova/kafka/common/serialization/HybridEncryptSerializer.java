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
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import io.macronova.kafka.common.serialization.config.HybridEncryptSerializerConfig;
import io.macronova.kafka.common.serialization.utils.EncryptionHelper;
import io.macronova.kafka.common.serialization.utils.IOUtils;

/**
 * Hybrid encryption encodes payload with randomly generated key and symmetric algorithm, for example AES.
 * Random secret is typically short and can be encrypted with asymmetric algorithm, e.g. RSA. Review below diagram
 * for output data representation. To encrypt and decrypt content, users need to possess only key pair. Length of
 * initialization vector is dynamically calculated based on chosen symmetric transformation
 * (see {@link EncryptionHelper#ivLength(String, Cipher)}).
 * <p/>
 *
 * Example configuration:
 * <blockquote><pre>
 * symmetric.transformation = AES/CBC/PKCS5Padding
 * asymmetric.transformation = RSA/None/PKCS1Padding
 * asymmetric.key.store.path = /tmp/keystore.jks
 * asymmetric.key.store.password = changeit
 * asymmetric.key.store.alias = key1
 * </pre></blockquote>
 *
 * Output data representation:
 * <blockquote><pre>
 * +--------------------------------------------------------------+
 * | RSA encoded secret | initialization vector  | AES encrypted  |
 * | key used for AES   | (optional, 8-16 bytes) |      data      |
 * +--------------------------------------------------------------+
 * </pre></blockquote>
 */
public class HybridEncryptSerializer implements Serializer<byte[]> {
	private static final SecureRandom random = new SecureRandom();

	private HybridEncryptSerializerConfig config = null;
	private PublicKey publicKey = null;

	@Override
	public void configure(Map<String, ?> configs, boolean isKey) {
		config = new HybridEncryptSerializerConfig( configs );
		try {
			final KeyStore keyStore = EncryptionHelper.loadKeyStore(
					config.getAsymmetricKeyStorePath(), config.getAsymmetricKeyStoreType(),
					config.getAsymmetricKeyStorePassword().toCharArray()
			);
			final Certificate certificate = keyStore.getCertificate( config.getAsymmetricKeyAlias() );
			if ( certificate == null ) {
				throw new ConfigException(
						String.format( "Could not find alias '%s' in key store '%s'.", config.getAsymmetricKeyAlias(), config.getAsymmetricKeyStorePath() )
				);
			}
			publicKey = certificate.getPublicKey();
		}
		catch ( Exception e ) {
			throw new ConfigException( String.format( "Failed to retrieve public key: %s.", e.getMessage() ), e );
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
			// Generate random secret key for symmetric encryption.
			final KeyGenerator keyGenerator = KeyGenerator.getInstance(
					EncryptionHelper.extractAlgorithm( config.getSymmetricTransformation() )
			);
			keyGenerator.init( EncryptionHelper.getKeyLength( publicKey ) / 8, random );
			final SecretKey secretKey = keyGenerator.generateKey();

			// Encrypt secret key with asymmetric algorithm.
			final Cipher asymmetricCipher = EncryptionHelper.initializeCipher(
					Cipher.ENCRYPT_MODE, config.getAsymmetricTransformation(),
					publicKey, null, outputStream
			);
			final InputStream keyInputStream = new ByteArrayInputStream( secretKey.getEncoded() );
			CipherInputStream cipherInputStream = new CipherInputStream( keyInputStream, asymmetricCipher );
			IOUtils.copy( cipherInputStream, outputStream );
			keyInputStream.close();
			cipherInputStream.close();

			// Encrypt data using symmetric algorithm.
			final Cipher symmetricCipher = EncryptionHelper.initializeCipher(
					Cipher.ENCRYPT_MODE, config.getSymmetricTransformation(),
					secretKey.getEncoded(), null, outputStream
			);
			cipherInputStream = new CipherInputStream( inputStream, symmetricCipher );
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
