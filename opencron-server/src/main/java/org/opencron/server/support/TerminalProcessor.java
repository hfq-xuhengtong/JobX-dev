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
import org.opencron.common.ext.MethodMark;
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
import java.io.File;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分布式web终端
 * 分布式+反射+多线程.别问我怎么实现的,忘了....
 */
@Component
public class TerminalProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OpencronRegistry.class);

    @Autowired
    private TerminalService termService;

    @Autowired
    private RedisCacheManager redisCacheManager;

    private final String ZK_TERM_INSTANCE_PREFIX = "term_";

    private final String ZK_TERM_METHOD_PREFIX = "method_";

    private final String ZK_TERM_METHOD_DONE_PREFIX = "done_";

    private final URL registryURL = URL.valueOf(PropertyPlaceholder.get(Constants.PARAM_OPENCRON_REGISTRY_KEY));

    private final String registryPath = Constants.ZK_REGISTRY_TERM_PATH;

    private final RegistryService registryService = new RegistryService();

    private final ZookeeperClient zookeeperClient = registryService.getZKClient(registryURL);

    private Map<String, Method> methodMap = new ConcurrentHashMap<String, Method>(0);

    private Map<String, String> methodLock = new ConcurrentHashMap<String, String>(0);

    //key-->token  value--> serverId
    private Map<String, String> terminalMapping = new ConcurrentHashMap<String, String>();

    @PostConstruct
    public void initialize() throws Exception {

        logger.info("[opencron] Terminal init zookeeper....");

        Method[] methods = this.getClass().getDeclaredMethods();
        for (Method method : methods) {
            if ( ReflectUtils.methodHasAnnotation(method,MethodMark.class) ) {
                method.setAccessible(true);
                String methodName = DigestUtils.md5Hex(method.getName());
                methodMap.put(methodName, method);
            }
        }

        new Thread(new Runnable() {
            @Override
            public void run() {

                zookeeperClient.addChildListener(registryPath, new ChildListener() {
                    @Override
                    public synchronized void childChanged(String path, List<String> children) {

                        if (CommonUtils.notEmpty(children)) {

                            for (String child : children) {

                                String array[] = child.split("_");

                                if (child.startsWith(ZK_TERM_METHOD_DONE_PREFIX)) {

                                    if (methodLock.get(child) != null) {
                                        //唤醒doWork里的等待...
                                        methodLock.remove(child);
                                        registryService.unregister(registryURL, registryPath + "/" + child);
                                    }
                                } else if (child.startsWith(ZK_TERM_INSTANCE_PREFIX)) {
                                    if (array[1].equalsIgnoreCase(OpencronTools.SERVER_ID)) {
                                        terminalMapping.put(array[2], array[1]);
                                    }
                                } else {
                                    String token = array[1];
                                    String methodName = array[2];
                                    //该方法在该机器上
                                    if (terminalMapping.containsKey(token) && terminalMapping.containsValue(OpencronTools.SERVER_ID)) {
                                        logger.info("[opencron] Terminal method :{} in this server", methodMap.get(methodName).getName());
                                        //unregister
                                        registryService.unregister(registryURL, registryPath + "/" + child);
                                        //invoke...
                                        Object[] param = redisCacheManager.get(token.concat(methodName), Object[].class);
                                        param = CommonUtils.arrayInsertIndex(param, 0, methodName);
                                        if (CommonUtils.notEmpty(param)) {
                                            //反射获取目标方法执行.....
                                            try {
                                                methodMap.get(methodName).invoke(TerminalProcessor.this, param);//执行方法......
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            } finally {
                                                String pathx = registryPath + "/" + ZK_TERM_METHOD_DONE_PREFIX + token.concat("_").concat(methodName);
                                                registryService.register(registryURL, pathx, true);
                                            }
                                        }
                                    }
                                }

                            }
                        }
                    }
                });
            }
        }).start();
    }

    /**
     * 分配分布式任务
     *
     * @param methodName
     * @param param
     */
    public synchronized void doWork(String methodName, Status status,Object... param) throws Exception {
        String methodMD5 = DigestUtils.md5Hex(methodName);
        String token = (String) param[0];

        redisCacheManager.put(token.concat(methodMD5), param);

        logger.info("[opencron] Terminal registry to zookeeper");

        //method_token_method
        String data = ZK_TERM_METHOD_PREFIX + token.concat("_").concat(methodMD5);
        registryService.register(registryURL, registryPath + "/" + data, true);

        String lock = data.replace(ZK_TERM_METHOD_PREFIX, ZK_TERM_METHOD_DONE_PREFIX);
        this.methodLock.put(lock, lock);
        //等待处理结果...
        while (this.methodLock.containsKey(lock));

        Status result = redisCacheManager.remove(data, Status.class);
        status.setStatus(result == null ? false : result.isStatus());
    }

    @MethodMark
    public void sendAll(String method, String token, String cmd) throws Exception {
        cmd = URLDecoder.decode(cmd, Constants.CHARSET_UTF8);
        TerminalClient terminalClient = TerminalSession.get(token);
        if (terminalClient != null) {
            List<TerminalClient> terminalClients = TerminalSession.findClient(terminalClient.getHttpSessionId());
            for (TerminalClient client : terminalClients) {
                client.write(cmd);
            }
        }

        String data = ZK_TERM_METHOD_PREFIX + token.concat("_").concat(method);
        redisCacheManager.put(data, Status.TRUE);
    }

    @MethodMark
    public void theme(String method, String token, String theme) throws Exception {
        TerminalClient terminalClient = TerminalSession.get(token);
        if (terminalClient != null) {
            termService.theme(terminalClient.getTerminal(), theme);
        }
        String data = ZK_TERM_METHOD_PREFIX + token.concat("_").concat(method);
        redisCacheManager.put(data, Status.TRUE);
    }

    @MethodMark
    public void resize(String method, String token, Integer cols, Integer rows, Integer width, Integer height) throws Exception {
        TerminalClient terminalClient = TerminalSession.get(token);
        if (terminalClient != null) {
            terminalClient.resize(cols, rows, width, height);
        }
        String data = ZK_TERM_METHOD_PREFIX + token.concat("_").concat(method);
        redisCacheManager.put(data, Status.TRUE);
    }

    @MethodMark
    public void upload(String method, String token, File file, String name, long size) {
        TerminalClient terminalClient = TerminalSession.get(token);
        boolean success = true;
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            if (terminalClient != null) {
                terminalClient.upload(file.getAbsolutePath(), name, size);
            }
        } catch (Exception e) {
            e.printStackTrace();
            success = false;
        }
        String data = ZK_TERM_METHOD_PREFIX + token.concat("_").concat(method);
        redisCacheManager.put(data, Status.create(success));
    }

    //该实例绑定在该机器下 term@_server_termId
    public void registry(String termId) {
        this.registryService.register(registryURL, registryPath + "/" + ZK_TERM_INSTANCE_PREFIX + OpencronTools.SERVER_ID + "_" + termId, true);
    }

    public void unregistry(String termId) {
        this.registryService.unregister(registryURL, registryPath + "/" + ZK_TERM_INSTANCE_PREFIX + OpencronTools.SERVER_ID + "_" + termId);
    }




}
