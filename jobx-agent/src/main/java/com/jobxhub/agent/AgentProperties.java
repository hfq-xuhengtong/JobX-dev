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

package com.jobxhub.agent;

import com.jobxhub.common.Constants;
import com.jobxhub.common.logging.LoggerFactory;
import com.jobxhub.common.util.CommonUtils;
import com.jobxhub.common.util.IOUtils;
import com.jobxhub.common.util.MacUtils;
import com.jobxhub.common.util.StringUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Properties;


/**
 * Utility class to read the bootstrap JobX configuration.
 *
 * @author benjobs.
 */
public class AgentProperties {

    private static final Logger logger = LoggerFactory.getLogger(AgentProperties.class);

    private static Properties properties = null;

    /**
     * @param name The property name
     * @return specified property value
     */
    public static String getProperty(String name) {
        if (properties == null) {
            loadProperties();
        }
        return properties.getProperty(name);
    }

    /**
     * Load properties.
     */
    private static void loadProperties() {

        InputStream is = null;

        String fileName = "conf.properties";

        try {
            File home = new File(Constants.JOBX_HOME);
            File conf = new File(home, "conf");
            File propsFile = new File(conf, fileName);
            is = new FileInputStream(propsFile);
        } catch (Throwable t) {
            handleThrowable(t);
        }

        if (is != null) {
            try {
                properties = new Properties();
                properties.load(is);
            } catch (Throwable t) {
                handleThrowable(t);
                if (logger.isWarnEnabled()) {
                    logger.warn("[JobX] init properties error:{}", t.getMessage());
                }
            } finally {
                try {
                    is.close();
                } catch (IOException ioe) {
                    if (logger.isWarnEnabled()) {
                        logger.warn("[JobX]Could not close jobx properties file", ioe);
                    }
                }
            }
        }

        if (is == null) {
            // Do something
            if (logger.isWarnEnabled()) {
                logger.warn("[JobX]Failed to load jobx properties file");
            }
            // That's fine - we have reasonable defaults.
            properties = new Properties();
        }
    }


    // Copied from ExceptionUtils since that class is not visible during start
    private static void handleThrowable(Throwable t) {
        if (t instanceof ThreadDeath) {
            throw (ThreadDeath) t;
        }
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        }
        // All other instances of Throwable will be silently swallowed
    }

    /**
     * 从用户的home/.jobx下读取UID文件
     * @return
     */
    public static String getMacId() {
        String macId = null;
        if (Constants.JOBX_UID_FILE.exists()) {
            if (Constants.JOBX_UID_FILE.isDirectory()) {
                Constants.JOBX_UID_FILE.delete();
            } else {
                macId = IOUtils.readText(Constants.JOBX_UID_FILE, Constants.CHARSET_UTF8);
                if (CommonUtils.notEmpty(macId)) {
                    macId = StringUtils.clearLine(macId);
                    if (macId.length() != 32) {
                        Constants.JOBX_UID_FILE.delete();
                        macId = null;
                    }
                }
            }
        } else {
            Constants.JOBX_UID_FILE.getParentFile().mkdirs();
        }

        if (macId == null) {
            macId = MacUtils.getMachineId();
            IOUtils.writeText(Constants.JOBX_UID_FILE, macId, Constants.CHARSET_UTF8);
            Constants.JOBX_UID_FILE.setReadable(true,false);
            Constants.JOBX_UID_FILE.setWritable(false,false);
        }
        return macId;
    }


}
