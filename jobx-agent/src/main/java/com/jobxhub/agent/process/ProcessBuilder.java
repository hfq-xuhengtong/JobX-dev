/**
 * Copyright (c) 2015 The JobX Project
 * <p>
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.jobxhub.agent.process;

import com.google.common.base.Joiner;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper code for building a process
 */
public class ProcessBuilder {

  private final List<String> cmd = new ArrayList<>();
  private Map<String, String> env = new HashMap<>();
  private String workingDir = System.getProperty("user.dir");
  private Logger logger = Logger.getLogger(Process.class);
  private boolean isRunAsUser = false;
  private String runAsUserLibPath = null;
  private String runAsUser = null;

  private int stdErrSnippetSize = 30;
  private int stdOutSnippetSize = 30;

  public ProcessBuilder(final String... command) {
    addArg(command);
  }

  public ProcessBuilder addArg(final String... command) {
    for (final String c : command) {
      this.cmd.add(c);
    }
    return this;
  }

  public ProcessBuilder setWorkingDir(final String dir) {
    this.workingDir = dir;
    return this;
  }

  public String getWorkingDir() {
    return this.workingDir;
  }

  public ProcessBuilder setWorkingDir(final File f) {
    return setWorkingDir(f.getAbsolutePath());
  }

  public ProcessBuilder addEnv(final String variable, final String value) {
    this.env.put(variable, value);
    return this;
  }

  public Map<String, String> getEnv() {
    return this.env;
  }

  public ProcessBuilder setEnv(final Map<String, String> m) {
    this.env = m;
    return this;
  }

  public int getStdErrorSnippetSize() {
    return this.stdErrSnippetSize;
  }

  public ProcessBuilder setStdErrorSnippetSize(final int size) {
    this.stdErrSnippetSize = size;
    return this;
  }

  public int getStdOutSnippetSize() {
    return this.stdOutSnippetSize;
  }

  public ProcessBuilder setStdOutSnippetSize(final int size) {
    this.stdOutSnippetSize = size;
    return this;
  }

  public ProcessBuilder setLogger(final Logger logger) {
    this.logger = logger;
    return this;
  }

  public Process build() {
    if (this.isRunAsUser) {
      return new Process(this.cmd,this.workingDir, this.logger,
          this.runAsUser);
    } else {
      return new Process(this.cmd,this.workingDir, this.logger);
    }
  }

  public List<String> getCommand() {
    return this.cmd;
  }

  public String getCommandString() {
    return Joiner.on(" ").join(getCommand());
  }

  @Override
  public String toString() {
    return "ProcessBuilder(cmd = " + Joiner.on(" ").join(this.cmd) + ", env = "
        + this.env + ", cwd = " + this.workingDir + ")";
  }

  public ProcessBuilder enableExecuteAsUser() {
    this.isRunAsUser = true;
    return this;
  }

  public ProcessBuilder setExecuteAsUserBinaryPath(final String runAsUserLibPath) {
    this.runAsUserLibPath = runAsUserLibPath;
    return this;
  }

  public ProcessBuilder setEffectiveUser(final String runAsUser) {
    this.runAsUser = runAsUser;
    return this;
  }
}
