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
package io.macronova.kafka.common.serialization.config;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;

public abstract class BaseHybridEncryptConfig extends AbstractConfig {
	public static final String SYMMETRIC_TRANSFORMATION_CONFIG = "symmetric.transformation";
	public static final String SYMMETRIC_TRANSFORMATION_DOC = "Cryptography transformation that consists " +
			"of algorithm, mode and padding. Hybrid serializer encrypts data with symmetric algorithm. " +
			"Example: AES/CBC/PKCS5Padding.";

	public static final String ASYMMETRIC_TRANSFORMATION_CONFIG = "asymmetric.transformation";
	public static final String ASYMMETRIC_TRANSFORMATION_DOC = "Cryptography transformation that consists " +
			"of algorithm, mode and padding. Hybrid serializer encrypts randomly generated key (used for " +
			"symmetric encryption) with asymmetric algorithm. Example: RSA.";

	public static final String ASYMMETRIC_KEY_STORE_PATH_CONFIG = "asymmetric.key.store.path";
	public static final String ASYMMETRIC_KEY_STORE_PATH_DOC = "Path to Java keystore.";

	public static final String ASYMMETRIC_KEY_STORE_PASSWORD_CONFIG = "asymmetric.key.store.password";
	public static final String ASYMMETRIC_KEY_STORE_PASSWORD_DOC = "Java keystore password.";

	public static final String ASYMMETRIC_KEY_STORE_TYPE_CONFIG = "asymmetric.key.store.type";
	public static final String ASYMMETRIC_KEY_STORE_TYPE_DEFAULT = "JKS";
	public static final String ASYMMETRIC_KEY_STORE_TYPE_DOC = "Java keystore type. Default: JKS.";

	public static final String ASYMMETRIC_KEY_ALIAS_CONFIG = "asymmetric.key.store.alias";
	public static final String ASYMMETRIC_KEY_ALIAS_DOC = "Alias of the key present in key store.";

	private static final Pattern transformationPattern = Pattern.compile( "^(.+)/(.+)/(.+)$" );

	public BaseHybridEncryptConfig(ConfigDef definition, Map<?, ?> originals) {
		super( definition, originals, false );
	}

	protected static ConfigDef baseConfigDef() {
		return new ConfigDef()
				.define( SYMMETRIC_TRANSFORMATION_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH, SYMMETRIC_TRANSFORMATION_DOC )
				.define( ASYMMETRIC_TRANSFORMATION_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH, ASYMMETRIC_TRANSFORMATION_DOC )
				.define( ASYMMETRIC_KEY_STORE_PATH_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH, ASYMMETRIC_KEY_STORE_PATH_DOC )
				.define( ASYMMETRIC_KEY_STORE_PASSWORD_CONFIG, ConfigDef.Type.PASSWORD, ConfigDef.Importance.HIGH, ASYMMETRIC_KEY_STORE_PASSWORD_DOC )
				.define( ASYMMETRIC_KEY_STORE_TYPE_CONFIG, ConfigDef.Type.STRING, ASYMMETRIC_KEY_STORE_TYPE_DEFAULT, ConfigDef.Importance.MEDIUM, ASYMMETRIC_KEY_STORE_TYPE_DOC )
				.define( ASYMMETRIC_KEY_ALIAS_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH, ASYMMETRIC_KEY_ALIAS_DOC );
	}

	protected void validate() {
		final Matcher matcher = transformationPattern.matcher( getAsymmetricTransformation() );
		if ( ! matcher.matches() ) {
			throw new ConfigException( "Asymmetric transformation has to include padding." );
		}
	}

	public String getSymmetricTransformation() {
		return getString( SYMMETRIC_TRANSFORMATION_CONFIG );
	}

	public String getAsymmetricTransformation() {
		return getString( ASYMMETRIC_TRANSFORMATION_CONFIG );
	}

	public String getAsymmetricKeyStorePath() {
		return getString( ASYMMETRIC_KEY_STORE_PATH_CONFIG );
	}

	public String getAsymmetricKeyStorePassword() {
		return getPassword( ASYMMETRIC_KEY_STORE_PASSWORD_CONFIG ).value();
	}

	public String getAsymmetricKeyStoreType() {
		return getString( ASYMMETRIC_KEY_STORE_TYPE_CONFIG );
	}

	public String getAsymmetricKeyAlias() {
		return getString( ASYMMETRIC_KEY_ALIAS_CONFIG );
	}
}
