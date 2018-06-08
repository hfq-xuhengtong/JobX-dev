/*
 * Copyright 2017 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.jobxhub.agent.process;

import com.jobxhub.agent.util.LogGobbler;
import com.jobxhub.common.Constants;
import com.jobxhub.common.util.CommonUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * An improved version of java.lang.Process.
 *
 * Output is read by separate threads to avoid deadlock and logged to log4j loggers.
 */
public class Process {

  public static String KILL_COMMAND = "kill";

  private final String workingDir;
  private final List<String> cmd;
  private final Logger logger;
  private final CountDownLatch startupLatch;
  private final CountDownLatch completeLatch;

  private volatile int processId;
  private volatile java.lang.Process process;

  private String runAsUser = null;

  public Process(List<String> cmd,final String workingDir,final Logger logger) {
    this.cmd = cmd;
    this.workingDir = workingDir;
    this.processId = -1;
    this.startupLatch = new CountDownLatch(1);
    this.completeLatch = new CountDownLatch(1);
    this.logger = logger;
  }

  public Process(final List<String> cmd,
                 final String workingDir,
                 final Logger logger,
                 final String runAsUser) {
    this(cmd,workingDir, logger);
    this.runAsUser = runAsUser;
  }

  /**
   * Execute this process, blocking until it has completed.
   */
  public void run() throws IOException {
    if (this.isStarted() || this.isComplete()) {
      throw new IllegalStateException("[JobX]The process can only be used once.");
    }

    final java.lang.ProcessBuilder builder = new java.lang.ProcessBuilder(this.cmd);
    builder.directory(new File(this.workingDir));
    builder.redirectErrorStream(true);
    this.process = builder.start();
    try {
      this.processId = processId(this.process);
      if (this.processId == 0) {
        this.logger.debug("[JobX]Spawned thread with unknown process id");
      } else {
        this.logger.debug("[JobX]Spawned thread with process id " + this.processId);
      }

      this.startupLatch.countDown();
      final LogGobbler outputGobbler =
          new LogGobbler(
              new InputStreamReader(this.process.getInputStream(), StandardCharsets.UTF_8),
              this.logger, Level.INFO, 30);
      final LogGobbler errorGobbler =
          new LogGobbler(
              new InputStreamReader(this.process.getErrorStream(), StandardCharsets.UTF_8),
              this.logger, Level.ERROR, 30);

      outputGobbler.start();
      errorGobbler.start();
      int exitCode = -1;
      try {
        exitCode = this.process.waitFor();
      } catch (final InterruptedException e) {
        this.logger.info("[JobX]Process interrupted. Exit code is " + exitCode, e);
      }

      this.completeLatch.countDown();

      // try to wait for everything to get logged out before exiting
      outputGobbler.awaitCompletion(5000);
      errorGobbler.awaitCompletion(5000);

      if (exitCode != 0) {
        final String output =
            new StringBuilder().append("Stdout:\n")
                .append(outputGobbler.getRecentLog()).append("\n\n")
                .append("Stderr:\n").append(errorGobbler.getRecentLog())
                .append("\n").toString();
        throw new ProcessException(exitCode, output);
      }

    } finally {
      IOUtils.closeQuietly(this.process.getInputStream());
      IOUtils.closeQuietly(this.process.getOutputStream());
      IOUtils.closeQuietly(this.process.getErrorStream());
    }
  }

  /**
   * Await the completion of this process
   *
   * @throws InterruptedException if the thread is interrupted while waiting.
   */
  public void awaitCompletion() throws InterruptedException {
    this.completeLatch.await();
  }

  /**
   * Await the start of this process
   *
   * When this method returns, the job process has been created and a this.processId has been set.
   *
   * @throws InterruptedException if the thread is interrupted while waiting.
   */
  public void awaitStartup() throws InterruptedException {
    this.startupLatch.await();
  }

  /**
   * Get the process id for this process, if it has started.
   *
   * @return The process id or -1 if it cannot be fetched
   */
  public int getProcessId() {
    checkStarted();
    return this.processId;
  }

  /**
   * Attempt to kill the process, waiting up to the given time for it to die
   *
   * @param time The amount of time to wait
   * @param unit The time unit
   * @return true iff this soft kill kills the process in the given wait time.
   */
  public boolean softKill(final long time, final TimeUnit unit)
      throws InterruptedException {
    checkStarted();
    if (this.processId != 0 && isStarted()) {
      try {
        if (isRunAsUser()) {
          final String cmd =
              String.format("%s %s %s %d",Constants.JOBX_EXECUTE_AS_USER_LIB.getAbsoluteFile(),
                  this.runAsUser, KILL_COMMAND, this.processId);
          Runtime.getRuntime().exec(cmd);
        } else {
          final String cmd = String.format("%s %d", KILL_COMMAND, this.processId);
          Runtime.getRuntime().exec(cmd);
        }
        return this.completeLatch.await(time, unit);
      } catch (final IOException e) {
        this.logger.error("Kill attempt failed.", e);
      }
      return false;
    }
    return false;
  }

  /**
   * Force kill this process
   */
  public void hardKill() {
    checkStarted();
    if (isRunning()) {
      if (this.processId != 0) {
        try {
          if (isRunAsUser()) {
            final String cmd =
                String.format("%s %s %s -9 %d", Constants.JOBX_EXECUTE_AS_USER_LIB.getAbsoluteFile(),
                    this.runAsUser, KILL_COMMAND, this.processId);
            Runtime.getRuntime().exec(cmd);
          } else {
            final String cmd = String.format("%s -9 %d", KILL_COMMAND, this.processId);
            Runtime.getRuntime().exec(cmd);
          }
        } catch (final IOException e) {
          this.logger.error("Kill attempt failed.", e);
        }
      }
      this.process.destroy();
    }
  }

  /**
   * Attempt to get the process id for this process
   *
   * @param process The process to get the id from
   * @return The id of the process
   */
  private int processId(final java.lang.Process process) {
    int processId = 0;
    try {
      final Field f = process.getClass().getDeclaredField("pid");
      f.setAccessible(true);

      processId = f.getInt(process);
    } catch (final Throwable e) {
      e.printStackTrace();
    }

    return processId;
  }

  /**
   * @return true iff the process has been started
   */
  public boolean isStarted() {
    return this.startupLatch.getCount() == 0L;
  }

  /**
   * @return true iff the process has completed
   */
  public boolean isComplete() {
    return this.completeLatch.getCount() == 0L;
  }

  /**
   * @return true iff the process is currently running
   */
  public boolean isRunning() {
    return isStarted() && !isComplete();
  }

  public void checkStarted() {
    if (!isStarted()) {
      throw new IllegalStateException("Process has not yet started.");
    }
  }

  public boolean isRunAsUser() {
    return CommonUtils.isEmpty(runAsUser);
  }

  public String getRunAsUser() {
    return this.runAsUser;
  }
}
