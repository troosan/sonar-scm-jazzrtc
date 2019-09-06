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
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.scm.BlameCommand.BlameInput;
import org.sonar.api.batch.scm.BlameCommand.BlameOutput;
import org.sonar.api.batch.scm.BlameLine;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonar.api.utils.command.StreamConsumer;
import org.sonar.api.utils.command.TimeoutException;

public class JazzRtcBlameCommandTest {
  @Mock
  private Configuration configuration;

  @Mock
  private BlameOutput result;

  @Mock
  private CommandExecutor commandExecutor;

  @Mock
  private BlameInput input;

  @Rule
  public UTCRule utcRule = new UTCRule();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private DefaultFileSystem fs;
  private File baseDir;

  @Before
  public void prepare() throws IOException {
    MockitoAnnotations.initMocks(this);

    baseDir = temp.newFolder();
    fs = new DefaultFileSystem(baseDir);
    when(input.fileSystem()).thenReturn(fs);
    when(configuration.get(JazzRtcConfiguration.USER_PROP_KEY)).thenReturn(Optional.ofNullable("test_user"));
    when(configuration.get(JazzRtcConfiguration.PASSWRD_PROP_KEY)).thenReturn(Optional.ofNullable("test_pwd"));
    when(configuration.getLong(JazzRtcConfiguration.CMD_TIMEOUT_PROP_KEY)).thenReturn(Optional.ofNullable(0L));
  }

  private DefaultInputFile createTestFile(String filePath, int numLines) throws IOException {
    File source = new File(baseDir, filePath);
    FileUtils.write(source, "sample content", Charset.defaultCharset());
    DefaultInputFile inputFile = new TestInputFileBuilder(baseDir.getAbsolutePath(), filePath).setLines(numLines).build();
    fs.add(inputFile);
    return inputFile;
  }

  @Test
  public void testParsingOfOutput() throws IOException {
    DefaultInputFile inputFile = createTestFile("src/foo.xoo", 3);

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
    new JazzRtcBlameCommand(commandExecutor, new JazzRtcConfiguration(configuration)).blame(input, result);
    verify(result).blameResult(inputFile,
      Arrays.asList(new BlameLine().date(DateUtils.parseDateTime("2014-12-09T09:14:00+0000")).revision("1000").author("Julien HENRY"),
        new BlameLine().date(DateUtils.parseDateTime("2014-12-09T09:14:00+0000")).revision("1000").author("Julien HENRY"),
        new BlameLine().date(DateUtils.parseDateTime("2014-12-09T09:14:00+0000")).revision("1000").author("Julien HENRY")));
  }

  /**
   * Assuming that CommandExecutor times out, tests if we break execution immediately with a IllegalStateException and that the timeout in the config is
   * passed to the executor.
   */
  @Test
  public void testCommandTimeout() throws IOException {
    long testTimeout = 100l;

    JazzRtcConfiguration mockedConfig = mock(JazzRtcConfiguration.class);
    when(mockedConfig.commandTimeout()).thenReturn(testTimeout);

    DefaultInputFile inputFile = createTestFile("src/foo.xoo", 3);

    when(commandExecutor.execute(any(Command.class), any(StreamConsumer.class), any(StreamConsumer.class), anyLong())).thenThrow(new TimeoutException(null, null, null));

    when(input.filesToBlame()).thenReturn(Arrays.<InputFile>asList(inputFile));
    try {
      new JazzRtcBlameCommand(commandExecutor, mockedConfig).blame(input, result);
      fail("expected exception");
    } catch (IllegalStateException e) {
      // expected
    }

    verify(commandExecutor).execute(any(Command.class), any(StreamConsumer.class), any(StreamConsumer.class), eq(testTimeout));
  }

  @Test
  // SONARSCRTC-3 and SONARSCRTC-6
  public void testNewShellOnWindows() throws IOException {
    System2 system = mock(System2.class);
    DefaultInputFile inputFile = createTestFile("src/foo.xoo", 3);

    when(system.isOsWindows()).thenReturn(true);
    when(input.filesToBlame()).thenReturn(Arrays.<InputFile>asList(inputFile));
    JazzRtcConfiguration mockedConfig = mock(JazzRtcConfiguration.class);
    new JazzRtcBlameCommand(commandExecutor, mockedConfig, system).blame(input, result);

    ArgumentCaptor<Command> argument = ArgumentCaptor.forClass(Command.class);
    verify(commandExecutor).execute(argument.capture(), any(StreamConsumer.class), any(StreamConsumer.class), anyLong());
    Command command = argument.getValue();

    assertThat(command.isNewShell()).isTrue();

  }

  @Test
  public void testParsingOfOutputWithWrappedCode() throws IOException {
    DefaultInputFile inputFile = createTestFile("src/foo.xoo", 3);

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
    new JazzRtcBlameCommand(commandExecutor, new JazzRtcConfiguration(configuration)).blame(input, result);
    verify(result).blameResult(inputFile,
      Arrays.asList(new BlameLine().date(DateUtils.parseDateTime("2014-12-09T09:14:00+0000")).revision("1000").author("Julien HENRY"),
        new BlameLine().date(DateUtils.parseDateTime("2014-12-09T09:14:00+0000")).revision("1000").author("Julien HENRY"),
        new BlameLine().date(DateUtils.parseDateTime("2014-12-09T09:14:00+0000")).revision("1000").author("Julien HENRY")));
  }

  @Test
  public void testUntrackedFile() throws IOException {
    DefaultInputFile inputFile = createTestFile("src/foo.xoo", 3);

    when(commandExecutor.execute(any(Command.class), any(StreamConsumer.class), any(StreamConsumer.class), anyLong())).thenAnswer(new Answer<Integer>() {

      @Override
      public Integer answer(InvocationOnMock invocation) throws Throwable {
        StreamConsumer outConsumer = (StreamConsumer) invocation.getArguments()[1];
        outConsumer.consumeLine("Problem running 'annotate':");
        outConsumer.consumeLine("Annotate failed.");
        outConsumer.consumeLine("failed to find the given file state starting with the given change set in the current configuration");
        outConsumer
          .consumeLine("Check the log for details about the error at \"/home/user/.jazz-scm\". If you have configured custom logging check your log configuration settings for the path to the log file.");
        return 3;
      }
    });

    when(input.filesToBlame()).thenReturn(Arrays.<InputFile>asList(inputFile));
    new JazzRtcBlameCommand(commandExecutor, new JazzRtcConfiguration(configuration)).blame(input, result);

    verifyZeroInteractions(result);
  }

  @Test
  public void testUntrackedFile2() throws IOException {
    DefaultInputFile inputFile = createTestFile("src/foo.xoo", 3);

    when(commandExecutor.execute(any(Command.class), any(StreamConsumer.class), any(StreamConsumer.class), anyLong())).thenAnswer(new Answer<Integer>() {

      @Override
      public Integer answer(InvocationOnMock invocation) throws Throwable {
        StreamConsumer outConsumer = (StreamConsumer) invocation.getArguments()[1];
        outConsumer.consumeLine("Problem running 'annotate':");
        outConsumer.consumeLine("No remote versionable found for \"/tmp/b/dummy-git/src/testfile\". Try 'lscm help annotate' for more information.");
        return 1;
      }
    });

    when(input.filesToBlame()).thenReturn(Arrays.<InputFile>asList(inputFile));
    new JazzRtcBlameCommand(commandExecutor, new JazzRtcConfiguration(configuration)).blame(input, result);

    verifyZeroInteractions(result);
  }

  /**
   * Differs from {@link testUntrackedFile} because it is an untracked file that is outside of the shared
   * directories.
   */
  @Test
  public void testUntrackedExternalFile() throws IOException {
    DefaultInputFile inputFile = createTestFile("src/foo.xoo", 3);

    when(commandExecutor.execute(any(Command.class), any(StreamConsumer.class), any(StreamConsumer.class), anyLong())).thenAnswer(new Answer<Integer>() {

      @Override
      public Integer answer(InvocationOnMock invocation) throws Throwable {
        StreamConsumer outConsumer = (StreamConsumer) invocation.getArguments()[1];
        outConsumer.consumeLine("Problem running 'annotate':");
        outConsumer.consumeLine("\"src/foo.xoo\" is not shared.");
        return 30;
      }
    });

    when(input.filesToBlame()).thenReturn(Arrays.<InputFile>asList(inputFile));
    new JazzRtcBlameCommand(commandExecutor, new JazzRtcConfiguration(configuration)).blame(input, result);

    verifyZeroInteractions(result);
  }

  /**
   * Tested with <pre>lscm annotate -u invalid -P invalid pom.xml</pre>
   */
  @Test(expected = IllegalStateException.class)
  public void testInvalidCredentials() throws IOException {
    DefaultInputFile inputFile = createTestFile("src/foo.xoo", 3);

    when(commandExecutor.execute(any(Command.class), any(StreamConsumer.class), any(StreamConsumer.class), anyLong())).thenAnswer(new Answer<Integer>() {

      @Override
      public Integer answer(InvocationOnMock invocation) throws Throwable {
        StreamConsumer outConsumer = (StreamConsumer) invocation.getArguments()[1];
        outConsumer.consumeLine("Problem running 'annotate':");
        outConsumer.consumeLine("Could not log in to https://localhost:9443/ccm/ as user invalid: CRJAZ0124E The user name or password is invalid.");
        outConsumer.consumeLine("CRJAZ0124E The user name or password is invalid.");
        outConsumer
          .consumeLine("Check the log for details about the error at \"/home/user/.jazz-scm\". If you have configured custom logging check your log configuration settings for the path to the log file.");
        return 2;
      }
    });

    when(input.filesToBlame()).thenReturn(Arrays.<InputFile>asList(inputFile));
    new JazzRtcBlameCommand(commandExecutor, new JazzRtcConfiguration(configuration)).blame(input, result);

    verifyZeroInteractions(result);
  }

  @Test
  public void testAddMissingLastLine() throws IOException {
    DefaultInputFile inputFile = createTestFile("src/foo.xoo", 4);

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
    new JazzRtcBlameCommand(commandExecutor, new JazzRtcConfiguration(configuration)).blame(input, result);
    verify(result).blameResult(inputFile,
      Arrays.asList(new BlameLine().date(DateUtils.parseDateTime("2014-12-09T09:14:00+0000")).revision("1000").author("Julien HENRY"),
        new BlameLine().date(DateUtils.parseDateTime("2014-12-09T09:14:00+0000")).revision("1000").author("Julien HENRY"),
        new BlameLine().date(DateUtils.parseDateTime("2014-12-09T09:14:00+0000")).revision("1000").author("Julien HENRY"),
        new BlameLine().date(DateUtils.parseDateTime("2014-12-09T09:14:00+0000")).revision("1000").author("Julien HENRY")));
  }

  /**
   * Tests that when the timeout is set to 0, it returns the default timeout
   */
  @Test
  public void testDefaultTimeout() throws IOException {
    when(configuration.getLong(JazzRtcConfiguration.CMD_TIMEOUT_PROP_KEY)).thenReturn(Optional.ofNullable(0L));
    JazzRtcConfiguration rtcConfiguration = new JazzRtcConfiguration(configuration);

    assertThat(rtcConfiguration.commandTimeout()).isEqualTo(60000L);
  }

  @Test
  public void testCustomTimeout() throws IOException {
    when(configuration.getLong(JazzRtcConfiguration.CMD_TIMEOUT_PROP_KEY)).thenReturn(Optional.ofNullable(1000L));
    JazzRtcConfiguration rtcConfiguration = new JazzRtcConfiguration(configuration);

    assertThat(rtcConfiguration.commandTimeout()).isEqualTo(1000L);
  }
}
