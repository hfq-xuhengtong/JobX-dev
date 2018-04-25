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

import com.alibaba.fastjson.JSON;
import com.sun.tools.corba.se.idl.Compile;
import org.apache.commons.exec.*;
import org.hyperic.sigar.SigarException;
import com.jobxhub.common.Constants;
import com.jobxhub.common.api.AgentJob;
import com.jobxhub.common.ext.ExtensionLoader;
import com.jobxhub.common.job.*;
import com.jobxhub.common.logging.LoggerFactory;
import com.jobxhub.common.util.*;
import com.jobxhub.rpc.Client;
import com.jobxhub.rpc.ServerHandler;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;

import static com.jobxhub.common.util.CommonUtils.*;


public class AgentProcessor implements ServerHandler, AgentJob {

    private Logger logger = LoggerFactory.getLogger(AgentProcessor.class);

    private Client client = null;

    private final String REPLACE_REX = "%s:\\sline\\s[0-9]+:";

    private String EXITCODE_KEY = "exitCode";

    private String EXITCODE_SCRIPT = String.format("\n\necho %s:$?", EXITCODE_KEY);

    private AgentMonitor agentMonitor = new AgentMonitor();

    @Override
    public Response handle(Request request) {
        Action action = request.getAction();
        if (Action.PATH.equals(action)) {
            return path(request);
        }
        //verify password...
        if (!SystemPropertyUtils.get(Constants.PARAM_JOBX_PASSWORD_KEY).equalsIgnoreCase(request.getPassword())) {
            return Response.response(request)
                    .setSuccess(false)
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
            case GUID:
                return guid(request);
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
        String path = request.getParams().get(Constants.PARAM_LISTPATH_PATH_KEY);
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
        Constants.ConnType connType = Constants.ConnType.getByName(request.getParams().get("connType"));
        Response response = Response.response(request);
        switch (connType) {
            case PROXY:
                try {
                    Monitor monitor = agentMonitor.monitor();
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

        String command = request.getParams().get(Constants.PARAM_COMMAND_KEY);

        String pid = request.getParams().get(Constants.PARAM_PID_KEY);
        //以分钟为单位
        Long timeout = CommonUtils.toLong(request.getParams().get(Constants.PARAM_TIMEOUT_KEY), 0L);

        boolean timeoutFlag = timeout > 0;

        if (logger.isInfoEnabled()) {
            logger.info("[JOBX]:execute:{},pid:{}", command, pid);
        }


        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        final Response response = Response.response(request);

        final ExecuteWatchdog watchdog = new ExecuteWatchdog(Integer.MAX_VALUE);

        final Timer timer = new Timer();

        DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();

        Integer exitValue;

        String successExit = request.getParams().get(Constants.PARAM_SUCCESSEXIT_KEY);
        if (CommonUtils.isEmpty(successExit)) {
            exitValue = 0;//标准退住值:0
        } else {
            exitValue = Integer.parseInt(successExit);
        }

        File shellFile = CommandUtils.createShellFile(command, pid, EXITCODE_SCRIPT);

        try {
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
                    logger.info("[JOBX]:job has be killed!at pid :{}", request.getParams().get(Constants.PARAM_PID_KEY));
                }
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("[JOBX]:job execute error:{}", e.getCause().getMessage());
                }
            }
        } finally {
            exitValue = resultHandler.getExitValue();
            if (CommonUtils.notEmpty(outputStream.toByteArray())) {
                try {
                    outputStream.flush();
                    String text = outputStream.toString();
                    if (notEmpty(text)) {
                        try {
                            if (CommonUtils.isWindows()) {
                                response.setMessage(text);
                            } else {
                                text = text.replaceAll(String.format(REPLACE_REX, shellFile.getAbsolutePath()), "");
                                response.setMessage(text.substring(0, text.lastIndexOf(EXITCODE_KEY)));
                                exitValue = Integer.parseInt(text.substring(text.lastIndexOf(EXITCODE_KEY) + EXITCODE_KEY.length() + 1).trim());
                            }
                        } catch (IndexOutOfBoundsException e) {
                            response.setMessage(text);
                        }
                    }
                    outputStream.close();
                } catch (Exception e) {
                    if (logger.isErrorEnabled()) {
                        logger.error("[JOBX]:error:{}", e);
                    }
                }
            }

            if (Constants.StatusCode.TIME_OUT.getValue() == response.getExitCode()) {
                response.setSuccess(false).end();
            } else {
                if (CommonUtils.isEmpty(successExit)) {
                    response.setExitCode(exitValue).setSuccess(exitValue == Constants.StatusCode.SUCCESS_EXIT.getValue()).end();
                } else {
                    response.setExitCode(exitValue).setSuccess(successExit.equals(exitValue.toString())).end();
                }
            }

        }

        if (CommonUtils.notEmpty(shellFile)) {
            shellFile.delete();
        }

        if (logger.isInfoEnabled()) {
            logger.info("[JOBX]:execute result:{}", response.toString());
        }

        watchdog.stop();

        return response;
    }

    @Override
    public Response password(Request request) {
        String newPassword = request.getParams().get(Constants.PARAM_NEWPASSWORD_KEY);
        Response response = Response.response(request);
        if (isEmpty(newPassword)) {
            return response.setSuccess(false).setExitCode(Constants.StatusCode.SUCCESS_EXIT.getValue()).setMessage("密码不能为空").end();
        }
        SystemPropertyUtils.setProperty(Constants.PARAM_JOBX_PASSWORD_KEY, newPassword);
        IOUtils.writeText(Constants.JOBX_PASSWORD_FILE, newPassword, "UTF-8");
        return response.setSuccess(true).setExitCode(Constants.StatusCode.SUCCESS_EXIT.getValue()).end();
    }

    @Override
    public Response kill(Request request) {
        String pid = request.getParams().get(Constants.PARAM_PID_KEY);
        if (logger.isInfoEnabled()) {
            logger.info("[JOBX]:kill pid:{}", pid);
        }

        Response response = Response.response(request);
        String text = CommandUtils.executeShell(Constants.JOBX_KILL_SHELL, pid, EXITCODE_SCRIPT);
        String message = "";
        Integer exitVal = 0;

        if (notEmpty(text)) {
            try {
                message = text.substring(0, text.lastIndexOf(EXITCODE_KEY));
                exitVal = Integer.parseInt(text.substring(text.lastIndexOf(EXITCODE_KEY) + EXITCODE_KEY.length() + 1).trim());
            } catch (StringIndexOutOfBoundsException e) {
                message = text;
            }
        }

        response.setExitCode(Constants.StatusCode.ERROR_EXIT.getValue().equals(exitVal) ? Constants.StatusCode.ERROR_EXIT.getValue() : Constants.StatusCode.SUCCESS_EXIT.getValue())
                .setMessage(message)
                .end();

        if (logger.isInfoEnabled()) {
            logger.info("[JOBX]:kill result:{}" + response);
        }
        return response;
    }

    @Override
    public Response proxy(Request request) {

        if (this.client == null) {
            this.client = ExtensionLoader.load(Client.class);
        }

        String proxyHost = request.getParams().get(Constants.PARAM_PROXYHOST_KEY);
        String proxyPort = request.getParams().get(Constants.PARAM_PROXYPORT_KEY);
        String proxyAction = request.getParams().get(Constants.PARAM_PROXYACTION_KEY);
        String proxyPassword = request.getParams().get(Constants.PARAM_PROXYPASSWORD_KEY);

        //其他参数....
        String proxyParams = request.getParams().get(Constants.PARAM_PROXYPARAMS_KEY);
        Map<String, String> params = new HashMap<String, String>(0);
        if (CommonUtils.notEmpty(proxyParams)) {
            params = (Map<String, String>) JSON.parse(proxyParams);
        }

        Request proxyReq = Request.request(proxyHost, toInt(proxyPort), Action.findByName(proxyAction), proxyPassword, request.getTimeOut(), null).setParams(params);

        Response response = null;
        try {
            response = this.client.sentSync(proxyReq);
        } catch (Exception e) {
            e.printStackTrace();
            response = Response.response(request);
            response.setExitCode(Constants.StatusCode.ERROR_EXIT.getValue())
                    .setMessage("[JOBX]:proxy error:" + e.getLocalizedMessage())
                    .setSuccess(false)
                    .end();
        }
        return response;
    }

    @Override
    public Response guid(Request request) {
        String guid = MacUtils.getMachineId();
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

}