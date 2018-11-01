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
import java.security.PrivateKey;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;

import io.macronova.kafka.common.serialization.config.HybridDecryptDeserializerConfig;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import io.macronova.kafka.common.serialization.utils.EncryptionHelper;
import io.macronova.kafka.common.serialization.utils.IOUtils;

/**
 * See {@link HybridEncryptSerializer}.
 * <p/>
 *
 * Example configuration:
 * <blockquote><pre>
 * symmetric.transformation = AES/CBC/PKCS5Padding
 * asymmetric.transformation = RSA/None/PKCS1Padding
 * asymmetric.key.store.path = /tmp/keystore.jks
 * asymmetric.key.store.password = changeit
 * asymmetric.key.store.alias = key1
 * asymmetric.key.store.alias.password = donotchange
 * </pre></blockquote>
 */
public class HybridDecryptDeserializer implements Deserializer<byte[]> {
	private HybridDecryptDeserializerConfig config = null;
	private PrivateKey privateKey = null;

	@Override
	public void configure(Map<String, ?> configs, boolean isKey) {
		config = new HybridDecryptDeserializerConfig( configs );
		try {
			final KeyStore keyStore = EncryptionHelper.loadKeyStore(
					config.getAsymmetricKeyStorePath(), config.getAsymmetricKeyStoreType(),
					config.getAsymmetricKeyStorePassword().toCharArray()
			);
			final KeyStore.PasswordProtection keyPassword = new KeyStore.PasswordProtection(
					config.getAsymmetricKeyAliasPassword().toCharArray()
			);
			final KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(
					config.getAsymmetricKeyAlias(), keyPassword
			);
			if ( privateKeyEntry == null ) {
				throw new ConfigException(
						String.format( "Could not find alias '%s' in key store '%s'.", config.getAsymmetricKeyAlias(), config.getAsymmetricKeyStorePath() )
				);
			}
			privateKey = privateKeyEntry.getPrivateKey();
		}
		catch ( Exception e ) {
			throw new ConfigException( String.format( "Failed to retrieve private key: %s.", e.getMessage() ), e );
		}
	}

	@Override
	public byte[] deserialize(String topic, byte[] data) {
		if ( data == null ) {
			return null;
		}
		InputStream inputStream = null;
		ByteArrayOutputStream outputStream = null;
		try {
			inputStream = new ByteArrayInputStream( data );
			outputStream = new ByteArrayOutputStream();

			// Read and decrypt random symmetric key.
			final Cipher asymmetricCipher = EncryptionHelper.initializeCipher(
					Cipher.DECRYPT_MODE, config.getAsymmetricTransformation(),
					privateKey, inputStream, null
			);
			final byte[] keyEncrypted = new byte[ EncryptionHelper.getKeyLength( privateKey ) / 8 ];
			if ( inputStream.read( keyEncrypted ) != keyEncrypted.length ) {
				throw new SerializationException( "Unexpected end of encrypted content." );
			}
			final ByteArrayInputStream keyInputStream = new ByteArrayInputStream( keyEncrypted );
			final ByteArrayOutputStream keyOutputStream = new ByteArrayOutputStream();
			CipherInputStream cipherInputStream = new CipherInputStream( keyInputStream, asymmetricCipher );
			IOUtils.copy( cipherInputStream, keyOutputStream );
			keyInputStream.close();
			cipherInputStream.close();
			keyOutputStream.close();
			final byte[] keyDecrypted = keyOutputStream.toByteArray();

			// Decrypt payload.
			final Cipher symmetricCipher = EncryptionHelper.initializeCipher(
					Cipher.DECRYPT_MODE, config.getSymmetricTransformation(), keyDecrypted, inputStream, null
			);
			cipherInputStream = new CipherInputStream( inputStream, symmetricCipher );
			IOUtils.copy( cipherInputStream, outputStream );
			cipherInputStream.close();

			return outputStream.toByteArray();
		}
		catch ( Exception e ) {
			throw new SerializationException( String.format( "Failed to decrypt content: %s.", e.getMessage() ), e );
		}
		finally {
			IOUtils.closeQuietly( inputStream );
			IOUtils.closeQuietly( outputStream );
		}
	}

	@Override
	public void close() {
		config = null;
		privateKey = null;
	}
}
