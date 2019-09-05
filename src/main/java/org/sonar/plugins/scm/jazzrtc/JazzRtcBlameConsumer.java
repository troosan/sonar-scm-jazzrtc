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

import org.sonar.api.batch.scm.BlameLine;
import org.sonar.api.utils.command.StreamConsumer;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JazzRtcBlameConsumer implements StreamConsumer {

  private static final Logger LOG = Loggers.get(JazzRtcBlameConsumer.class);

  private static final String JAZZ_TIMESTAMP_PATTERN = "yyyy-MM-dd hh:mm a";

  // 1 Julien HENRY (1008) 2011-12-14 09:14 AM Test.txt
  // 2 Julien HENRY (1005) 2011-12-14 09:14 AM My commit comment.

  private static final String LINE_PATTERN = "(\\d+)\\s+(.*?)\\s+\\((\\d+)\\) (\\d+-\\d+-\\d+ \\d+\\:\\d+ (AM|PM)) (.*)";

  private List<BlameLine> lines = new ArrayList<>();

  private DateFormat format;

  private final String filename;

  private Pattern pattern;

  public JazzRtcBlameConsumer(String filename) {
    this.filename = filename;
    this.format = new SimpleDateFormat(JAZZ_TIMESTAMP_PATTERN, Locale.ENGLISH);
    this.pattern = Pattern.compile(LINE_PATTERN);
  }

  @Override
  public void consumeLine(String line) {
    int expectingLine = getLines().size() + 1;
    Matcher matcher = pattern.matcher(line);
    if (!matcher.matches()) {
      // Probably code, ignore
      return;
    }
    String lineStr = matcher.group(1);
    int lineIdx;
    try {
      lineIdx = Integer.parseInt(lineStr);
    } catch (NumberFormatException e) {
      throw new IllegalStateException("Unable to blame file " + filename + ". Unrecognized blame info at line " + expectingLine + ": " + line);
    }
    if (expectingLine != lineIdx) {
      throw new IllegalStateException("Unable to blame file " + filename + ". Expecting blame info for line " + expectingLine + " but was " + lineIdx + ": " + line);
    }
    String owner = matcher.group(2);
    String changeSetNumberStr = matcher.group(3);
    String dateStr = matcher.group(4);
    Date date = parseDate(dateStr);
    lines.add(new BlameLine().date(date).revision(changeSetNumberStr).author(owner));
  }

  /**
   * Converts the date timestamp from the output into a date object.
   *
   * @return A date representing the timestamp of the log entry.
   */
  protected Date parseDate(String date) {
    try {
      return format.parse(date);
    } catch (ParseException e) {
      LOG.warn(
        "skip ParseException: " + e.getMessage() + " during parsing date " + date
          + " with pattern " + JAZZ_TIMESTAMP_PATTERN + " with Locale " + Locale.ENGLISH, e);
      return null;
    }
  }

  public List<BlameLine> getLines() {
    return lines;
  }
}
