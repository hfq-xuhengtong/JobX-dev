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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;


@Component
public class TerminalContext implements Serializable {

    @Autowired
    private RedisCacheManager redisCacheManager;

    public Terminal get(String key) {
        return redisCacheManager.get(key(key),Terminal.class);
    }

    public void put(String key, Terminal terminal) {
        redisCacheManager.put(Constants.PARAM_TERMINAL_TOKEN_KEY,key);
        redisCacheManager.put(key(key),terminal);
        /**
         * 为复制会话
         */
        String reKey = terminal.getId()+"_"+key;
        redisCacheManager.put( key(reKey),terminal);
    }

    public Terminal remove(String key) {
        Terminal terminal = get(key);
        redisCacheManager.evict(key(key));
        return terminal;
    }

    private String key(String key){
        return Constants.PARAM_TERMINAL_PREFIX_KEY + key;
    }

    public String getToken() {
        return redisCacheManager.get(Constants.PARAM_TERMINAL_TOKEN_KEY,String.class);
    }
}