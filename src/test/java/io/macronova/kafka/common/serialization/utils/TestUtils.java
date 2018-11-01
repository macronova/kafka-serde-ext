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
package io.macronova.kafka.common.serialization.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

public abstract class TestUtils {
	public static byte[] reverse(byte[] data) {
		for ( int i = 0; i < data.length / 2; ++i ) {
			byte tmp = data[i];
			data[i] = data[data.length - i - 1];
			data[data.length - i - 1] = tmp;
		}
		return data;
	}

	public static byte[] decrypt(byte[] data, byte[] iv, String secret, String transformation) throws Exception {
		final Cipher cipher = Cipher.getInstance( transformation );
		final SecretKeySpec keySpec = new SecretKeySpec(
				DatatypeConverter.parseHexBinary( secret ), EncryptionHelper.extractAlgorithm( transformation )
		);
		if ( iv != null ) {
			cipher.init( Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec( iv ) );
		}
		else {
			cipher.init( Cipher.DECRYPT_MODE, keySpec );
		}

		final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		final CipherInputStream cipherInputStream = new CipherInputStream( new ByteArrayInputStream( data ), cipher );
		IOUtils.copy( cipherInputStream, outputStream );
		cipherInputStream.close();
		outputStream.close();

		return outputStream.toByteArray();
	}

	public static byte[] decrypt(byte[] data, byte[] iv, File keystore, String alias, String keyStorePassword,
								 String aliasPassword, String transformation) throws Exception {
		final KeyStore keyStore = KeyStore.getInstance( "JKS" );
		final InputStream keyStoreStream = new FileInputStream( keystore );
		keyStore.load( keyStoreStream, keyStorePassword.toCharArray() );
		final KeyStore.PasswordProtection keyPassword = new KeyStore.PasswordProtection( aliasPassword.toCharArray() );
		final KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry( alias, keyPassword );
		final PrivateKey privateKey = privateKeyEntry.getPrivateKey();
		keyStoreStream.close();

		final Cipher cipher = Cipher.getInstance( transformation );
		if ( iv != null ) {
			cipher.init( Cipher.DECRYPT_MODE, privateKey, new IvParameterSpec( iv ) );
		}
		else {
			cipher.init( Cipher.DECRYPT_MODE, privateKey );
		}

		final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		final CipherInputStream cipherInputStream = new CipherInputStream( new ByteArrayInputStream( data ), cipher );
		IOUtils.copy( cipherInputStream, outputStream );
		cipherInputStream.close();
		outputStream.close();

		return outputStream.toByteArray();
	}

	public static void waitForCondition(final TestCondition testCondition, final long maxWaitMs,
										String conditionDetails) throws InterruptedException {
		final long startTime = System.currentTimeMillis();
		boolean testConditionMet = false;
		while ( !( testConditionMet = testCondition.conditionMet() ) && ( ( System.currentTimeMillis() - startTime ) < maxWaitMs ) ) {
			Thread.sleep( Math.min( maxWaitMs, 100L ) );
		}
		if ( ! testConditionMet ) {
			conditionDetails = conditionDetails != null ? conditionDetails : "";
			throw new AssertionError( "Condition not met within timeout " + maxWaitMs + ". " + conditionDetails );
		}
	}

}
