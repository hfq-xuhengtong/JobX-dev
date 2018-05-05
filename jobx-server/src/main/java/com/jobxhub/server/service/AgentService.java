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

import com.jobxhub.common.Constants;
import com.jobxhub.common.util.CommonUtils;
import com.jobxhub.server.dao.QueryDao;
import com.jobxhub.server.domain.User;
import com.jobxhub.server.job.JobXRegistry;
import com.jobxhub.server.support.JobXTools;
import com.jobxhub.server.tag.PageBean;
import org.apache.commons.codec.digest.DigestUtils;
import com.jobxhub.server.domain.Agent;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpSession;

import static com.jobxhub.common.util.CommonUtils.isEmpty;
import static com.jobxhub.common.util.CommonUtils.notEmpty;

@Service
@Transactional(readOnly = false)
public class AgentService {

    @Autowired
    private QueryDao queryDao;

    @Autowired
    private ExecuteService executeService;

    @Autowired
    private NoticeService noticeService;

    @Autowired
    private ConfigService configService;

    @Autowired
    private JobXRegistry jobxRegistry;

    public List<Agent> getAgentByConnType(Constants.ConnType connType) {
        return queryDao.hqlQuery("from Agent where status=? and proxy=?", Constants.ConnStatus.CONNECTED.getValue(), connType.getType());
    }

    public List<Agent> getAll() {
        List<Agent> agents = JobXTools.CACHE.get(Constants.PARAM_CACHED_AGENT_KEY, List.class);
        if (CommonUtils.isEmpty(agents)) {
            flushLocalAgent();
        }
        return JobXTools.CACHE.get(Constants.PARAM_CACHED_AGENT_KEY, List.class);
    }

    private synchronized void flushLocalAgent() {
        JobXTools.CACHE.put(
                Constants.PARAM_CACHED_AGENT_KEY,
                queryDao.getAll(Agent.class)
        );
    }

    public List<Agent> getOwnerAgentByConnStatus(HttpSession session, Constants.ConnStatus status) {
        String hql = "from Agent where status=?";
        if (!JobXTools.isPermission(session)) {
            User user = JobXTools.getUser(session);
            if (user.getAgentIds() != null) {
                hql += " and agentId in (".concat(user.getAgentIds()).concat(")");
            }
        }
        return queryDao.hqlQuery(hql, status.getValue());
    }

    public void getOwnerAgent(HttpSession session, PageBean pageBean) {
        String hql = "from Agent  ";
        if (!JobXTools.isPermission(session)) {
            User user = JobXTools.getUser(session);
            if (user.getAgentIds() != null) {
                hql += " where agentId in (".concat(user.getAgentIds()).concat(")");
            }
        }
        pageBean.verifyOrderBy("name", "name", "host", "port");
        hql += " order by " + pageBean.getOrderBy() + " " + pageBean.getOrder();
        queryDao.hqlPageQuery(hql, pageBean);
    }

    public Agent getAgent(Long id) {
        Agent agent = queryDao.get(Agent.class, id);
        if (agent != null) {
            agent.setUsers(getAgentUsers(agent));
        }
        return agent;
    }

    private List<User> getAgentUsers(Agent agent) {
        String agentId = agent.getAgentId().toString();

        String hql = "from User where agentIds like ?";

        //1
        List<User> users = queryDao.hqlQuery(hql, agentId);
        if (isEmpty(users)) {
            //1,
            users = queryDao.hqlQuery(hql, agentId + ",%");
        }
        if (isEmpty(users)) {
            //,1
            users = queryDao.hqlQuery(hql, ",%" + agentId);
        }

        if (isEmpty(users)) {
            //,1,
            users = queryDao.hqlQuery(hql, ",%" + agentId + ",%");
        }

        return isEmpty(users) ? Collections.<User>emptyList() : users;
    }

    @Transactional(readOnly = false)
    public Agent merge(Agent agent) {
        agent = (Agent) queryDao.merge(agent);
        flushLocalAgent();
        return agent;
    }

    public boolean existsName(Long id, String name) {
        String hql = "select count(1) from Agent where name=? ";
        if (notEmpty(id)) {
            hql += " and agentId !=" + id;
        }
        return queryDao.hqlCount(hql, name) > 0;
    }

    public boolean checkDelete(Long id) {
        Agent agent = getAgent(id);
        if (agent == null) {
            return false;
        }
        //检查该执行器是否定义的有任务
        String hql = "select count(1) from Job where agentId=? ";
        return queryDao.hqlCount(hql, id) > 0;
    }

    @Transactional(rollbackFor = Exception.class,readOnly = false)
    public void delete(Long id) {
        Agent agent = getAgent(id);
        queryDao.getSession().clear();
        queryDao.delete(Agent.class,id);
        jobxRegistry.agentUnRegister(agent);
        flushLocalAgent();
    }

    public boolean existsHost(Long id, String host) {
        String hql = "select count(1) from Agent where host=? ";
        if (notEmpty(id)) {
            hql += " and agentId != " + id;
        }
        return queryDao.hqlCount(hql, host) > 0;
    }

    @Transactional(readOnly = false)
    public String editPassword(Long id, Boolean type, String pwd0, String pwd1, String pwd2) {
        Agent agent = this.getAgent(id);
        boolean verify;
        if (type) {//直接输入的密钥
            agent.setPassword(pwd0);
            verify = executeService.ping(agent,false).equals(Constants.ConnStatus.CONNECTED);
        } else {//密码...
            verify = DigestUtils.md5Hex(pwd0).equals(agent.getPassword());
        }
        if (verify) {
            if (pwd1.equals(pwd2)) {
                pwd1 = DigestUtils.md5Hex(pwd1);
                Boolean flag = executeService.password(agent, pwd1);
                if (flag) {
                    agent.setPassword(pwd1);
                    agent.setStatus(Constants.ConnStatus.CONNECTED.getValue());
                    merge(agent);
                    return "true";
                } else {
                    return "false";
                }
            } else {
                return "two";
            }
        } else {
            return "one";
        }
    }

    public List<Agent> getOwnerAgents(HttpSession session) {
        String hql = "from Agent ";
        if (!JobXTools.isPermission(session)) {
            User user = JobXTools.getUser(session);
            if (user.getAgentIds()!=null) {
                hql += " where agentId in (" + user.getAgentIds() + ")";
            }
        }
        return queryDao.hqlQuery(hql);
    }

    public Agent getAgentByMachineId(String machineId) {
        String hql = "from Agent where machineId=?";
        //不能保证macId的唯一性,可能两台机器存在同样的macId,这种概率可以忽略不计,这里为了程序的健壮性...
        List<Agent> agents = queryDao.hqlQuery(hql, machineId);
        if (CommonUtils.notEmpty(agents)) {
            return agents.get(0);
        }
        return null;
    }

    public List<Agent> getAgentByIds(String agentIds) {
        return queryDao.hqlQuery(String.format("from Agent where agentId in (%s)", agentIds));
    }

    public void doDisconnect(Agent agent) {
        if (CommonUtils.isEmpty(agent.getNotifyTime()) || new Date().getTime() - agent.getNotifyTime().getTime() >= configService.getSysConfig().getSpaceTime() * 60 * 1000) {
            noticeService.notice(agent);
            //记录本次任务失败的时间
            agent.setNotifyTime(new Date());
        }
        agent.setStatus(Constants.ConnStatus.DISCONNECTED.getValue());
        merge(agent);
    }

    public void doDisconnect(String info) {
        if (CommonUtils.notEmpty(info)) {
            String macId = info.split("_")[0];
            String password =  info.split("_")[1];
            Agent agent = getAgentByMachineId(macId);
            if ( CommonUtils.notEmpty(agent,password) && password.equals(agent.getPassword()) ) {
                doDisconnect(agent);
            }
        }
    }

    /**
     * agent如果未设置host参数,则只往注册中心加入macId和password,server只能根据这个信息改过是否连接的状态
     * 如果设置了host,则会一并设置port,server端不但可以更新连接状态还可以实现agent自动注册(agent未注册的情况下)
     */
    public void doConnect(String agentInfo) {
        List<Agent> transfers = transfer(agentInfo);
        if (transfers == null) return;
        Agent registryAgent = transfers.get(0);
        Agent agent = transfers.get(1);
        //两个参数
        if (registryAgent.getHost() == null) {
            //密码一致
            if (agent != null && registryAgent.getPassword().equals(agent.getPassword())) {
                executeService.ping(agent,true);
            }
            return;
        }

        //4个参数
        if (agent != null) {
            //密码一致
            if (agent.getPassword().equals(registryAgent.getPassword())) {
                executeService.ping(agent,true);
            }
            return;
        }
        //新的机器，需要自动注册.
        registryAgent.setName(registryAgent.getHost());
        registryAgent.setComment("auto registered.");
        registryAgent.setWarning(false);
        registryAgent.setMobiles(null);
        registryAgent.setEmailAddress(null);
        registryAgent.setProxy(Constants.ConnType.CONN.getType());
        registryAgent.setProxyAgent(null);
        if (executeService.ping(registryAgent,false).equals(Constants.ConnStatus.CONNECTED)) {
            registryAgent.setStatus(Constants.ConnStatus.CONNECTED.getValue());
            merge(registryAgent);
        }
    }

    public List<Agent> transfer(String registryInfo) {
        if (CommonUtils.isEmpty(registryInfo)) return null;
        String[] array = registryInfo.split("_");
        if (array.length != 2 && array.length != 4) {
            return null;
        }
        String macId = array[0];
        String password = array[1];
        Agent agent = new Agent();
        if (array.length == 2) {
            agent.setMachineId(macId);
            agent.setPassword(password);
        } else {
            String host = array[2];
            String port = array[3];
            agent.setMachineId(macId);
            agent.setPassword(password);
            agent.setHost(host);
            agent.setPort(Integer.valueOf(port));
        }
        Agent agent1 = this.getAgentByMachineId(macId);
        return Arrays.asList(agent, agent1);
    }


}
