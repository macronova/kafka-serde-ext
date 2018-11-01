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

import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import io.macronova.kafka.common.serialization.config.DecryptDeserializerConfig;
import io.macronova.kafka.common.serialization.utils.EncryptionHelper;
import io.macronova.kafka.common.serialization.utils.IOUtils;

/**
 * Decrypt data with secret key or certificate loaded from keystore. If chosen cipher requires initialization vector,
 * deserializer expects to read it from the beginning of encoded content. Length of initialization vector
 * is dynamically calculated based on chosen cipher suite (see {@link EncryptionHelper#ivLength(String, Cipher)}).
 * <p/>
 *
 * Example configuration (secret key):
 * <blockquote><pre>
 * transformation = AES/ECB/PKCS5Padding
 * secret = 770A8A65DA156D24EE2A093277530142
 * </pre></blockquote>
 *
 * Example configuration (private key decryption):
 * <blockquote><pre>
 * transformation = RSA/None/PKCS1Padding
 * key.store.path = /tmp/keystore.jks
 * key.store.password = changeit
 * key.store.alias = key1
 * key.store.alias.password = donotchange
 * </pre></blockquote>
 *
 * Deserializer expects input data representation analogical to output generated by {@link EncryptSerializer}.
 */
public class DecryptDeserializer implements Deserializer<byte[]> {
	private DecryptDeserializerConfig config = null;
	private PrivateKey privateKey = null;

	@Override
	public void configure(Map<String, ?> configs, boolean isKey) {
		config = new DecryptDeserializerConfig( configs );
		if ( config.useCertificate() ) {
			try {
				final KeyStore keyStore = EncryptionHelper.loadKeyStore(
						config.getKeyStorePath(), config.getKeyStoreType(), config.getKeyStorePassword().toCharArray()
				);
				final KeyStore.PasswordProtection keyPassword = new KeyStore.PasswordProtection(
						config.getKeyAliasPassword().toCharArray()
				);
				final KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(
						config.getKeyAlias(), keyPassword
				);
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
			final Cipher cipher = config.useCertificate()
					? EncryptionHelper.initializeCipher(
							Cipher.DECRYPT_MODE, config.getTransformation(), privateKey, inputStream, null
					)
					: EncryptionHelper.initializeCipher(
							Cipher.DECRYPT_MODE, config.getTransformation(), config.getSecret(), inputStream, null
					);

			final CipherInputStream cipherInputStream = new CipherInputStream( inputStream, cipher );
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