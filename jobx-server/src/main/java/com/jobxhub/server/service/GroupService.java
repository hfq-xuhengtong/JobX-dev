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

import com.google.common.collect.Lists;
import com.jobxhub.common.util.CommonUtils;
import com.jobxhub.common.util.collection.HashMap;
import com.jobxhub.server.domain.AgentGroupBean;
import com.jobxhub.server.domain.GroupBean;
import com.jobxhub.server.dao.GroupDao;
import com.jobxhub.server.dto.Agent;
import com.jobxhub.server.dto.Group;
import com.jobxhub.server.tag.PageBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Service
public class GroupService {

    @Autowired
    private GroupDao groupDao;

    @Autowired
    private AgentService agentService;

    public void getByPageBean(PageBean pageBean) {
        List<GroupBean> groupBeans = groupDao.getByPageBean(pageBean);
        if (CommonUtils.notEmpty(groupBeans)) {
            int count = groupDao.getCount(pageBean.getFilter());
            List<Group> groups = new ArrayList<Group>(0);
            for (GroupBean groupBean:groupBeans) {
                Group group = Group.transfer.apply(groupBean);
                int agentCount = groupDao.getAgentCount(group.getGroupId());
                group.setAgentCount(agentCount);
                groups.add(group);
            }
            pageBean.setResult(groups);
            pageBean.setTotalCount(count);
        }
    }

    public List<Group> getAll() {
        return Lists.transform(groupDao.getAll(),Group.transfer);
    }

    public List<Group> getForAgent() {
        List<AgentGroupBean> agentGroups = groupDao.getForAgent();
        Group noGroup = new Group();
        noGroup.setGroupName("未分组");
        noGroup.setGroupId(0L);

        Map<Long, Group> groupMap = new HashMap<Long, Group>(0);
        if (CommonUtils.notEmpty(agentGroups)) {
            for (AgentGroupBean agentGroup : agentGroups) {
                Agent agent = new Agent();
                agent.setAgentId(agentGroup.getAgentId());
                agent.setName(agentGroup.getAgentName());
                agent.setHost(agentGroup.getAgentHost());
                if (agentGroup.getGroupId() == null) {
                    noGroup.getAgentList().add(agent);
                } else {
                    if (groupMap.get(agentGroup.getGroupId()) == null) {
                        Group group = new Group();
                        group.setGroupId(agentGroup.getGroupId());
                        group.setGroupName(agentGroup.getGroupName());
                        group.getAgentList().add(agent);
                        groupMap.put(agentGroup.getGroupId(), group);
                    } else {
                        groupMap.get(agentGroup.getGroupId()).getAgentList().add(agent);
                    }
                }
            }
        }

        List<Group> groups = new ArrayList<Group>(0);
        groups.add(noGroup);
        for (Map.Entry<Long, Group> entry : groupMap.entrySet()) {
            groups.add(entry.getValue());
        }
        return groups;
    }

    public void merge(Group group) {
        GroupBean groupBean = GroupBean.transfer.apply(group);
        if (groupBean.getGroupId() == null) {
            groupDao.save(groupBean);
            group.setGroupId(groupBean.getGroupId());
            //新增关联关系
            groupDao.saveGroup(group.getGroupId(),group.getAgentIds());
        }else {
            groupDao.update(groupBean);
            //删除原因的管理关系
            groupDao.deleteGroup(groupBean.getGroupId());
            //保存现在的关联关系
            groupDao.saveGroup(groupBean.getGroupId(),group.getAgentIds());
        }
    }

    public boolean existsName(Long groupId, String groupName) {
        Map<String,Object> filter = new HashMap<String, Object>(0);
        filter.put("groupId",groupId);
        filter.put("groupName",groupName);
        return groupDao.existsCount(filter) > 0;
    }

    public Group getById(Long groupId) {
        GroupBean groupBean = groupDao.getById(groupId);
        if (groupBean!=null) {
            Group group = Group.transfer.apply(groupBean);
            List<Agent> agentList = agentService.getByGroup(group.getGroupId());
            group.setAgentList(agentList);
            return group;
        }
        return null;
    }

}
