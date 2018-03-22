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

package org.opencron.server.job;

import net.sf.ehcache.store.chm.ConcurrentHashMap;
import org.opencron.common.Constants;
import org.opencron.common.logging.LoggerFactory;
import org.opencron.common.util.*;
import org.opencron.registry.URL;
import org.opencron.registry.api.RegistryService;
import org.opencron.registry.zookeeper.ChildListener;
import org.opencron.registry.zookeeper.ZookeeperClient;
import org.opencron.server.domain.Agent;
import org.opencron.server.domain.Job;
import org.opencron.server.service.*;
import org.opencron.server.support.OpencronTools;
import org.opencron.server.vo.JobInfo;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.opencron.common.util.CommonUtils.toLong;
import static org.opencron.common.util.StringUtils.*;
import static org.opencron.common.util.StringUtils.line;

/**
 * @author benjobs.
 */

@Component
public class OpencronRegistry {

    private static final Logger logger = LoggerFactory.getLogger(OpencronRegistry.class);

    @Autowired
    private JobService jobService;

    @Autowired
    private OpencronCollector opencronCollector;

    @Autowired
    private AgentService agentService;

    @Autowired
    private SchedulerService schedulerService;

    @Autowired
    private ExecuteService executeService;

    private final URL registryURL = URL.valueOf(PropertyPlaceholder.get(Constants.PARAM_OPENCRON_REGISTRY_KEY));;

    private final String registryPath = Constants.ZK_REGISTRY_SERVER_PATH + "/" + OpencronTools.SERVER_ID;

    private final RegistryService registryService = new RegistryService();

    private final ZookeeperClient zookeeperClient = registryService.getZKClient(registryURL);;

    private final Map<String, String> agents = new ConcurrentHashMap<String, String>(0);

    private final Map<Long, Long> jobs = new ConcurrentHashMap<Long, Long>(0);

    private List<String> servers = new ArrayList<String>(0);

    private Integer serverSize = 0;//server集群大小

    //在server销毁之前会将server从zookeeper中移除,这有可能会在此触发回调事件,而回调触发的时候server可能已经终止.
    private volatile boolean destroy = false;

    private Lock lock = new ReentrantLock();

    public void initialize() {
        //server第一次启动检查所有的agent是否可用
        List<Agent> agentList = this.agentService.getAll();
        if (CommonUtils.notEmpty(agentList)) {
            for (Agent agent : agentList) {
                boolean ping = executeService.ping(agent);
                if (!agent.getStatus().equals(ping)) {
                    agent.setStatus(ping);
                    agentService.merge(agent);
                }
            }
        }

        if (!Constants.OPENCRON_CLUSTER) return;

        //扫描agent自动注册到server
        List<String> children = this.zookeeperClient.getChildren(Constants.ZK_REGISTRY_AGENT_PATH);
        if (CommonUtils.notEmpty(children)) {
            for (String agent : children) {
                agents.put(agent, agent);
                if (logger.isInfoEnabled()) {
                    logger.info("[opencron] agent auto connected! info:{}", agent);
                }
                agentService.doConnect(agent);
            }
        }
    }

    public void destroy() {
        destroy = true;
        if (logger.isInfoEnabled()) {
            logger.info("[opencron] run destroy now...");
        }

        if (!Constants.OPENCRON_CLUSTER) return;

        //job unregister
        if (CommonUtils.notEmpty(jobs)) {
            for (Long job : jobs.keySet()) {
                this.registryService.unregister(registryURL, Constants.ZK_REGISTRY_JOB_PATH + "/" + job);
            }
        }

        //server unregister
        this.registryService.unregister(registryURL, registryPath);
    }

    public void registryAgent() {

        this.zookeeperClient.addChildListener(Constants.ZK_REGISTRY_AGENT_PATH, new ChildListener() {
            @Override
            public void childChanged(String path, List<String> children) {

                lock.lock();

                if (destroy) return;

                if (agents.isEmpty()) {
                    for (String agent : children) {
                        agents.put(agent, agent);
                        if (logger.isInfoEnabled()) {
                            logger.info("[opencron] agent connected! info:{}", agent);
                        }
                        agentService.doConnect(agent);
                    }
                } else {
                    Map<String, String> unAgents = new ConcurrentHashMap<String, String>(agents);
                    for (String agent : children) {
                        unAgents.remove(agent);
                        if (!agents.containsKey(agent)) {
                            //新增...
                            agents.put(agent, agent);
                            logger.info("[opencron] agent connected! info:{}", agent);
                            agentService.doConnect(agent);
                        }
                    }
                    if (CommonUtils.notEmpty(unAgents)) {
                        for (String child : unAgents.keySet()) {
                            agents.remove(child);
                            logger.info("[opencron] agent doDisconnect! info:{}", child);
                            agentService.doDisconnect(child);
                        }
                    }
                }
                lock.unlock();
            }
        });
    }

    public void registryServer() {

        if (!Constants.OPENCRON_CLUSTER) return;

        //server监控增加和删除
        this.zookeeperClient.addChildListener(Constants.ZK_REGISTRY_SERVER_PATH, new ChildListener() {
            @Override
            public void childChanged(String path, List<String> children) {

                try {

                    lock.lock();

                    if (destroy) return;

                    servers = children;

                    //一致性哈希计算出每个Job落在哪个server上
                    ConsistentHash<String> hash = new ConsistentHash<String>(servers);

                    List<Job> jobList = jobService.getScheduleJob();

                    for (Job job : jobList) {
                        Long jobId = job.getJobId();
                        //该任务落在当前的机器上
                        if (!jobs.containsKey(jobId) && OpencronTools.SERVER_ID.equals(hash.get(jobId))) {
                            jobDispatch(jobId);
                        } else {
                            jobRemove(jobId);
                        }
                    }

                    dispatchedInfo(children.size());

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    lock.unlock();
                }
            }
        });

        //将server加入到注册中心
        this.registryService.register(registryURL, registryPath, true);

        //register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                if (logger.isInfoEnabled()) {
                    logger.info("[opencron] run shutdown hook now...");
                }
                registryService.unregister(registryURL, registryPath);
            }
        }, "OpencronShutdownHook"));

    }


    public void registryJob() {

        if (!Constants.OPENCRON_CLUSTER) return;

        //job的监控
        this.zookeeperClient.addChildListener(Constants.ZK_REGISTRY_JOB_PATH, new ChildListener() {
            @Override
            public void childChanged(String path, List<String> children) {
                try {

                    lock.lock();

                    if (destroy) return;

                    Map<Long, Long> unJobs = new HashMap<Long, Long>(jobs);

                    ConsistentHash<String> hash = new ConsistentHash<String>(servers);

                    for (String job : children) {

                        Long jobId = toLong(job);
                        unJobs.remove(jobId);
                        if (!jobs.containsKey(jobId) && hash.get(jobId).equals(OpencronTools.SERVER_ID)) {
                            jobDispatch(jobId);
                        }

                    }

                    for (Long job : unJobs.keySet()) {
                        jobRemove(job);
                    }

                    dispatchedInfo(serverSize);

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    lock.unlock();
                }
            }
        });

    }

    //job新增的时候手动触发.....
    public void jobRegister(Long jobId) {
        if (Constants.OPENCRON_CLUSTER) {
            this.registryService.register(registryURL, Constants.ZK_REGISTRY_JOB_PATH + "/" + jobId, true);
        } else {
            this.jobDispatch(jobId);
        }
    }

    //job删除的时候手动触发.....
    public void jobUnRegister(Long jobId) {
        if (Constants.OPENCRON_CLUSTER) {
            this.registryService.unregister(registryURL, Constants.ZK_REGISTRY_JOB_PATH + "/" + jobId);
        } else {
            this.jobRemove(jobId);
        }
    }

    /**
     * 作业的分发一定要经过一致性哈希算法,计算是否落在该server上....
     *
     * @param jobId
     */
    private void jobDispatch(Long jobId) {
        try {

            this.lock.lock();
            if (Constants.OPENCRON_CLUSTER) {
                this.jobs.put(jobId, jobId);
                this.jobUnRegister(jobId);
                this.jobRegister(jobId);
            } else {
                this.jobRemove(jobId);
            }
            JobInfo jobInfo = this.jobService.getJobInfoById(jobId);
            Constants.CronType cronType = Constants.CronType.getByType(jobInfo.getCronType());
            switch (cronType) {
                case CRONTAB:
                    this.opencronCollector.add(jobInfo);
                    break;
                case QUARTZ:
                    this.schedulerService.put(jobInfo);
                    break;
            }
        } catch (Exception e) {
            new RuntimeException(e);
        } finally {
            this.lock.unlock();
        }
    }

    private void jobRemove(Long jobId) {
        try {
            this.lock.lock();
            if (Constants.OPENCRON_CLUSTER) {
                this.jobs.remove(jobId);
            }
            this.opencronCollector.remove(jobId);
            this.schedulerService.remove(jobId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            this.lock.unlock();
        }
    }

    private void dispatchedInfo(Integer serverSize) {
        String headerformat = line(1) + tab(1);
        String bodyformat = line(1) + tab(3);
        String endformat = line(2);

        String infoformat = headerformat + "███████████████ [opencron] serverChanged,print dispatched info ███████████████" +
                bodyformat + "datetime: \"{}\"" +
                bodyformat + "previous serverSize:{}" +
                bodyformat + "current serverSize:{}" +
                bodyformat + "jobs:[ {} ]" + endformat;

        logger.info(
                infoformat,
                DateUtils.formatFullDate(new Date()),
                this.serverSize,
                serverSize,
                StringUtils.join(this.jobs.keySet().toArray(new Long[0]), "|")
        );

        this.serverSize = serverSize;
    }

}
    
