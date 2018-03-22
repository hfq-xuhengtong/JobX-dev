/**
 * Copyright (c) 2015 The Opencron Project
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

package org.opencron.server.support;

import org.opencron.common.Constants;
import org.opencron.server.domain.Terminal;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Component
public class TerminalContext implements Serializable {

    //key-->token value--->Terminal
    public static Map<String, Terminal> terminalContext = new ConcurrentHashMap<String, Terminal>(0);

    private String token;

    public Terminal get(String key) {
        if (Constants.OPENCRON_CLUSTER) {
            return OpencronTools.getRedisManager().get(key(key), Terminal.class);
        } else {
            return terminalContext.get(key(key));
        }
    }

    public void put(String key, Terminal terminal) {
        if (Constants.OPENCRON_CLUSTER) {
            OpencronTools.getRedisManager().put(Constants.PARAM_TERMINAL_TOKEN_KEY, key);
            OpencronTools.getRedisManager().put(key(key), terminal);
            /**
             * 为复制会话
             */
            String reKey = terminal.getId() + "_" + key;
            OpencronTools.getRedisManager().put(key(reKey), terminal);
        } else {
            this.token = key;
            //该终端实例只能被的打开一次,之后就失效
            terminalContext.put(key(key), terminal);
            /**
             * 为复制会话
             */
            String reKey = terminal.getId() + "_" + key;
            terminalContext.put(key(reKey), terminal);
        }
    }

    public Terminal remove(String key) {
        if (Constants.OPENCRON_CLUSTER) {
            Terminal terminal = get(key);
            OpencronTools.getRedisManager().evict(key(key));
            return terminal;
        } else {
            return terminalContext.remove(key(key));
        }
    }

    private String key(String key) {
        return Constants.PARAM_TERMINAL_PREFIX_KEY + key;
    }

    public String getToken() {
        if (Constants.OPENCRON_CLUSTER) {
            return OpencronTools.getRedisManager().remove(Constants.PARAM_TERMINAL_TOKEN_KEY, String.class);
        } else {
            return this.token;
        }
    }

}