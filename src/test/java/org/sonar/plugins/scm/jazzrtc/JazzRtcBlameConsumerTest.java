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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class JazzRtcBlameConsumerTest {
  private static String[] lines = {
    "1  Duarte (1058) 2015-05-29 11:23 AM  Share /* ",
    "2  Duarte (1058) 2015-05-29 11:23 AM  Share  * Sonar, open source software quality management tool. ",
    "3  Duarte (1058) 2015-05-29 11:23 AM  Share  * Copyright (C) 2008-2012 SonarSource ",
    "4  Duarte (1058) 2015-05-29 11:23 AM  Share  * mailto:contact AT sonarsource DOT com ",
    "5  Duarte (1058) 2015-05-29 11:23 AM  Share  * ",
    "6  Duarte (1058) 2015-05-29 11:23 AM  Share  * Sonar is free software; you can redistribute it and/or ",
    "7  Duarte (1058) 2015-05-29 11:23 AM  Share  * modify it under the terms of the GNU Lesser General Public ",
    "8  Duarte (1058) 2015-05-29 11:23 AM  Share  * License as published by the Free Software Foundation; either ",
    "9  Duarte (1058) 2015-05-29 11:23 AM  Share  * version 3 of the License, or (at your option) any later version. ",
    "10 Duarte (1058) 2015-05-29 11:23 AM  Share  * ",
    "11 Duarte (1058) 2015-05-29 11:23 AM  Share  * Sonar is distributed in the hope that it will be useful, ",
    "12 Duarte (1058) 2015-05-29 11:23 AM  Share  * but WITHOUT ANY WARRANTY; without even the implied warranty of ",
    "13 Duarte    (1058) 2015-05-29 11:23 AM  Share  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU "
  };

  @Test
  public void testAuthorParsing() {
    JazzRtcBlameConsumer consumer = new JazzRtcBlameConsumer("dummy.java");

    for (String l : lines) {
      consumer.consumeLine(l);
    }

    assertThat(consumer.getLines()).hasSize(lines.length);
    assertThat(consumer.getLines()).extracting("author").containsOnly("Duarte");
  }
}
