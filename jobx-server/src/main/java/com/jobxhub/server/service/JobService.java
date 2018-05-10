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

import java.io.Serializable;
import java.util.*;

import static com.jobxhub.common.Constants.*;

import com.jobxhub.common.Constants;
import com.jobxhub.common.util.collection.ParamsMap;
import com.jobxhub.server.dao.QueryDao;
import com.jobxhub.server.domain.Job;
import com.jobxhub.server.domain.User;
import com.jobxhub.server.job.JobXRegistry;
import com.jobxhub.server.support.JobXTools;
import com.jobxhub.server.tag.PageBean;


import com.jobxhub.common.util.CommonUtils;
import com.jobxhub.server.vo.JobInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpSession;

import static com.jobxhub.common.util.CommonUtils.notEmpty;

@Service
@Transactional
public class JobService {

    @Autowired
    private QueryDao queryDao;

    @Autowired
    private AgentService agentService;

    @Autowired
    private UserService userService;

    @Autowired
    private JobXRegistry jobxRegistry;

    public Job getJob(Long jobId) {
        return queryDao.get(Job.class, jobId);
    }

    private void queryJobMore(List<JobInfo> jobs) {
        if (CommonUtils.notEmpty(jobs)) {
            for (JobInfo job : jobs) {
                job.setAgent(agentService.getAgent(job.getAgentId()));
                queryJobInfoChildren(job);
                queryJobUser(job);
            }
        }
    }

    public List<Job> getJobsByJobType(HttpSession session, JobType jobType) {
        String sql = "SELECT * FROM T_JOB WHERE jobType=?";
        if (JobType.FLOW.equals(jobType)) {
            sql += " AND flowNum=0";
        }
        if (!JobXTools.isPermission(session)) {
            User user = JobXTools.getUser(session);
            sql += " AND userId = " + user.getUserId() + " AND agentId IN (" + user.getAgentIds() + ")";
        }
        return queryDao.sqlQuery(Job.class, sql, jobType.getCode());
    }

    public List<Job> getAll() {
        List<Job> jobs = JobXTools.CACHE.get(Constants.PARAM_CACHED_JOB_KEY, List.class);
        if (CommonUtils.isEmpty(jobs)) {
            flushLocalJob();
        }
        return JobXTools.CACHE.get(Constants.PARAM_CACHED_JOB_KEY, List.class);
    }

    //本地缓存中的job列表
    private synchronized void flushLocalJob() {
        JobXTools.CACHE.put(Constants.PARAM_CACHED_JOB_KEY, queryDao.getAll(Job.class));
    }

    public PageBean<JobInfo> getJobInfoPage(HttpSession session, PageBean pageBean, JobInfo job) {
        String sql = "SELECT T.*,D.name AS agentName,D.port,D.host,D.password,U.userName AS operateUname " +
                "FROM T_JOB AS T " +
                "LEFT JOIN T_AGENT AS D " +
                "ON T.agentId = D.agentId " +
                "LEFT JOIN T_USER AS U " +
                "ON T.userId = U.userId " +
                "WHERE IFNULL(flowNum,0)=0 ";
        if (job != null) {
            if (notEmpty(job.getAgentId())) {
                sql += " AND T.agentId=" + job.getAgentId();
            }
            if (notEmpty(job.getCronType())) {
                sql += " AND T.cronType=" + job.getCronType();
            }
            if (notEmpty(job.getJobType())) {
                sql += " AND T.jobType=" + job.getJobType();
            }
            if (notEmpty(job.getRedo())) {
                sql += " AND T.redo=" + job.getRedo();
            }
            if (!JobXTools.isPermission(session)) {
                User user = JobXTools.getUser(session);
                sql += " AND T.userId = " + user.getUserId() + " AND T.agentId IN (" + user.getAgentIds() + ")";
            }
        }
        pageBean = queryDao.sqlPageQuery(pageBean, JobInfo.class, sql);
        List<JobInfo> parentJobs = pageBean.getResult();

        for (JobInfo parentJob : parentJobs) {
            queryJobInfoChildren(parentJob);
        }
        pageBean.setResult(parentJobs);
        return pageBean;
    }

    private List<JobInfo> queryJobInfoChildren(JobInfo job) {
        if (job.getJobType().equals(JobType.FLOW.getCode())) {
            String sql = "SELECT T.*,D.name AS agentName,D.port,D.host,D.password,U.userName AS operateUname FROM T_JOB AS T " +
                    "LEFT JOIN T_AGENT AS D " +
                    "ON T.agentId = D.agentId " +
                    "LEFT JOIN T_USER AS U " +
                    "ON T.userId = U.userId " +
                    "WHERE T.flowId = ? " +
                    "AND T.flowNum>0 " +
                    "ORDER BY T.flowNum ASC";
            List<JobInfo> childJobs = queryDao.sqlQuery(JobInfo.class, sql, job.getFlowId());
            if (CommonUtils.notEmpty(childJobs)) {
                for (JobInfo jobInfo : childJobs) {
                    jobInfo.setAgent(agentService.getAgent(jobInfo.getAgentId()));
                }
            }
            job.setChildren(childJobs);
            return childJobs;
        }
        return Collections.emptyList();
    }


    public Job merge(Job job) {
        job = (Job) queryDao.merge(job);
        flushLocalJob();
        return job;
    }

    public JobInfo getJobInfoById(Long id) {
        String sql = "SELECT T.*,D.name AS agentName,D.port,D.host,D.password,U.username AS operateUname " +
                " FROM T_JOB AS T " +
                "LEFT JOIN T_AGENT AS D " +
                "ON T.agentId = D.agentId " +
                "LEFT JOIN T_USER AS U " +
                "ON T.userId = U.userId " +
                "WHERE T.jobId =?";
        JobInfo job = queryDao.sqlUniqueQuery(JobInfo.class, sql, id);
        if (job == null) {
            return null;
        }
        queryJobMore(Arrays.asList(job));
        return job;
    }

    private void queryJobUser(JobInfo job) {
        if (job != null && job.getUserId() != null) {
            User user = userService.getUserById(job.getUserId());
            job.setUser(user);
        }
    }

    public List<JobInfo> getJobByAgentId(Long agentId) {
        String sql = "SELECT T.*,D.name AS agentName,D.port,D.host,D.password,U.userName AS operateUname FROM T_JOB AS T " +
                "LEFT JOIN T_USER AS U " +
                "ON T.userId = U.userId " +
                "LEFT JOIN T_AGENT D " +
                "ON T.agentId = D.agentId " +
                "WHERE T.agentId =?";
        return queryDao.sqlQuery(JobInfo.class, sql, agentId);
    }

    public boolean existsName(Long jobId, Long agentId, String name) {
        String sql = "SELECT COUNT(1) FROM T_JOB WHERE agentId=? AND jobName=? ";
        if (notEmpty(jobId)) {
            sql += " AND jobId != " + jobId + " AND flowId != " + jobId;
        }
        return (queryDao.sqlCount(sql, agentId, name)) > 0L;
    }

    public String checkDelete(Long id) {
        Job job = getJob(id);
        if (job == null) {
            return "error";
        }

        //该任务是否正在执行中
        String sql = "SELECT COUNT(1) FROM T_RECORD WHERE jobId = ? AND `status`=?";
        int count = queryDao.sqlCount(sql, id, RunStatus.RUNNING.getStatus());
        if (count > 0) {
            return "false";
        }

        //流程任务则检查任务流是否在运行中...
        if (job.getJobType() == JobType.FLOW.getCode()) {
            sql = "SELECT COUNT(1) FROM T_RECORD AS R INNER JOIN (" +
                    " SELECT J.jobId FROM T_JOB AS J INNER JOIN T_JOB AS F" +
                    " ON J.flowId = F.flowId" +
                    " WHERE f.jobId = ?" +
                    " ) AS J" +
                    " on R.jobId = J.jobId" +
                    " and R.status=?";
            count = queryDao.sqlCount(sql, id, RunStatus.RUNNING.getStatus());
            if (count > 0) {
                return "false";
            }
        }

        return "true";
    }


    @Transactional(rollbackFor = Exception.class)
    public void delete(Long jobId) throws Exception {
        Job job = getJob(jobId);
        if (job != null) {
            //单一任务,直接执行删除
            String sql = "delete Job where 1=1 ";
            if (job.getJobType().equals(JobType.SINGLETON.getCode())) {
                sql += " and jobId=" + jobId;
            }
            if (job.getJobType().equals(JobType.FLOW.getCode())) {
                //其中一个子流程任务,则删除单个
                sql += " and jobId=" + jobId;
            }
            queryDao.createQuery(sql).executeUpdate();
            jobxRegistry.jobUnRegister(jobId);
            flushLocalJob();
        }
    }

    public void saveFlowJob(Job job, List<Job> children) throws Exception {
        job.setUpdateTime(new Date());
        /**
         * 保存最顶层的父级任务
         */
        if (job.getJobId() != null) {
            merge(job);
            /**
             * 当前作业已有的子作业
             */
            JobInfo jobInfo = new JobInfo();
            jobInfo.setJobType(JobType.FLOW.getCode());

            /**
             * 取差集..
             */
            List<JobInfo> hasChildren = queryJobInfoChildren(jobInfo);
            //数据库里已经存在的子集合..
            top:
            for (JobInfo hasChild : hasChildren) {
                //当前页面提交过来的子集合...
                for (Job child : children) {
                    if (child.getJobId() != null && child.getJobId().equals(hasChild.getJobId())) {
                        continue top;
                    }
                }
                /**
                 * 已有的子作业被删除的,则做删除操作...
                 */
                delete(hasChild.getJobId());
            }
        } else {
            Job job1 = merge(job);
            merge(job1);
            job.setJobId(job1.getJobId());
        }

        for (int i = 0; i < children.size(); i++) {
            Job child = children.get(i);
            /**
             * 子作业的流程编号都为顶层父任务的jobId
             */
            child.setUserId(job.getUserId());
            child.setUpdateTime(new Date());
            child.setJobType(JobType.FLOW.getCode());
            child.setWarning(job.getWarning());
            child.setMobiles(job.getMobiles());
            child.setEmailAddress(job.getEmailAddress());
            merge(child);
        }
    }

    public boolean checkJobOwner(HttpSession session, Long userId) {
        return JobXTools.isPermission(session) || userId.equals(JobXTools.getUserId(session));
    }

    public boolean pauseJob(Job jobBean) {

        Job job = this.getJob(jobBean.getJobId());

        if (jobBean.getPause() == null) return false;

        if (job.getPause() != null && jobBean.getPause().equals(job.getPause())) {
            return false;
        }

        //暂停任务
        if (jobBean.getPause()) {
            jobxRegistry.jobUnRegister(jobBean.getJobId());
        }else {
            //恢复任务
            jobxRegistry.jobRegister(jobBean.getJobId());
        }

        job.setPause(jobBean.getPause());
        merge(job);
        return true;
    }

    public List<Job> getFlowJob(Long id) {
        return queryDao.hqlQuery("from Job where flowId=?", id);
    }

    public List<Job> getScheduleJob() {
        Integer[] cronTypes = new Integer[2];
        cronTypes[0] = CronType.CRONTAB.getType();
        cronTypes[1] = CronType.QUARTZ.getType();
        Map params = ParamsMap.map().set("cronType", cronTypes).set("pause", false);
        String hql = "from Job where cronType in (:cronType) and pause=:pause";
        return queryDao.hqlQuery(hql, params);
    }

    public PageBean<Job> search(PageBean pageBean,Long agentId, Integer cronType, String jobName) {
        String hql = "from Job where createType=:createType ";
        Map<String,Serializable> params = new HashMap<String, Serializable>(0);
        params.put("createType",CreateType.NORMAL.getValue());
        if (agentId!=null) {
            hql+=" and agentId=:agentId";
            params.put("agentId",agentId);
        }
        if (cronType!=null) {
            hql+=" and cronType=:cronType";
            params.put("cronType",cronType);
        }
        if (CommonUtils.notEmpty(jobName)) {
            hql+=" and jobName like :jobName ";
            params.put("jobName","%" + jobName + "%");
        }
        return queryDao.hqlPageQuery(hql,pageBean,params);
    }
}
