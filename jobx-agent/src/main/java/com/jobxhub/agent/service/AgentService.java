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
package com.jobxhub.agent.service;

import com.alibaba.fastjson.JSON;
import com.jobxhub.agent.util.PropertiesLoader;
import com.jobxhub.common.Constants;
import com.jobxhub.common.api.AgentJob;
import com.jobxhub.common.ext.ExtensionLoader;
import com.jobxhub.common.job.Action;
import com.jobxhub.common.job.Monitor;
import com.jobxhub.common.job.Request;
import com.jobxhub.common.job.Response;
import com.jobxhub.common.logging.LoggerFactory;
import com.jobxhub.common.util.*;
import com.jobxhub.common.util.collection.HashMap;
import com.jobxhub.registry.URL;
import com.jobxhub.registry.zookeeper.ZookeeperRegistry;
import com.jobxhub.registry.zookeeper.ZookeeperTransporter;
import com.jobxhub.rpc.Client;
import com.jobxhub.rpc.ServerHandler;
import org.apache.commons.exec.*;
import org.hyperic.sigar.SigarException;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;

import static com.jobxhub.common.util.CommonUtils.*;


public class AgentService implements ServerHandler, AgentJob {

    private static Logger logger = LoggerFactory.getLogger(AgentService.class);

    private Client client = null;

    private MonitorService monitorService = new MonitorService();

    private static ZookeeperRegistry registry = null;

    private boolean log2hbase;

    public AgentService() {
        String registryAddress = SystemPropertyUtils.get(Constants.PARAM_JOBX_REGISTRY_KEY);
        URL url = URL.valueOf(registryAddress);
        ZookeeperTransporter transporter =  ExtensionLoader.load(ZookeeperTransporter.class);
        registry = new ZookeeperRegistry(url,transporter);
        log2hbase = PropertiesLoader.getBoolean(Constants.PARAM_LOG2HBASE_KEY);
    }

    @Override
    public Response handle(Request request) {
        Action action = request.getAction();
        if (Action.PATH.equals(action)) {
            return path(request);
        }
        //verify password...
        if (!SystemPropertyUtils.get(Constants.PARAM_JOBX_PASSWORD_KEY).equalsIgnoreCase(request.getPassword())) {
            Map<String,String> result = new HashMap<String, String>(0);
            result.put(Action.PING.name(),"0");
            return Response.response(request)
                    .setSuccess(false)
                    .setResult(result)
                    .setExitCode(Constants.StatusCode.ERROR_PASSWORD.getValue())
                    .setMessage(Constants.StatusCode.ERROR_PASSWORD.getDescription())
                    .end();
        }

        switch (action) {
            case PING:
                return ping(request);
            case PATH:
                return path(request);
            case LISTPATH:
                return listPath(request);
            case EXECUTE:
                return execute(request);
            case PASSWORD:
                return password(request);
            case KILL:
                return kill(request);
            case MACID:
                return macId(request);
            case PROXY:
                return proxy(request);
            case MONITOR:
                return monitor(request);
            case RESTART:
                restart(request);
                break;

        }
        return null;
    }

    @Override
    public Response ping(Request request) {
        Map<String, String> platform = new HashMap<String, String>(0);
        //agent Platform...
        if (CommonUtils.isWindows()) {
            platform.put(Constants.PARAM_OS_KEY, Constants.Platform.Windows.toString());
        } else {
            platform.put(Constants.PARAM_OS_KEY, Constants.Platform.Unix.toString());
        }
        return Response.response(request).setResult(platform).setSuccess(true).setExitCode(Constants.StatusCode.SUCCESS_EXIT.getValue()).end();
    }

    @Override
    public Response path(Request request) {
        //返回密码文件的路径...
        return Response.response(request).setSuccess(true)
                .setExitCode(Constants.StatusCode.SUCCESS_EXIT.getValue())
                .setMessage(Constants.JOBX_HOME)
                .end();
    }

    @Override
    public Response listPath(Request request) {
        Response response = Response.response(request).setExitCode(Constants.StatusCode.SUCCESS_EXIT.getValue());
        String path = request.getParams().getString(Constants.PARAM_LISTPATH_PATH_KEY);
        if (CommonUtils.isEmpty(path)) return response.setSuccess(false).end();
        File file = new File(path);
        if (!file.exists()) {
            return response.setSuccess(false).end();
        }
        Map<String, String> result = new HashMap<String, String>(0);
        List<Map<String, String>> data = new ArrayList<Map<String, String>>(0);
        for (File itemFile : file.listFiles()) {
            if (itemFile.isHidden()) continue;
            Map<String, String> itemMap = new HashMap<String, String>(0);
            itemMap.put(Constants.PARAM_LISTPATH_NAME_KEY, itemFile.getName());
            itemMap.put(Constants.PARAM_LISTPATH_PATH_KEY, itemFile.getAbsolutePath());
            itemMap.put(Constants.PARAM_LISTPATH_ISDIRECTORY_KEY, itemFile.isDirectory() ? "0" : "1");
            data.add(itemMap);
        }
        result.put(Constants.PARAM_LISTPATH_PATH_KEY, JSON.toJSONString(data));
        response.setSuccess(true).setResult(result).end();
        return response;
    }

    @Override
    public Response monitor(Request request) {
        Constants.ConnType connType = Constants.ConnType.getByName(request.getParams().getString("connType"));
        Response response = Response.response(request);
        switch (connType) {
            case PROXY:
                try {
                    Monitor monitor = monitorService.monitor();
                    Map<String, String> map = monitor.toMap();
                    response.setResult(map)
                            .setSuccess(true)
                            .setExitCode(Constants.StatusCode.SUCCESS_EXIT.getValue())
                            .end();
                    return response;
                } catch (SigarException e) {
                    e.printStackTrace();
                }
            default:
                return null;
        }
    }

    @Override
    public Response execute(final Request request) {

        String command = request.getParams().getString(Constants.PARAM_COMMAND_KEY);

        String pid = request.getParams().getString(Constants.PARAM_PID_KEY);
        //以分钟为单位
        Integer timeout = request.getParams().getInt(Constants.PARAM_TIMEOUT_KEY);
        timeout = timeout == null?0:timeout;

        boolean timeoutFlag = timeout > 0;

        if (logger.isInfoEnabled()) {
            logger.info("[JobX]:execute:{},pid:{}", command, pid);
        }

        ByteArrayOutputStream outputStream= new ByteArrayOutputStream();

        final Response response = Response.response(request);

        final ExecuteWatchdog watchdog = new ExecuteWatchdog(Integer.MAX_VALUE);

        final Timer timer = new Timer();

        DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();

        Integer successExit = request.getParams().getInt(Constants.PARAM_SUCCESSEXIT_KEY);
        Integer exitValue = successExit == null?0:successExit;
        File shellFile = CommandUtils.createShellFile(command, pid);

        MessageService messageService = null;

        try {
            if (log2hbase) {
                new MessageService(outputStream, pid);
            }
            String runCmd = CommonUtils.isWindows()?"":"/bin/bash +x ";
            CommandLine commandLine = CommandLine.parse(runCmd + shellFile.getAbsoluteFile());
            final DefaultExecutor executor = new DefaultExecutor();
            ExecuteStreamHandler stream = new PumpStreamHandler(outputStream, outputStream);
            executor.setStreamHandler(stream);
            response.setStartTime(new Date().getTime());
            //成功执行完毕时退出值为0,shell标准的退出
            executor.setExitValue(exitValue);

            if (timeoutFlag) {
                //设置监控狗...
                executor.setWatchdog(watchdog);
                //监控超时的计时器
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        //超时,kill...
                        if (watchdog.isWatching()) {
                            /**
                             * 调用watchdog的destroyProcess无法真正kill进程...
                             * watchdog.destroyProcess();
                             */
                            timer.cancel();
                            watchdog.stop();
                            //call  kill...
                            request.setAction(Action.KILL);
                            try {
                                if (!CommonUtils.isWindows()) {
                                    kill(request);
                                }
                                response.setExitCode(Constants.StatusCode.TIME_OUT.getValue());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                        }
                    }
                }, timeout * 60 * 1000);

                //正常执行完毕则清除计时器
                resultHandler = new DefaultExecuteResultHandler() {
                    @Override
                    public void onProcessComplete(int exitValue) {
                        super.onProcessComplete(exitValue);
                        timer.cancel();
                    }

                    @Override
                    public void onProcessFailed(ExecuteException e) {
                        super.onProcessFailed(e);
                        timer.cancel();
                    }
                };
            }

            //开始写入日志文件
            if (log2hbase) {
                messageService.start();
            }
            executor.execute(commandLine, resultHandler);
            resultHandler.waitFor();
        } catch (Exception e) {
            if (e instanceof ExecuteException) {
                exitValue = ((ExecuteException) e).getExitValue();
            } else {
                exitValue = Constants.StatusCode.ERROR_EXEC.getValue();
            }
            if (Constants.StatusCode.KILL.getValue().equals(exitValue)) {
                if (timeoutFlag) {
                    timer.cancel();
                    watchdog.stop();
                }
                if (logger.isInfoEnabled()) {
                    logger.info("[JobX]:job has been killed! at pid :{}", request.getParams().get(Constants.PARAM_PID_KEY));
                }
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("[JobX]:job execute error:{}", e.getCause().getMessage());
                }
            }
        } finally {

            //记录结束时间
            response.setEndTime(new Date().getTime());
            //结束写入日志文件
            if (log2hbase) {
                messageService.stop();
            }
            exitValue = resultHandler.getExitValue();
            if (CommonUtils.notEmpty(outputStream.toByteArray())) {
                try {
                    outputStream.flush();
                    String text = outputStream.toString();
                    if (notEmpty(text)) {
                        response.setMessage(text);
                    }
                    outputStream.close();
                } catch (Exception e) {
                    if (logger.isErrorEnabled()) {
                        logger.error("[JobX]:error:{}", e);
                    }
                }
            }

            if (Constants.StatusCode.TIME_OUT.getValue() == response.getExitCode()) {
                response.setSuccess(false).end();
            } else {
                if (CommonUtils.isEmpty(successExit)) {
                    response.setExitCode(exitValue).setSuccess(exitValue == Constants.StatusCode.SUCCESS_EXIT.getValue()).end();
                } else {
                    response.setExitCode(exitValue).setSuccess(successExit.equals(exitValue)).end();
                }
            }

        }

        if (CommonUtils.notEmpty(shellFile)) {
            shellFile.delete();
        }

        if (logger.isInfoEnabled()) {
            logger.info("[JobX]:execute result:{}", response.toString());
        }

        watchdog.stop();

        return response;
    }

    @Override
    public Response password(Request request) {
        String newPassword = request.getParams().getString(Constants.PARAM_NEWPASSWORD_KEY);
        Response response = Response.response(request);
        if (isEmpty(newPassword)) {
            return response.setSuccess(false).setExitCode(Constants.StatusCode.SUCCESS_EXIT.getValue()).setMessage("密码不能为空").end();
        }

        //把老的注册删除
        unRegister(request.getHost(),request.getPort());

        SystemPropertyUtils.setProperty(Constants.PARAM_JOBX_PASSWORD_KEY, newPassword);
        IOUtils.writeText(Constants.JOBX_PASSWORD_FILE, newPassword, Constants.CHARSET_UTF8);

        //最新密码信息注册进来
        register(request.getHost(),request.getPort());

        return response.setSuccess(true).setExitCode(Constants.StatusCode.SUCCESS_EXIT.getValue()).end();
    }

    @Override
    public Response kill(Request request) {
        String pid = request.getParams().getString(Constants.PARAM_PID_KEY);
        if (logger.isInfoEnabled()) {
            logger.info("[JobX]:kill pid:{}", pid);
        }
        Response response = Response.response(request);
        String text = CommandUtils.executeShell(Constants.JOBX_KILL_SHELL, pid);
        response.setExitCode(Constants.StatusCode.SUCCESS_EXIT.getValue()).setMessage(text).end();
        if (logger.isInfoEnabled()) {
            logger.info("[JobX]:kill result:{}", text);
        }
        return response;
    }

    @Override
    public Response proxy(Request request) {
        if (this.client == null) {
            this.client = ExtensionLoader.load(Client.class);
        }
        String proxyHost = request.getParams().getString(Constants.PARAM_PROXYHOST_KEY);
        String proxyPort = request.getParams().getString(Constants.PARAM_PROXYPORT_KEY);
        String proxyAction = request.getParams().getString(Constants.PARAM_PROXYACTION_KEY);
        String proxyPassword = request.getParams().getString(Constants.PARAM_PROXYPASSWORD_KEY);
        //其他参数....
        String proxyParams = request.getParams().getString(Constants.PARAM_PROXYPARAMS_KEY);
        HashMap<String, Object> params = new HashMap<String, Object>(0);
        if (CommonUtils.notEmpty(proxyParams)) {
            params = (HashMap<String, Object>) JSON.parse(proxyParams);
        }

        Request proxyReq = Request.request(proxyHost, toInt(proxyPort), Action.findByName(proxyAction), proxyPassword, request.getTimeOut(), null).setParams(params);
        Response response;
        try {
            response = this.client.sentSync(proxyReq);
        } catch (Exception e) {
            e.printStackTrace();
            response = Response.response(request);
            response.setExitCode(Constants.StatusCode.ERROR_EXIT.getValue())
                    .setMessage("[JobX]:proxy error:" + e.getLocalizedMessage())
                    .setSuccess(false)
                    .end();
        }
        return response;
    }

    @Override
    public Response macId(Request request) {
        String guid = getMacId();
        Response response = Response.response(request).end();
        if (notEmpty(guid)) {
            return response.setMessage(guid).setSuccess(true).setExitCode(Constants.StatusCode.SUCCESS_EXIT.getValue());
        }
        return response.setSuccess(false).setExitCode(Constants.StatusCode.ERROR_EXIT.getValue());
    }

    /**
     * 重启前先检查密码,密码不正确返回Response,密码正确则直接执行重启
     *
     * @param request
     * @return
     * @throws InterruptedException
     */
    @Override
    public void restart(Request request) {

    }

    public static void register(final String host,final Integer port) {
        /**
         * agent如果未设置host参数,则只往注册中心加入macId和password,server只能根据这个信息改过是否连接的状态
         * 如果设置了host,则会一并设置port,server端不但可以更新连接状态还可以实现agent自动注册(agent未注册的情况下)
         */
        registry.register(getRegistryPath(host,port), true);
        if (logger.isInfoEnabled()) {
            logger.info("[JobX] agent register to zookeeper done");
        }
    }

    public static void unRegister(final String host,final Integer port) {
        registry.unRegister(getRegistryPath(host,port));
        if (logger.isInfoEnabled()) {
            logger.info("[JobX] agent unRegister to zookeeper done");
        }
    }

    public static void bindShutdownHook(final String host,final Integer port) {
        //register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                if (logger.isInfoEnabled()) {
                    logger.info("[JobX] run shutdown hook now...");
                }
                registry.unRegister(getRegistryPath(host,port));
            }
        }, "JobXShutdownHook"));
    }

    private static String getRegistryPath(String host,Integer port) {
        //mac_password
        String machineId = getMacId();
        if (machineId == null) {
            throw new IllegalArgumentException("[JobX] getUniqueId error.");
        }

        String password = SystemPropertyUtils.get(Constants.PARAM_JOBX_PASSWORD_KEY);

        String registryPath = String.format("%s/%s_%s", Constants.ZK_REGISTRY_AGENT_PATH, machineId,password);
        if (CommonUtils.isEmpty(host)) {
            if (logger.isWarnEnabled()) {
                logger.warn("[JobX] agent host not input,auto register can not be run，you can add this agent by yourself");
            }
        } else {
            //mac_password_host_port
            registryPath = String.format("%s/%s_%s_%s_%s",
                    Constants.ZK_REGISTRY_AGENT_PATH,
                    machineId,
                    password,
                    host,
                    port);
        }
        return registryPath;
    }

    /**
     * 从用户的home/.jobx下读取UID文件
     * @return
     */
    private static String getMacId() {
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