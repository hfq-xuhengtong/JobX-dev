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


import com.alibaba.fastjson.JSON;
import org.apache.zookeeper.data.Stat;
import org.opencron.common.Constants;
import org.opencron.common.logging.LoggerFactory;
import org.opencron.common.util.*;
import org.opencron.registry.URL;
import org.opencron.registry.api.RegistryService;
import org.opencron.registry.zookeeper.ChildListener;
import org.opencron.registry.zookeeper.ZookeeperClient;
import org.opencron.server.job.OpencronRegistry;
import org.opencron.server.service.TerminalService;
import org.opencron.server.vo.Status;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;
import java.lang.annotation.*;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分布式web终端
 */
@Component
public class TerminalProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OpencronRegistry.class);

    @Autowired
    private TerminalService termService;

    private final URL registryURL = URL.valueOf(PropertyPlaceholder.get(Constants.PARAM_OPENCRON_REGISTRY_KEY));

    private final String registryPath = Constants.ZK_REGISTRY_TERM_PATH;

    private final RegistryService registryService = new RegistryService();

    private final ZookeeperClient zookeeperClient = registryService.getZKClient(registryURL);

    private Map<String, Object[]> methodParams = new ConcurrentHashMap<String, Object[]>(0);

    private Map<String, Method> invokeMethods = new ConcurrentHashMap<String, Method>(0);

    private Map<String, String> methodLock = new ConcurrentHashMap<String, String>(0);

    private final String ZK_TERM_INSTANCE_PREFIX = "term_";

    private final String ZK_TERM_METHOD_PREFIX = "method_";

    //key-->serverid  value-->token
    private Map<String, String> terminals = new ConcurrentHashMap<String, String>();

    @PostConstruct
    public void initialize() throws Exception {

        logger.info("[opencron] Terminal init zookeeper....");
        this.zookeeperClient.addChildListener(registryPath, new ChildListener() {
            @Override
            public synchronized void childChanged(String path, List<String> children) {

                if (CommonUtils.notEmpty(children)) {

                    for (String child : children) {

                        String array[] = child.split("_");

                        if (child.startsWith(ZK_TERM_INSTANCE_PREFIX)) {
                            if (array[1].equalsIgnoreCase(OpencronTools.SERVER_ID)) {
                                terminals.put(array[1], array[2]);
                            }
                        } else {
                            String token = array[0];
                            //该方法在该机器上
                            if (terminals.containsValue(token)) {
                                logger.info("[opencron] Terminal serverId in this webServer");
                                //该实例
                                String methodName = array[1];

                                Object[] param = methodParams.remove(token.concat(methodName));
                                if (CommonUtils.notEmpty(param)) {
                                    logger.info("[opencron] Terminal instance in this webServer");
                                    //unregister
                                    registryService.unregister(registryURL, registryPath + "/" + child);
                                    //反射获取目标方法执行.....
                                    try {
                                        invokeMethods.get(methodName).invoke(param[0], (Object[]) param[1]);//执行方法......
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    } finally {
                                        //任务完成后移除任务锁
                                        methodLock.remove(child);
                                    }
                                }

                            }
                        }

                    }
                }
            }
        });

        Method[] methods = this.getClass().getDeclaredMethods();
        for (Method method : methods) {
            if (method.getDeclaredAnnotation(TerminalMethod.class) != null) {
                method.setAccessible(true);
                String methodName = DigestUtils.md5Hex(method.getName());
                this.invokeMethods.put(methodName, method);
            }
        }

    }


    /**
     * 分配分布式任务
     *
     * @param methodName
     * @param param
     */
    public synchronized void doWork(String methodName, Object... param) {
        String methodMD5 = DigestUtils.md5Hex(methodName);
        String token = (String) param[0];

        this.methodParams.put(token.concat(methodMD5), new Object[]{this, param});

        logger.info("[opencron] Terminal registry to zookeeper");

        //method_token_method
        String data = this.ZK_TERM_METHOD_PREFIX + token.concat("_").concat(methodMD5);
        this.registryService.register(registryURL, registryPath + "/" + data, true);

        //添加任务锁,
        this.methodLock.put(data, data);
        while (this.methodLock.containsKey(data)) {
            //
        }
    }

    @TerminalMethod
    public void sendAll(HttpServletResponse response, String token, String cmd) throws Exception {
        cmd = URLDecoder.decode(cmd, Constants.CHARSET_UTF8);
        TerminalClient terminalClient = TerminalSession.get(token);
        if (terminalClient != null) {
            List<TerminalClient> terminalClients = TerminalSession.findClient(terminalClient.getHttpSessionId());
            for (TerminalClient client : terminalClients) {
                client.write(cmd);
            }
        }
        WebUtils.writeJson(response, JSON.toJSONString(Status.TRUE));
    }

    @TerminalMethod
    public void theme(HttpServletResponse response, String token, String theme) throws Exception {
        TerminalClient terminalClient = TerminalSession.get(token);
        if (terminalClient != null) {
            termService.theme(terminalClient.getTerminal(), theme);
        }
        WebUtils.writeJson(response, JSON.toJSONString(Status.TRUE));
    }

    @TerminalMethod
    public void resize(HttpServletResponse response, String token, Integer cols, Integer rows, Integer width, Integer height) throws Exception {
        TerminalClient terminalClient = TerminalSession.get(token);
        if (terminalClient != null) {
            terminalClient.resize(cols, rows, width, height);
        }
        WebUtils.writeJson(response, JSON.toJSONString(Status.TRUE));
    }

    @TerminalMethod
    public void upload(HttpServletResponse response, String token,String path, String name,long size) throws Exception {
        TerminalClient terminalClient = TerminalSession.get(token);
        boolean success = true;
        if (terminalClient != null) {
            terminalClient.upload(path, name, size);
        }
        WebUtils.writeJson(response, JSON.toJSONString(new Status(success)));
    }

    //该实例绑定在该机器下 term@_server_termId
    public void registry(String termId) {
        this.registryService.register(registryURL, registryPath + "/" + ZK_TERM_INSTANCE_PREFIX + OpencronTools.SERVER_ID + "_" + termId, true);
    }

    public void unregistry(String termId) {
        this.registryService.unregister(registryURL, registryPath + "/" + ZK_TERM_INSTANCE_PREFIX + OpencronTools.SERVER_ID + "_" + termId);
    }


    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    public @interface TerminalMethod {
    }

}
