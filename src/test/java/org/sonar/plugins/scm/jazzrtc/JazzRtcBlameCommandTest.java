/*
 * SonarQube :: Plugins :: SCM :: Jazz RTC
 * Copyright (C) 2014 SonarSource
 * dev@sonar.codehaus.org
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.scm.jazzrtc;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.scm.BlameCommand.BlameInput;
import org.sonar.api.batch.scm.BlameCommand.BlameOutput;
import org.sonar.api.batch.scm.BlameLine;
import org.sonar.api.config.Settings;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonar.api.utils.command.StreamConsumer;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JazzRtcBlameCommandTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private DefaultFileSystem fs;
  private File baseDir;
  private BlameInput input;

  @Before
  public void prepare() throws IOException {
    baseDir = temp.newFolder();
    fs = new DefaultFileSystem();
    fs.setBaseDir(baseDir);
    input = mock(BlameInput.class);
    when(input.fileSystem()).thenReturn(fs);
  }

  @Test
  public void testParsingOfOutput() throws IOException {
    File source = new File(baseDir, "src/foo.xoo");
    FileUtils.write(source, "sample content");
    DefaultInputFile inputFile = new DefaultInputFile("foo", "src/foo.xoo").setLines(3).setAbsolutePath(new File(baseDir, "src/foo.xoo").getAbsolutePath());
    fs.add(inputFile);

    BlameOutput result = mock(BlameOutput.class);
    CommandExecutor commandExecutor = mock(CommandExecutor.class);

    when(commandExecutor.execute(any(Command.class), any(StreamConsumer.class), any(StreamConsumer.class), anyLong())).thenAnswer(new Answer<Integer>() {

      @Override
      public Integer answer(InvocationOnMock invocation) throws Throwable {
        StreamConsumer outConsumer = (StreamConsumer) invocation.getArguments()[1];
        outConsumer.consumeLine("1 Julien HENRY (1000) 2014-12-09 09:14 AM  Partager foo");
        outConsumer.consumeLine("2 Julien HENRY (1000) 2014-12-09 09:14 AM  Partager ");
        outConsumer.consumeLine("3 Julien HENRY (1000) 2014-12-09 09:14 AM  Partager bar");
        return 0;
      }
    });

    when(input.filesToBlame()).thenReturn(Arrays.<InputFile>asList(inputFile));
    new JazzRtcBlameCommand(commandExecutor, new JazzRtcConfiguration(new Settings())).blame(input, result);
    verify(result).blameResult(inputFile,
      Arrays.asList(new BlameLine().date(DateUtils.parseDateTime("2014-12-09T09:14:00+0100")).revision("1000").author("Julien HENRY"),
        new BlameLine().date(DateUtils.parseDateTime("2014-12-09T09:14:00+0100")).revision("1000").author("Julien HENRY"),
        new BlameLine().date(DateUtils.parseDateTime("2014-12-09T09:14:00+0100")).revision("1000").author("Julien HENRY")));
  }

  @Test
  public void testParsingOfOutputWithWrappedCode() throws IOException {
    File source = new File(baseDir, "src/foo.xoo");
    FileUtils.write(source, "sample content");
    DefaultInputFile inputFile = new DefaultInputFile("foo", "src/foo.xoo").setLines(3).setAbsolutePath(new File(baseDir, "src/foo.xoo").getAbsolutePath());
    fs.add(inputFile);

    BlameOutput result = mock(BlameOutput.class);
    CommandExecutor commandExecutor = mock(CommandExecutor.class);

    when(commandExecutor.execute(any(Command.class), any(StreamConsumer.class), any(StreamConsumer.class), anyLong())).thenAnswer(new Answer<Integer>() {

      @Override
      public Integer answer(InvocationOnMock invocation) throws Throwable {
        StreamConsumer outConsumer = (StreamConsumer) invocation.getArguments()[1];
        outConsumer.consumeLine("1 Julien HENRY (1000) 2014-12-09 09:14 AM  Partager");
        outConsumer.consumeLine("foo");
        outConsumer.consumeLine("2 Julien HENRY (1000) 2014-12-09 09:14 AM  Partager ");
        outConsumer.consumeLine("3 Julien HENRY (1000) 2014-12-09 09:14 AM  Partager bar");
        return 0;
      }
    });

    when(input.filesToBlame()).thenReturn(Arrays.<InputFile>asList(inputFile));
    new JazzRtcBlameCommand(commandExecutor, new JazzRtcConfiguration(new Settings())).blame(input, result);
    verify(result).blameResult(inputFile,
      Arrays.asList(new BlameLine().date(DateUtils.parseDateTime("2014-12-09T09:14:00+0100")).revision("1000").author("Julien HENRY"),
        new BlameLine().date(DateUtils.parseDateTime("2014-12-09T09:14:00+0100")).revision("1000").author("Julien HENRY"),
        new BlameLine().date(DateUtils.parseDateTime("2014-12-09T09:14:00+0100")).revision("1000").author("Julien HENRY")));
  }

  @Test
  public void testAddMissingLastLine() throws IOException {
    File source = new File(baseDir, "src/foo.xoo");
    FileUtils.write(source, "sample content");
    DefaultInputFile inputFile = new DefaultInputFile("foo", "src/foo.xoo").setLines(4).setAbsolutePath(new File(baseDir, "src/foo.xoo").getAbsolutePath());
    fs.add(inputFile);

    BlameOutput result = mock(BlameOutput.class);
    CommandExecutor commandExecutor = mock(CommandExecutor.class);

    when(commandExecutor.execute(any(Command.class), any(StreamConsumer.class), any(StreamConsumer.class), anyLong())).thenAnswer(new Answer<Integer>() {

      @Override
      public Integer answer(InvocationOnMock invocation) throws Throwable {
        StreamConsumer outConsumer = (StreamConsumer) invocation.getArguments()[1];
        outConsumer.consumeLine("1 Julien HENRY (1000) 2014-12-09 09:14 AM  Partager foo");
        outConsumer.consumeLine("2 Julien HENRY (1000) 2014-12-09 09:14 AM  Partager ");
        outConsumer.consumeLine("3 Julien HENRY (1000) 2014-12-09 09:14 AM  Partager bar");
        // Jazz doesn't blame last empty line
        return 0;
      }
    });

    when(input.filesToBlame()).thenReturn(Arrays.<InputFile>asList(inputFile));
    new JazzRtcBlameCommand(commandExecutor, new JazzRtcConfiguration(new Settings())).blame(input, result);
    verify(result).blameResult(inputFile,
      Arrays.asList(new BlameLine().date(DateUtils.parseDateTime("2014-12-09T09:14:00+0100")).revision("1000").author("Julien HENRY"),
        new BlameLine().date(DateUtils.parseDateTime("2014-12-09T09:14:00+0100")).revision("1000").author("Julien HENRY"),
        new BlameLine().date(DateUtils.parseDateTime("2014-12-09T09:14:00+0100")).revision("1000").author("Julien HENRY"),
        new BlameLine().date(DateUtils.parseDateTime("2014-12-09T09:14:00+0100")).revision("1000").author("Julien HENRY")));
  }

}
