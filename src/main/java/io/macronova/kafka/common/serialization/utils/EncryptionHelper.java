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

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECParameterSpec;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.kafka.common.errors.SerializationException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public abstract class EncryptionHelper {
	private static final BouncyCastleProvider provider = new BouncyCastleProvider();
	private static final SecureRandom random = new SecureRandom();

	/**
	 * Initialize cipher. Depending on mode, function:
	 * <ul>
	 * 		<li>generates random initialization vector and appends it to output stream (encryption).</li>
	 * 		<li>reads initialization vector from input stream (decryption).</li>
	 * </ul>
	 *
	 * @param mode Encrypt or decrypt mode.
	 * @param transformation Encryption algorithm.
	 * @param key Private or public key depending on usage.
	 * @param inputStream Encrypted input stream to read IV from. May be {@code null} in case of encryption mode.
	 * @param outputStream Encryption output stream. May be {@code null} in case of decryption mode.
	 * @return Initialized cipher.
	 * @throws Exception Indicates failure.
	 */
	public static Cipher initializeCipher(int mode, String transformation, Key key,
										  InputStream inputStream, OutputStream outputStream) throws Exception {
		final Cipher cipher = Cipher.getInstance( transformation, provider );
		if ( requiresIV( transformation ) ) {
			byte[] iv = null;
			switch ( mode ) {
				case Cipher.ENCRYPT_MODE:
					iv = randomIV( transformation, cipher );
					outputStream.write( iv );
					break;
				case Cipher.DECRYPT_MODE:
					int ivLength = ivLength( transformation, cipher );
					iv = new byte[ ivLength ];
					if ( inputStream.read( iv ) != ivLength ) {
						throw new SerializationException( "Unexpected end of encrypted content." );
					}
					break;
			}
			cipher.init( mode, key, new IvParameterSpec( iv ) );
		}
		else {
			cipher.init( mode, key );
		}
		return cipher;
	}

	/**
	 * Initialize cipher. Depending on mode, function:
	 * <ul>
	 * 		<li>generates random initialization vector and appends it to output stream (encryption).</li>
	 * 		<li>reads initialization vector from input stream (decryption).</li>
	 * </ul>
	 *
	 * @param mode Encrypt or decrypt mode.
	 * @param transformation Encryption algorithm.
	 * @param secret Symmetric cryptography secret key.
	 * @param inputStream Encrypted input stream to read IV from. May be {@code null} in case of encryption mode.
	 * @param outputStream Encryption output stream. May be {@code null} in case of decryption mode.
	 * @return Initialized cipher.
	 * @throws Exception Indicates failure.
	 */
	public static Cipher initializeCipher(int mode, String transformation, byte[] secret,
										  InputStream inputStream, OutputStream outputStream) throws Exception {
		final Cipher cipher = Cipher.getInstance( transformation, provider );
		final SecretKeySpec keySpec = new SecretKeySpec( secret, extractAlgorithm( transformation ) );
		if ( requiresIV( transformation ) ) {
			byte[] iv = null;
			switch ( mode ) {
				case Cipher.ENCRYPT_MODE:
					iv = randomIV( transformation, cipher );
					outputStream.write( iv );
					break;
				case Cipher.DECRYPT_MODE:
					int ivLength = ivLength( transformation, cipher );
					iv = new byte[ ivLength ];
					if ( inputStream.read( iv ) != ivLength ) {
						throw new SerializationException( "Unexpected end of encrypted content." );
					}
					break;
			}
			cipher.init( mode, keySpec, new IvParameterSpec( iv ) );
		}
		else {
			cipher.init( mode, keySpec );
		}
		return cipher;
	}

	public static Signature initializeSignature(String algorithm, Key key, boolean sign) throws Exception {
		final Signature signature = Signature.getInstance( algorithm, provider );
		if ( sign ) {
			signature.initSign( (PrivateKey) key, random );
		}
		else {
			signature.initVerify( (PublicKey) key );
		}
		return signature;
	}

	public static KeyStore loadKeyStore(String keystorePath, String type, char[] password) throws Exception {
		InputStream keyStoreStream = null;
		try {
			final KeyStore keyStore = KeyStore.getInstance( type );
			keyStoreStream = new FileInputStream( keystorePath );
			keyStore.load( keyStoreStream, password );
			return keyStore;
		}
		finally {
			IOUtils.closeQuietly( keyStoreStream );
		}
	}

	public static int getKeyLength(final PublicKey pk) {
		int len = -1;
		if ( pk instanceof RSAPublicKey ) {
			final RSAPublicKey rsa = (RSAPublicKey) pk;
			len = rsa.getModulus().bitLength();
		}
		else if ( pk instanceof ECPublicKey ) {
			final ECPublicKey ec = (ECPublicKey) pk;
			final ECParameterSpec spec = ec.getParams();
			len = spec.getOrder().bitLength();
		}
		else if ( pk instanceof DSAPublicKey ) {
			final DSAPublicKey dsa = (DSAPublicKey) pk;
			if ( dsa.getParams() != null ) {
				len = dsa.getParams().getP().bitLength();
			}
			else {
				len = dsa.getY().bitLength();
			}
		}
		return len;
	}

	public static int getKeyLength(final PrivateKey pk) {
		int len = -1;
		if ( pk instanceof RSAPrivateKey ) {
			final RSAPrivateKey rsa = (RSAPrivateKey) pk;
			len = rsa.getModulus().bitLength();
		}
		else if ( pk instanceof ECPrivateKey ) {
			final ECPrivateKey ec = (ECPrivateKey) pk;
			final ECParameterSpec spec = ec.getParams();
			len = spec.getOrder().bitLength();
		}
		else if ( pk instanceof DSAPrivateKey ) {
			final DSAPrivateKey dsa = (DSAPrivateKey) pk;
			if ( dsa.getParams() != null ) {
				len = dsa.getParams().getP().bitLength();
			}
			else {
				len = dsa.getX().bitLength();
			}
		}
		return len;
	}

	public static boolean requiresIV(String transformation) {
		return transformation.contains( "/CBC/" ) || transformation.contains( "/CTR/" )
				|| transformation.contains( "/CCM/" ) || transformation.contains( "/GCM/" )
				|| transformation.contains( "/OFB/" ) || transformation.contains( "/OCB/" );
	}

	public static int ivLength(String transformation, Cipher cipher) {
		if ( transformation.contains( "/CBC/" ) ) {
			return cipher.getBlockSize();
		}
		else if ( transformation.contains( "/CCM/" ) || transformation.contains( "/GCM/" )
				|| transformation.contains( "/OCB/" ) ) {
			return 12;
		}
		return 16;
	}

	public static String extractAlgorithm(String transformation) {
		return transformation.contains( "/" ) ? transformation.substring( 0, transformation.indexOf( "/" ) ) : transformation;
	}

	public static byte[] randomIV(String transformation, Cipher cipher) {
		final byte randomIV[] = new byte[ ivLength( transformation, cipher ) ];
		random.nextBytes( randomIV );
		return randomIV;
	}
}
