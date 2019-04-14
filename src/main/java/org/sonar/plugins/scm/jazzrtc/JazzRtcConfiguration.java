/*
 * SonarQube :: Plugins :: SCM :: Jazz RTC
 * Copyright (C) 2014-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugins.scm.jazzrtc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.CheckForNull;

import org.sonar.api.BatchComponent;
import org.sonar.api.CoreProperties;
import org.sonar.api.PropertyType;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.Settings;
import org.sonar.api.resources.Qualifiers;

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
public class JazzRtcConfiguration implements BatchComponent {

	private static final String CATEGORY_JAZZ = "Jazz RTC";
	public static final String CMD_TIMEOUT = "sonar.jazzrtc.cmd.timeout";
	public static final String USER_PROP_KEY = "sonar.jazzrtc.username";
	public static final String PASSWORD_PROP_KEY = "sonar.jazzrtc.password.secured";

	private final Settings settings;

	public JazzRtcConfiguration(Settings settings) {
		this.settings = settings;
	}

	public static List<PropertyDefinition> getProperties() {

		List<PropertyDefinition> listPropertiesDefinition = new ArrayList<PropertyDefinition>();

		listPropertiesDefinition.add(PropertyDefinition.builder(USER_PROP_KEY).name("Username")
				.description("Username to be used for Jazz RTC authentication").type(PropertyType.STRING)
				.onQualifiers(Qualifiers.PROJECT).category(CoreProperties.CATEGORY_SCM).subCategory(CATEGORY_JAZZ)
				.index(0).build());

		listPropertiesDefinition.add(PropertyDefinition.builder(PASSWORD_PROP_KEY).name("Password")
				.description("Password to be used for Jazz RTC authentication").type(PropertyType.PASSWORD)
				.onQualifiers(Qualifiers.PROJECT).category(CoreProperties.CATEGORY_SCM).subCategory(CATEGORY_JAZZ)
				.index(1).build());

		listPropertiesDefinition.add(PropertyDefinition.builder(CMD_TIMEOUT).name("CMD Timeout")
				.description("Timeout to be used for Jazz RTC Annotate command").type(PropertyType.INTEGER)
				.defaultValue("60000").onQualifiers(Qualifiers.PROJECT).category(CoreProperties.CATEGORY_SCM)
				.subCategory(CATEGORY_JAZZ).index(2).build());

		return Collections.unmodifiableList(listPropertiesDefinition);
		
	}

	@CheckForNull
	public String username() {
		return settings.getString(USER_PROP_KEY);
	}

	@CheckForNull
	public String password() {
		return settings.getString(PASSWORD_PROP_KEY);
	}

	public long commandTimeout() {
		long defaultCommandTimeout = 60_000;
		int alternativeCommandTimeout = settings.getInt(CMD_TIMEOUT);

		if (alternativeCommandTimeout != 0)
			defaultCommandTimeout = alternativeCommandTimeout;

		return defaultCommandTimeout;
	}

}
