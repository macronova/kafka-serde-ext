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

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;

public class DecryptDeserializerConfig extends BaseEncryptConfig {
	private static ConfigDef config = baseConfigDef();

	public static final String KEY_ALIAS_PASSWORD_CONFIG = "key.store.alias.password";
	public static final String KEY_ALIAS_PASSWORD_DOC = "Password to access private key present in key store. " +
			"Required in case of certificate decryption.";

	public DecryptDeserializerConfig(Map<?, ?> originals) {
		super( config, originals );
		validate();
	}

	protected static ConfigDef baseConfigDef() {
		final ConfigDef config = BaseEncryptConfig.baseConfigDef();
		config.define( KEY_ALIAS_PASSWORD_CONFIG, ConfigDef.Type.PASSWORD, null, ConfigDef.Importance.HIGH, KEY_ALIAS_PASSWORD_DOC );
		return config;
	}

	@Override
	protected void validate() {
		super.validate();
		if ( useCertificate() ) {
			if ( getPassword( KEY_ALIAS_PASSWORD_CONFIG ) == null ) {
				throw new ConfigException(
						String.format( "Property '%s' cannot be empty when declared '%s'.", KEY_ALIAS_PASSWORD_CONFIG, KEY_STORE_PATH_CONFIG )
				);
			}
		}
	}

	public String getKeyAliasPassword() {
		return getPassword( KEY_ALIAS_PASSWORD_CONFIG ).value();
	}
}
