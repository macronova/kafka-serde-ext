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

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;

public abstract class BaseSignatureConfig extends AbstractConfig {
	public static final String ALGORITHM_CONFIG = "algorithm";
	public static final String ALGORITHM_DOC = "Digital signature algorithm. Example: SHA256withRSA.";

	public static final String KEY_STORE_PATH_CONFIG = "key.store.path";
	public static final String KEY_STORE_PATH_DOC = "Path to Java keystore.";

	public static final String KEY_STORE_PASSWORD_CONFIG = "key.store.password";
	public static final String KEY_STORE_PASSWORD_DOC = "Java keystore password.";

	public static final String KEY_STORE_TYPE_CONFIG = "key.store.type";
	public static final String KEY_STORE_TYPE_DEFAULT = "JKS";
	public static final String KEY_STORE_TYPE_DOC = "Java keystore type. Default: JKS.";

	public static final String KEY_ALIAS_CONFIG = "key.store.alias";
	public static final String KEY_ALIAS_DOC = "Alias of the key present in key store.";

	public BaseSignatureConfig(ConfigDef definition, Map<?, ?> originals) {
		super( definition, originals, false );
	}

	protected static ConfigDef baseConfigDef() {
		return new ConfigDef()
				.define( ALGORITHM_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH, ALGORITHM_DOC )
				.define( KEY_STORE_PATH_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH, KEY_STORE_PATH_DOC )
				.define( KEY_STORE_PASSWORD_CONFIG, ConfigDef.Type.PASSWORD, ConfigDef.Importance.HIGH, KEY_STORE_PASSWORD_DOC )
				.define( KEY_STORE_TYPE_CONFIG, ConfigDef.Type.STRING, KEY_STORE_TYPE_DEFAULT, ConfigDef.Importance.MEDIUM, KEY_STORE_TYPE_DOC )
				.define( KEY_ALIAS_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH, KEY_ALIAS_DOC );
	}

	public String getAlgorithm() {
		return getString( ALGORITHM_CONFIG );
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
