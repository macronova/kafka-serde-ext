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

import javax.xml.bind.DatatypeConverter;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.config.types.Password;

public abstract class BaseEncryptConfig extends AbstractConfig {
	public static final String TRANSFORMATION_CONFIG = "transformation";
	public static final String TRANSFORMATION_DOC = "Cryptography transformation that consists " +
			"of algorithm, mode and padding. Example: AES/CBC/PKCS5Padding.";

	public static final String KEY_STORE_PATH_CONFIG = "key.store.path";
	public static final String KEY_STORE_PATH_DOC = "Path to Java keystore. " +
			"Required in case of certificate encryption.";

	public static final String KEY_STORE_PASSWORD_CONFIG = "key.store.password";
	public static final String KEY_STORE_PASSWORD_DOC = "Java keystore password. " +
			"Required in case of certificate encryption.";

	public static final String KEY_STORE_TYPE_CONFIG = "key.store.type";
	public static final String KEY_STORE_TYPE_DEFAULT = "JKS";
	public static final String KEY_STORE_TYPE_DOC = "Java keystore type. Default: JKS.";

	public static final String KEY_ALIAS_CONFIG = "key.store.alias";
	public static final String KEY_ALIAS_DOC = "Alias of the key present in key store. " +
			"Required in case of certificate encryption.";

	public static final String SECRET_CONFIG = "secret";
	public static final String SECRET_DOC = "Encryption key in hexadecimal format. Required in " +
			"case of passphrase encryption.";

	public BaseEncryptConfig(ConfigDef definition, Map<?, ?> originals) {
		super( definition, originals, false );
	}

	protected static ConfigDef baseConfigDef() {
		return new ConfigDef()
				.define( TRANSFORMATION_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH, TRANSFORMATION_DOC )
				.define( KEY_STORE_PATH_CONFIG, ConfigDef.Type.STRING, null, ConfigDef.Importance.HIGH, KEY_STORE_PATH_DOC )
				.define( KEY_STORE_PASSWORD_CONFIG, ConfigDef.Type.PASSWORD, null, ConfigDef.Importance.HIGH, KEY_STORE_PASSWORD_DOC )
				.define( KEY_STORE_TYPE_CONFIG, ConfigDef.Type.STRING, KEY_STORE_TYPE_DEFAULT, ConfigDef.Importance.MEDIUM, KEY_STORE_TYPE_DOC )
				.define( KEY_ALIAS_CONFIG, ConfigDef.Type.STRING, null, ConfigDef.Importance.HIGH, KEY_ALIAS_DOC )
				.define( SECRET_CONFIG, ConfigDef.Type.PASSWORD, null, ConfigDef.Importance.HIGH, SECRET_DOC );
	}

	protected void validate() {
		if ( ( getString( KEY_STORE_PATH_CONFIG ) == null && getPassword( SECRET_CONFIG ) == null )
				|| ( getString( KEY_STORE_PATH_CONFIG ) != null && getPassword( SECRET_CONFIG ) != null ) ) {
			throw new ConfigException( String.format( "Exactly one of the following properties need to be specified: %s, %s.", KEY_STORE_PATH_CONFIG, SECRET_CONFIG ) );
		}
		if ( useCertificate() ) {
			if ( getPassword( KEY_STORE_PASSWORD_CONFIG ) == null ) {
				throw new ConfigException(
						String.format( "Property '%s' cannot be empty when declared '%s'.", KEY_STORE_PASSWORD_CONFIG, KEY_STORE_PATH_CONFIG )
				);
			}
			if ( getString( KEY_ALIAS_CONFIG ) == null ) {
				throw new ConfigException(
						String.format( "Property '%s' cannot be empty when declared '%s'.", KEY_ALIAS_CONFIG, KEY_STORE_PATH_CONFIG )
				);
			}
		}
	}

	public boolean useCertificate() {
		return getString( KEY_STORE_PATH_CONFIG ) != null;
	}

	public String getTransformation() {
		return getString( TRANSFORMATION_CONFIG );
	}

	public byte[] getSecret() {
		final Password secret = getPassword( SECRET_CONFIG );
		if ( secret != null ) {
			return DatatypeConverter.parseHexBinary( secret.value() );
		}
		return null;
	}

	public String getKeyStorePath() {
		return getString( KEY_STORE_PATH_CONFIG );
	}

	public String getKeyStorePassword() {
		return getPassword( KEY_STORE_PASSWORD_CONFIG ).value();
	}

	public String getKeyStoreType() {
		return getString( KEY_STORE_TYPE_CONFIG );
	}

	public String getKeyAlias() {
		return getString( KEY_ALIAS_CONFIG );
	}
}
