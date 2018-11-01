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

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.UUID;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public abstract class BaseTestCase {
	protected static final SecureRandom random = new SecureRandom();
	private static final BouncyCastleProvider provider = new BouncyCastleProvider();
	private static final String keyStorePath = System.getProperty( "java.io.tmpdir" ) + File.separator + UUID.randomUUID().toString();

	@BeforeClass
	public static void createKeyStore() throws Exception {
		final KeyPairGenerator generator = KeyPairGenerator.getInstance( "RSA", provider );
		generator.initialize( 2048, SecureRandom.getInstance( "SHA1PRNG", "SUN" ) );
		final KeyPair keyPair = generator.generateKeyPair();

		final X500Name issuer = new X500Name( "cn=Unknown" );
		final Date from = new Date();
		final Date to = new Date( from.getTime() + 1000L * 24L * 60L * 60L );
		final JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
				issuer, BigInteger.ONE, from, to, issuer, keyPair.getPublic()
		);
		final X509CertificateHolder holder = builder.build(
				new JcaContentSignerBuilder( "SHA1WithRSA" ).setProvider( provider ).build( keyPair.getPrivate() )
		);
		final X509Certificate certificate = new JcaX509CertificateConverter().setProvider( provider ).getCertificate( holder );
		certificate.checkValidity( new Date() );
		certificate.verify( keyPair.getPublic() );

		final KeyStore keyStore = KeyStore.getInstance( "JKS" );
		keyStore.load( null, null );
		final FileOutputStream fileOutputStream = new FileOutputStream( keyStorePath() );
		keyStore.setKeyEntry(
				keyAlias(), keyPair.getPrivate(), keyAliasPassword().toCharArray(), new Certificate[] { certificate }
		);
		keyStore.store( fileOutputStream, keyStorePassword().toCharArray() );
		fileOutputStream.close();
	}

	@AfterClass
	public static void removeKeyStore() throws Exception {
		Files.deleteIfExists( Paths.get( keyStorePath() ) );
	}

	protected static String keyStorePath() {
		return keyStorePath;
	}

	protected static String keyAlias() {
		return "my-key";
	}

	protected static String keyAliasPassword() {
		return "changeit";
	}

	protected static String keyStorePassword() {
		return "notchange";
	}
}
