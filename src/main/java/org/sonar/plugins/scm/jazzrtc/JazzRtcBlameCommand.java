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

import org.sonar.api.utils.System2;

import java.io.File;
import java.util.List;

import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.scm.BlameCommand;
import org.sonar.api.batch.scm.BlameLine;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonar.api.utils.command.StreamConsumer;
import org.sonar.api.utils.command.StringStreamConsumer;
import org.sonar.api.utils.command.TimeoutException;

public class JazzRtcBlameCommand extends BlameCommand {

  private static final Logger LOG = LoggerFactory.getLogger(JazzRtcBlameCommand.class);
  private static final int[] UNTRACKED_BLAME_RETURN_CODES = {1, 3, 30};
  private final CommandExecutor commandExecutor;
  private final JazzRtcConfiguration config;
  private final System2 system;

  public JazzRtcBlameCommand(JazzRtcConfiguration configuration) {
    this(CommandExecutor.create(), configuration);
  }

  JazzRtcBlameCommand(CommandExecutor commandExecutor, JazzRtcConfiguration configuration) {
    this(commandExecutor, configuration, System2.INSTANCE);
  }
  
  JazzRtcBlameCommand(CommandExecutor commandExecutor, JazzRtcConfiguration configuration, System2 system) {
    this.commandExecutor = commandExecutor;
    this.config = configuration;
    this.system = system;
  }

  @Override
  public void blame(BlameInput input, BlameOutput output) {
    FileSystem fs = input.fileSystem();
    LOG.debug("Working directory: " + fs.baseDir().getAbsolutePath());
    for (InputFile inputFile : input.filesToBlame()) {
      blame(fs, inputFile, output);
    }

  }

  private void blame(FileSystem fs, InputFile inputFile, BlameOutput output) {
    String filename = inputFile.relativePath();
    Command cl = createCommandLine(fs.baseDir(), filename);
    JazzRtcBlameConsumer consumer = new JazzRtcBlameConsumer(filename);
    StringStreamConsumer stderr = new StringStreamConsumer();

    int exitCode = execute(cl, consumer, stderr);
    if (ArrayUtils.contains(UNTRACKED_BLAME_RETURN_CODES, exitCode)) {
      LOG.debug("Skipping untracked file: {}. Annotate command exit code: {}", filename, exitCode);
      return;
    } else if (exitCode != 0) {
      throw new IllegalStateException("The jazz annotate command [" + cl.toString() + "] failed: " + stderr.getOutput());
    }

    List<BlameLine> lines = consumer.getLines();
    if (lines.size() == inputFile.lines() - 1) {
      // SONARPLUGINS-3097 JazzRTC does not report blame on last empty line
      lines.add(lines.get(lines.size() - 1));
    }
    output.blameResult(inputFile, lines);
  }

  public int execute(Command cl, StreamConsumer consumer, StreamConsumer stderr) {
    LOG.debug("Executing: " + cl);

    try {
      return commandExecutor.execute(cl, consumer, stderr, config.commandTimeout());
    } catch (TimeoutException t) {
      String errorMsg = "The jazz annotate command [" + cl.toString() + "] timed out";

      if (config.username() != null && config.password() != null) {
        throw new IllegalStateException(errorMsg, t);
      } else {
        throw new IllegalStateException(errorMsg + ". Please check if you are logged in or provide username and password", t);
      }
    }
  }

  private Command createCommandLine(File workingDirectory, String filename) {
    Command cl = Command.create("lscm");
    // SONARSCRTC-3 and SONARSCRTC-6
    if(system.isOsWindows()) {
      cl.setNewShell(true);
    }
    cl.setDirectory(workingDirectory);
    cl.addArgument("annotate");
    String username = config.username();
    if (username != null) {
      cl.addArgument("-u");
      cl.addArgument(username);
    }
    String password = config.password();
    if (password != null) {
      cl.addArgument("-P");
      cl.addMaskedArgument(password);
    }
    cl.addArgument(filename);
    return cl;
  }

}
