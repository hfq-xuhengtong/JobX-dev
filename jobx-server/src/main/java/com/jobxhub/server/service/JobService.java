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

import java.util.*;

import static com.jobxhub.common.Constants.*;

import com.google.common.collect.Lists;
import com.jobxhub.common.Constants;
import com.jobxhub.server.domain.JobBean;
import com.jobxhub.server.job.JobXRegistry;
import com.jobxhub.server.dao.JobDao;
import com.jobxhub.server.support.JobXTools;
import com.jobxhub.server.tag.PageBean;


import com.jobxhub.common.util.CommonUtils;
import com.jobxhub.server.dto.Job;
import com.jobxhub.server.dto.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

@Service
public class JobService {

    @Autowired
    private JobDao jobDao;

    @Autowired
    private RecordService recordService;

    @Autowired
    private JobXRegistry jobxRegistry;


    public int getCountByType(HttpSession session, JobType jobType) {
        Job job = new Job();
        if (jobType != null) {
            job.setJobType(jobType.getCode());
        }
        if (!JobXTools.isPermission(session)) {
            job.setUserId(JobXTools.getUserId(session));
        }
        Map<String, Object> map = new HashMap<String, Object>(0);
        map.put("job", job);
        return jobDao.getCount(map);
    }

    public List<JobBean> getAll() {
        List<JobBean> jobs = JobXTools.CACHE.get(Constants.PARAM_CACHED_JOB_KEY, List.class);
        if (CommonUtils.isEmpty(jobs)) {
            flushLocalJob();
        }
        return JobXTools.CACHE.get(Constants.PARAM_CACHED_JOB_KEY, List.class);
    }

    //本地缓存中的job列表
    private synchronized void flushLocalJob() {
        List<JobBean> jobBeans = jobDao.getAll();
        JobXTools.CACHE.put(Constants.PARAM_CACHED_JOB_KEY, Lists.transform(jobBeans, Job.transfer));
    }

    public void getPageBean(HttpSession session, PageBean pageBean, Job job) {
        if (job == null) {
            job = new Job();
        }
        if (!JobXTools.isPermission(session)) {
            User user = JobXTools.getUser(session);
            job.setUserId(user.getUserId());
        }
        pageBean.put("job", job);
        List<JobBean> jobs = jobDao.getByPageBean(pageBean);
        if (CommonUtils.notEmpty(jobs)) {
            int count = jobDao.getCount(pageBean.getFilter());
            pageBean.setTotalCount(count);
            pageBean.setResult(Lists.transform(jobs, Job.transfer));
        }
    }

    public void merge(Job job) {
        JobBean jobBean = JobBean.transfer.apply(job);
        if (job.getJobId() == null) {
            jobDao.save(jobBean);
            job.setJobId(jobBean.getJobId());
        } else {
            jobDao.update(jobBean);
        }
    }

    public Job getById(Long id) {
        JobBean job = jobDao.getById(id);
        return Job.transfer.apply(job);
    }

    public List<Job> getByAgent(Long agentId) {
        List<JobBean> jobs = jobDao.getByAgent(agentId);
        if (CommonUtils.notEmpty(jobs)) {
            return Lists.transform(jobs, Job.transfer);
        }
        return Collections.EMPTY_LIST;
    }

    public boolean existsName(Long jobId, Long agentId, String name) {
        int count = jobDao.existsCount(jobId, agentId, name);
        return count > 0;
    }

    /**
     * 1)检测当前任务是否正在运行中(单一任务,流程任务)
     * 2)如果当前任务是某个流程任务在子任务,则不能删除
     * @param id
     * @return
     */
    public boolean checkDelete(Long id) {
        Job job = getById(id);
        if (job == null) {
            return false;
        }
        //任务是否运行中(单一|流程)
        boolean runFlag = recordService.isRunning(id);
        if (runFlag) {
            return false;
        }

        //当前任务是否为某个工作流的子任务
        //todo...
        return true;
    }

    public void delete(Long jobId) throws Exception {
        Job job = getById(jobId);
        //单一任务...
        if (job.getJobType() == JobType.SIMPLE.getCode()) {

        }
        //todo ...
    }

    public void saveFlowJob(Job job, List<Job> children) throws Exception {
        //todo ...
    }

    public boolean checkJobOwner(HttpSession session, Long userId) {
        return JobXTools.isPermission(session) || userId.equals(JobXTools.getUserId(session));
    }

    public boolean pauseJob(Job jobParam) {
        if (jobParam.getPause() == null) return false;
        Job job = this.getById(jobParam.getJobId());
        if (jobParam.getPause().equals(job.getPause())) {
            return false;
        }
        //暂停任务
        if (jobParam.getPause()) {
            jobxRegistry.jobUnRegister(jobParam.getJobId());
        } else {
            //恢复任务
            jobxRegistry.jobRegister(jobParam.getJobId());
        }
        jobDao.pause(jobParam.getJobId(),jobParam.getPause());
        return true;
    }

    public List<Job> getFlowJob(Long id) {
        //todo ...
        return null;
    }

    public List<Job> getScheduleJob() {
        List<JobBean> jobs = jobDao.getScheduleJob();
        if (CommonUtils.notEmpty(jobs)) {
            return Lists.transform(jobs, Job.transfer);
        }
        return Collections.EMPTY_LIST;
    }

    public PageBean<Job> search(HttpSession session, PageBean pageBean, Long agentId, Integer cronType, String jobName) {
        Job job = new Job();
        if (!JobXTools.isPermission(session)) {
            job.setUserId(JobXTools.getUserId(session));
        }
        job.setAgentId(agentId);
        job.setCronType(cronType);
        job.setJobName(jobName);
        pageBean.put("job", job);

        List<JobBean> jobs = jobDao.getByPageBean(pageBean);
        int count = jobDao.getCount(pageBean.getFilter());

        pageBean.setResult(Lists.transform(jobs, Job.transfer));
        pageBean.setTotalCount(count);
        return pageBean;
    }

    public void updateToken(Long jobId, String token) {
        jobDao.updateToken(jobId,token);
    }
}
