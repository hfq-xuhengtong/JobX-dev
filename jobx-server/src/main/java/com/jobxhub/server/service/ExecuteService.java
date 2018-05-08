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


package com.jobxhub.server.service;

import com.jobxhub.common.Constants;
import com.jobxhub.common.exception.PingException;
import com.jobxhub.common.job.Action;
import com.jobxhub.common.job.Request;
import com.jobxhub.common.job.RequestFile;
import com.jobxhub.common.job.Response;
import com.jobxhub.common.util.CommonUtils;
import com.jobxhub.common.util.HttpClientUtils;
import com.jobxhub.common.util.collection.ParamsMap;
import com.jobxhub.rpc.InvokeCallback;
import com.jobxhub.server.domain.Record;
import com.jobxhub.server.domain.Agent;
import com.jobxhub.server.domain.User;
import com.jobxhub.server.job.JobXCaller;
import com.jobxhub.server.vo.JobInfo;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

import static com.jobxhub.common.Constants.*;

/**
 * 这段调度核心代码得彻底重构,太臭了...实在有失水准...
 */
@Service
public class ExecuteService implements Job {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private RecordService recordService;

    @Autowired
    private JobService jobService;

    @Autowired
    private NoticeService noticeService;

    @Autowired
    private JobXCaller caller;

    @Autowired
    private AgentService agentService;

    @Autowired
    private UserService userService;

    private Map<Long, Integer> reExecuteThreadMap = new HashMap<Long, Integer>(0);

    private static final String PACKETTOOBIG_ERROR = "在向MySQL数据库插入数据量过多,需要设定max_allowed_packet";

    private ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(16, 16, 600L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(65536));

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        String key = jobExecutionContext.getJobDetail().getKey().getName();
        JobInfo jobInfo = (JobInfo) jobExecutionContext.getJobDetail().getJobDataMap().get(key);
        Agent agent = agentService.getAgent(jobInfo.getAgentId());
        jobInfo.setAgent(agent);
        try {
            ExecuteService executeService = (ExecuteService) jobExecutionContext.getJobDetail().getJobDataMap().get("jobBean");
            executeService.execute(jobInfo, ExecType.AUTO);
            this.printLog("[JobX] job:{} at {}:{}", jobInfo, null);
        } catch (Exception e) {
            if (logger.isErrorEnabled()) {
                logger.error(e.getLocalizedMessage(), e);
            }
        }
    }

    /**
     * 基本方式执行任务，按任务类型区分
     */
    public void execute(JobInfo job, Constants.ExecType execType) {
        JobType jobType = JobType.getJobType(job.getJobType());
        switch (jobType) {
            case SINGLETON:
                executeSingleJob(job, execType);//单一任务
                break;
            case FLOW:
                executeFlowJob(job, execType);//流程任务
                break;
            default:
                break;
        }
    }

    /**
     * 单一任务执行过程
     */
    private void executeSingleJob(final JobInfo job, final ExecType execType) {

        if (!checkJobPermission(job.getAgentId(), job.getUserId())) {
            return;
        }

        threadPoolExecutor.submit(new Runnable() {
            @Override
            public void run() {
                final Record record = new Record(job, execType);
                record.setStartTime(new Date());
                record.setJobType(JobType.SINGLETON.getCode());//单一任务

                InvokeCallback callback = new InvokeCallback() {

                    @Override
                    public void done(Response response) {

                        logger.info("[JobX]:execute response:{}", response.toString());

                        setRecordDone(record, response);

                        try {
                            //api方式调度,回调结果数据给调用方
                            if (execType.getStatus().equals(ExecType.API.getStatus()) && CommonUtils.notEmpty(job.getCallbackURL())) {
                                try {
                                    ParamsMap params = ParamsMap.map().put(
                                            "jobId", job.getJobId(),
                                            "startTime", response.getStartTime(),
                                            "endTime", response.getEndTime(),
                                            "success", response.isSuccess(),
                                            "message", response.getMessage()
                                    );
                                    HttpClientUtils.httpPostRequest(job.getCallbackURL(), params);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            //防止返回的信息太大,往数据库存，有保存失败的情况发送
                            recordService.merge(record);

                            if (!response.isSuccess()) {
                                noticeService.notice(job, null);
                                printLog("execute failed:jobName:{} at host:{},port:{},info:{}", job, record.getMessage());
                            } else {
                                printLog("execute successful:jobName:{} at host:{},port:{}", job, null);
                            }

                        } catch (Exception e) {
                            //信息丢失,继续保存记录
                            printLostJobInfo(job, record.getMessage());
                            record.setMessage(null);
                            recordService.merge(record);
                            //发送警告信息
                            noticeService.notice(job, PACKETTOOBIG_ERROR);
                            loggerError("execute failed:jobName:%s at host:%s,port:%d,info:%s", job, PACKETTOOBIG_ERROR, e);
                        }
                    }

                    @Override
                    public void caught(Throwable err) {
                        //方法失联
                        setRecordLost(record);
                        noticeService.notice(job, "调用失败,获取不到返回结果集");
                    }
                };

                try {
                    //执行前先保存
                    Record record1 = recordService.merge(record);
                    record.setRecordId(record1.getRecordId());
                    //执行前先检测一次通信是否正常
                    checkPing(job, record);
                    Request request = Request.request(
                            job.getAgent().getHost(),
                            job.getAgent().getPort(),
                            Action.EXECUTE,
                            job.getAgent().getPassword(),
                            job.getTimeout(),
                            job.getAgent().getProxyAgent())
                            .putParam(Constants.PARAM_COMMAND_KEY, job.getCommand())
                            .putParam(Constants.PARAM_PID_KEY, record.getPid())
                            .putParam(Constants.PARAM_TIMEOUT_KEY, job.getTimeout().toString())
                            .putParam(Constants.PARAM_SUCCESSEXIT_KEY, job.getSuccessExit());

                    caller.sentAsync(request,callback);

                } catch (Exception e) {
                    callback.caught(e);
                }
            }
        });

    }

    /**
     * 流程任务 按流程任务处理方式区分
     */
    private void executeFlowJob(JobInfo job, ExecType execType) {
        if (!checkJobPermission(job.getAgentId(), job.getUserId())) {
            return;
        }

        //分配一个流程组Id
        final long groupId = System.nanoTime() + Math.abs(new Random().nextInt());
        final Queue<JobInfo> jobQueue = new LinkedBlockingQueue<JobInfo>();
        jobQueue.add(job);
        jobQueue.addAll(job.getChildren());
        executeSequenceJob(groupId, jobQueue, execType);
    }

    /**
     * 串行任务处理方式
     */
    private void executeSequenceJob(long groupId, Queue<JobInfo> jobQueue, ExecType execType) {
        for (JobInfo jobInfo : jobQueue) {
            doFlowJob(jobInfo, groupId, execType);
        }
    }

    /**
     * 并行任务处理方式
     */
    private void executeSameTimeJob(final long groupId, final Queue<JobInfo> jobQueue, final ExecType execType) {
        final List<Boolean> result = new ArrayList<Boolean>(0);

        final Semaphore semaphore = new Semaphore(jobQueue.size());
        ExecutorService exec = Executors.newCachedThreadPool();

        for (final JobInfo jobInfo : jobQueue) {
            Runnable task = new Runnable() {
                @Override
                public void run() {
                    try {
                        semaphore.acquire();
                        result.add(doFlowJob(jobInfo, groupId, execType));
                        semaphore.release();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            exec.submit(task);
        }
        exec.shutdown();
        while (true) {
            if (exec.isTerminated()) {
                if (logger.isInfoEnabled()) {
                    logger.info("[JobX]SameTimeJob,All doned!");
                }
            }
        }
    }

    /**
     * 流程任务（通用）执行过程
     */
    private boolean doFlowJob(JobInfo job, long groupId, ExecType execType) {
        Record record = new Record(job, execType);
        //组Id
        record.setGroupId(groupId);
        //流程任务
        record.setJobType(JobType.FLOW.getCode());
        record.setFlowNum(job.getFlowNum());

        boolean success = true;

        try {
            //执行前先保存
            record = recordService.merge(record);
            //执行前先检测一次通信是否正常
            checkPing(job, record);

            Response result = responseToRecord(job, record);

            if (!result.isSuccess()) {
                recordService.merge(record);
                //被kill,直接退出
                if (StatusCode.KILL.getValue().equals(result.getExitCode())) {
                    recordService.flowJobDone(record);
                } else {
                    success = false;
                }
                return false;
            } else {
                //当前任务是流程任务的最后一个任务,则整个任务运行完毕
                if (job.getLastChild()) {
                    recordService.merge(record);
                    recordService.flowJobDone(record);
                } else {
                    //当前任务非流程任务最后一个子任务,全部流程任务为运行中...
                    record.setStatus(RunStatus.RUNNING.getStatus());
                    recordService.merge(record);
                }
                return true;
            }
        } catch (PingException e) {
            //通信失败,流程任务挂起.
            recordService.flowJobDone(record);
            return false;
        } catch (Exception e) {
            record.setMessage(this.loggerError("execute failed(flow job):jobName:%s at host:%s,port:%d,info:%s", job, e.getMessage(), e));
            //程序调用失败
            record.setSuccess(ResultStatus.FAILED.getStatus());
            record.setReturnCode(StatusCode.ERROR_EXEC.getValue());
            record.setEndTime(new Date());
            recordService.merge(record);
            success = false;
            return false;
        } finally {
            //流程任务的重跑靠自身维护...
            if (!success) {
                Record red = recordService.get(record.getRecordId());
                if (job.getRedo() == 1 && job.getRunCount() > 0) {
                    int index = 0;
                    boolean flag;
                    do {
                        flag = reExecuteJob(red, job, JobType.FLOW);
                        ++index;
                    } while (!flag && index < job.getRunCount());

                    //重跑到截止次数还是失败,则发送通知,记录最终运行结果
                    if (!flag) {
                        noticeService.notice(job, null);
                        recordService.flowJobDone(record);
                    }
                } else {
                    noticeService.notice(job, null);
                    recordService.flowJobDone(record);
                }
            }
        }

    }

    /**
     * 多执行器同时 现场执行过程
     */
    public void batchExecuteJob(final Long userId, String command, String agentIds) {
        String[] arrayIds = agentIds.split(";");
        final Semaphore semaphore = new Semaphore(arrayIds.length);
        ExecutorService exec = Executors.newCachedThreadPool();
        for (String agentId : arrayIds) {
            Agent agent = agentService.getAgent(Long.parseLong(agentId));
            final JobInfo jobInfo = new JobInfo(userId, command, agent);
            jobInfo.setSuccessExit("0");
            exec.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        semaphore.acquire();
                        executeSingleJob(jobInfo, ExecType.BATCH);
                        semaphore.release();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        exec.shutdown();
        while (true) {
            if (exec.isTerminated()) {
                if (logger.isInfoEnabled()) {
                    logger.info("[JobX]batchExecuteJob doned!");
                }
                break;
            }
        }
    }

    /**
     * 失败任务的重执行过程
     */
    public boolean reExecuteJob(final Record parentRecord, JobInfo job, JobType jobType) {

        if (parentRecord.getRedoCount().equals(reExecuteThreadMap.get(parentRecord.getRecordId()))) {
            return false;
        } else {
            reExecuteThreadMap.put(parentRecord.getRecordId(), parentRecord.getRedoCount());
        }

        parentRecord.setStatus(RunStatus.RERUNNING.getStatus());
        Record record = new Record(job, ExecType.RERUN);

        try {
            recordService.merge(parentRecord);
            /**
             * 当前重新执行的新纪录
             */
            record.setParentId(parentRecord.getRecordId());
            record.setGroupId(parentRecord.getGroupId());
            record.setJobType(jobType.getCode());
            //运行次数
            parentRecord.setRedoCount(parentRecord.getRedoCount() + 1);
            record.setRedoCount(parentRecord.getRedoCount());
            record = recordService.merge(record);

            //执行前先检测一次通信是否正常
            checkPing(job, record);

            Response result = responseToRecord(job, record);

            //当前重跑任务成功,则父记录执行完毕
            if (result.isSuccess()) {
                parentRecord.setStatus(RunStatus.RERUNDONE.getStatus());
                //重跑的某一个子任务被Kill,则整个重跑计划结束
            } else if (StatusCode.KILL.getValue().equals(result.getExitCode())) {
                parentRecord.setStatus(RunStatus.RERUNDONE.getStatus());
            } else {
                //已经重跑到最后一次了,还是失败了,则认为整个重跑任务失败,发送通知
                if (parentRecord.getRunCount().equals(parentRecord.getRedoCount())) {
                    noticeService.notice(job, null);
                }
                parentRecord.setStatus(RunStatus.RERUNUNDONE.getStatus());
            }
            this.printLog("execute successful:jobName:{} at host:{},port:{}", job, null);
        } catch (Exception e) {
            noticeService.notice(job, e.getMessage());
            errorExec(record, this.loggerError("execute failed:jobName:%s at host:%s,port:%d,info:%s", job, e.getMessage(), e));

        } finally {
            //如果已经到了任务重跑的截至次数直接更新为已重跑完成
            if (parentRecord.getRunCount().equals(parentRecord.getRedoCount())) {
                parentRecord.setStatus(RunStatus.RERUNDONE.getStatus());
            }
            try {
                recordService.merge(record);
                recordService.merge(parentRecord);
            } catch (Exception e) {
                record.setMessage(this.loggerError("execute failed(flow job):jobName:%s at host:%s,port:%d,info:%s", job, e.getMessage(), e));
            }

        }
        return record.getSuccess().equals(ResultStatus.SUCCESSFUL.getStatus());
    }

    /**
     * 终止任务过程
     */
    public boolean killJob(Record record) {

        final Queue<Record> recordQueue = new LinkedBlockingQueue<Record>();

        //单一任务
        if (JobType.SINGLETON.getCode().equals(record.getJobType())) {
            recordQueue.add(record);
        } else if (JobType.FLOW.getCode().equals(record.getJobType())) {
            //流程任务
            recordQueue.addAll(recordService.getRunningFlowJob(record.getRecordId()));
        }

        final List<Boolean> result = new ArrayList<Boolean>(0);

        final Semaphore semaphore = new Semaphore(recordQueue.size());
        ExecutorService exec = Executors.newCachedThreadPool();

        for (final Record cord : recordQueue) {
            exec.submit(new Runnable() {
                @Override
                public void run() {

                    JobInfo job = jobService.getJobInfoById(cord.getJobId());

                    final  Agent agent = agentService.getAgent(cord.getAgentId());

                    //现场执行的job
                    if (cord.getExecType() == ExecType.BATCH.getStatus()) {
                        job = new JobInfo(cord.getUserId(),cord.getCommand(),agent);
                    }

                    try {
                        semaphore.acquire();
                        //临时的改成停止中...
                        cord.setStatus(RunStatus.STOPPING.getStatus());
                        //被杀.
                        cord.setSuccess(ResultStatus.KILLED.getStatus());
                        recordService.merge(cord);
                        //向远程机器发送kill指令

                        final JobInfo finalJob = job;
                        caller.sentAsync(
                                Request.request(
                                        agent.getHost(),
                                        agent.getPort(),
                                        Action.KILL,
                                        agent.getPassword(),
                                        Constants.RPC_TIMEOUT,
                                        agent.getProxyAgent()
                                ).putParam(
                                        Constants.PARAM_PID_KEY,
                                        cord.getPid())
                                , new InvokeCallback() {
                                    @Override
                                    public void done(Response response) {
                                        cord.setStatus(RunStatus.STOPED.getStatus());
                                        cord.setEndTime(new Date());
                                        recordService.merge(cord);
                                        printLog("killed successful :jobName:{} at host:{},port:{},pid:{}", finalJob, cord.getPid());
                                    }

                                    @Override
                                    public void caught(Throwable err) {
                                        cord.setStatus(RunStatus.STOPED.getStatus());
                                        cord.setEndTime(new Date());
                                        recordService.merge(cord);
                                        printLog("killed successful :jobName:{} at host:{},port:{},pid:{}", finalJob, cord.getPid());
                                    }
                                });
                    } catch (Exception e) {
                        noticeService.notice(job, null);
                        loggerError("killed error:jobName:%s at host:%s,port:%d,pid:%s", job, cord.getPid() + " failed info: " + e.getMessage(), e);

                        logger.error("[JobX] job rumModel with SAMETIME error:{}", e.getMessage());

                        result.add(false);
                    }
                    semaphore.release();
                }
            });
        }
        exec.shutdown();
        while (true) {
            if (exec.isTerminated()) {
                logger.info("[JobX] SAMETIMEjob done!");
                return !result.contains(false);
            }
        }
    }

    /**
     * 向执行器发送请求，并封装响应结果
     */
    private Response responseToRecord(final JobInfo job, final Record record) throws Exception {
        Response response = caller.sentSync(Request.request(
                job.getAgent().getHost(),
                job.getAgent().getPort(),
                Action.EXECUTE,
                job.getAgent().getPassword(),
                job.getTimeout(),
                job.getAgent().getProxyAgent())
                .putParam(Constants.PARAM_COMMAND_KEY, job.getCommand())
                .putParam(Constants.PARAM_PID_KEY, record.getPid())
                .putParam(Constants.PARAM_TIMEOUT_KEY, job.getTimeout() + "")
                .putParam(Constants.PARAM_SUCCESSEXIT_KEY, job.getSuccessExit())
        );
        logger.info("[JobX]:execute response:{}", response.toString());
        setRecordDone(record, response);
        return response;
    }

    private void setRecordDone(Record record, Response response) {
        record.setEndTime(new Date());
        record.setReturnCode(response.getExitCode());
        record.setMessage(response.getMessage());
        record.setSuccess(response.isSuccess() ? ResultStatus.SUCCESSFUL.getStatus() : ResultStatus.FAILED.getStatus());

        if (StatusCode.KILL.getValue().equals(response.getExitCode())) {
            record.setStatus(RunStatus.STOPED.getStatus());
            //被kill任务失败
            record.setSuccess(ResultStatus.KILLED.getStatus());
        } else if (StatusCode.TIME_OUT.getValue().equals(response.getExitCode())) {
            record.setStatus(RunStatus.STOPED.getStatus());
            //超时...
            record.setSuccess(ResultStatus.TIMEOUT.getStatus());
        } else {
            record.setStatus(RunStatus.DONE.getStatus());
        }
    }

    public void setRecordLost(Record record) {
        record.setStatus(RunStatus.STOPED.getStatus());
        record.setSuccess(ResultStatus.LOST.getStatus());
        record.setEndTime(new Date());
        recordService.merge(record);
    }


    /**
     * 调用失败后的处理
     */
    private void errorExec(Record record, String errorInfo) {
        //程序调用失败
        record.setSuccess(ResultStatus.FAILED.getStatus());
        //已完成
        record.setStatus(RunStatus.DONE.getStatus());
        record.setReturnCode(StatusCode.ERROR_EXEC.getValue());
        record.setEndTime(new Date());
        record.setMessage(errorInfo);
        recordService.merge(record);
    }


    /**
     * 任务执行前 检测通信
     */
    private void checkPing(JobInfo job, Record record) throws PingException {
        ConnStatus connStatus = ping(job.getAgent(),true);
        if (!connStatus.equals(ConnStatus.CONNECTED)) {
            //已完成
            record.setStatus(RunStatus.DONE.getStatus());
            record.setReturnCode(StatusCode.ERROR_PING.getValue());

            String format = "can't to communicate with agent:%s(%s:%d),execute job:%s failed";
            String content = String.format(format, job.getAgentName(), job.getAgent().getHost(), job.getAgent().getPort(), job.getJobName());

            record.setMessage(content);
            record.setSuccess(ResultStatus.FAILED.getStatus());
            record.setEndTime(new Date());
            recordService.merge(record);
            throw new PingException(content);
        }
    }

    public Constants.ConnStatus ping(Agent agent,boolean update) {
        Response response = null;
        try {
            response = caller.sentSync(Request.request(
                    agent.getHost(),
                    agent.getPort(),
                    Action.PING,
                    agent.getPassword(),
                    Constants.RPC_TIMEOUT,
                    agent.getProxyAgent()));

        } catch (Exception e) {
            logger.error("[JobX]ping failed,host:{},port:{}", agent.getHost(), agent.getPort());
        }

        ConnStatus status = ConnStatus.DISCONNECTED;

        if (response != null) {
            if (response.isSuccess()) {
                status = ConnStatus.CONNECTED;
            }else {
                if (response.getResult().isEmpty()) {
                    status = ConnStatus.DISCONNECTED;
                }else {
                    status = ConnStatus.UNAUTHORIZED;
                }
            }
        }

        if (update) {
            agent.setStatus(status.getValue());
            agentService.merge(agent);
        }
        return status;
    }

    public String getMacId(Agent agent) {
        try {
            Response response = caller.sentSync(Request.request(
                    agent.getHost(),
                    agent.getPort(),
                    Action.MACID,
                    agent.getPassword(),
                    Constants.RPC_TIMEOUT,
                    agent.getProxyAgent())
            );
            return response.getMessage();
        } catch (Exception e) {
            logger.error("[JobX]getguid failed,host:{},port:{}", agent.getHost(), agent.getPort());
            return null;
        }
    }

    public String path(Agent agent) {
        try {
           return caller.sentSync(
                   Request.request(
                    agent.getHost(),
                    agent.getPort(),
                    Action.PATH,
                    null,
                    Constants.RPC_TIMEOUT,
                    agent.getProxyAgent())
           ).getMessage();
        } catch (Exception e) {
            logger.error("[JobX]ping failed,host:{},port:{}", agent.getHost(), agent.getPort());
            return null;
        }
    }

    public Response listPath(Agent agent, String path) {
        return caller.sentSync(Request.request(
                agent.getHost(),
                agent.getPort(),
                Action.LISTPATH,
                agent.getPassword(),
                Constants.RPC_TIMEOUT,
                agent.getProxyAgent()).putParam(Constants.PARAM_LISTPATH_PATH_KEY,path));
    }

    /**
     * 修改密码
     */
    public boolean password(Agent agent, final String newPassword) {
        boolean ping = false;
        try {
            Response response = caller.sentSync(Request.request(
                    agent.getHost(),
                    agent.getPort(),
                    Action.PASSWORD,
                    agent.getPassword(),
                    Constants.RPC_TIMEOUT,
                    agent.getProxyAgent()
                    ).putParam(
                    Constants.PARAM_NEWPASSWORD_KEY,
                    newPassword)
            );
            ping = response.isSuccess();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ping;
    }

    /**
     * 监测执行器运行状态
     */
    public Response monitor(Agent agent) throws Exception {
        return caller.sentSync(
                Request.request(
                        agent.getHost(),
                        agent.getPort(),
                        Action.MONITOR,
                        agent.getPassword(),
                        Constants.RPC_TIMEOUT,
                        agent.getProxyAgent()
                ).setParams(ParamsMap.map().set("connType", ConnType.getByType(agent.getProxy()).getName())));
    }


    public Response upload(Agent agent, RequestFile requestFile) {
        Request request = Request.request(
                agent.getHost(),
                agent.getPort(),
                Action.UPLOAD,
                agent.getPassword(),
                null,
                agent.getProxyAgent());

        request.setUploadFile(requestFile);
        Response response = caller.sentSync(request);
        if (!response.isSuccess()) {
            response.setSuccess(response.getExitCode() == StatusCode.SUCCESS_EXIT.getValue());
        }
        return response;
    }

    /**
     * 校验任务执行权限
     */
    private boolean checkJobPermission(Long jobAgentId, Long userId) {
        if (userId == null) {
            return false;
        }
        User user = userService.getUserById(userId);
        //超级管理员拥有所有执行器的权限
        if (user != null && user.getRoleId() == 999) {
            return true;
        }
        String agentIds = userService.getUserById(userId).getAgentIds();
        agentIds = "," + agentIds + ",";
        String thisAgentId = "," + jobAgentId + ",";
        return agentIds.contains(thisAgentId);
    }

    private void printLog(String str, JobInfo job, String message) {
        if (message != null) {
            if (logger.isInfoEnabled()) {
                logger.info(str, job.getJobName(), job.getAgent().getHost(), job.getAgent().getPort(), message);
            }
        } else {
            if (logger.isInfoEnabled()) {
                logger.info(str, job.getJobName(), job.getAgent().getHost(), job.getAgent().getPort());
            }
        }
    }

    private String loggerError(String str, JobInfo job, String message, Exception e) {
        String errorInfo = String.format(str, job.getJobName(), job.getAgent().getHost(), job.getAgent().getPort(), message);
        if (logger.isErrorEnabled()) {
            logger.error(errorInfo, e);
        }
        return errorInfo;
    }

    private void printLostJobInfo(JobInfo jobInfo, String message) {

    }



}
