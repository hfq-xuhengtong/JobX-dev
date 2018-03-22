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
package org.opencron.server.session.store;

import org.opencron.server.session.HttpSessionStore;
import org.opencron.server.support.OpencronTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class JedisSessionStore implements HttpSessionStore {

    private static final Logger logger = LoggerFactory.getLogger(JedisSessionStore.class);

    @Override
    public void deleteSession(String sessionId) {
        OpencronTools.getRedisManager().evict(sessionId);
    }

    @Override
    public Map getSession(String sessionId) {
        Map result = (Map) get(sessionId);
        if (result == null) {
            result = new HashMap();
        }
        return result;
    }

    private Object get(String sessionId) {
        return OpencronTools.getRedisManager().get(sessionId, Map.class);
    }

    @Override
    public void setSession(String sessionId, Map sessionData) {
        OpencronTools.getRedisManager().put(sessionId, sessionData);
    }

}
