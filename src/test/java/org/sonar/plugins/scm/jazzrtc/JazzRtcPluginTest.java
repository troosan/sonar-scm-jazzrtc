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

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonar.api.SonarRuntime;

public class JazzRtcPluginTest {

  @Mock
  private SonarRuntime sonarRuntime;

  @Before
  public void prepare() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void getExtensions() {
    org.sonar.api.Plugin.Context context = new org.sonar.api.Plugin.Context(sonarRuntime);

    new JazzRtcPlugin().define(context);
    assertThat(context.getExtensions()).hasSize(6);
  }
}
