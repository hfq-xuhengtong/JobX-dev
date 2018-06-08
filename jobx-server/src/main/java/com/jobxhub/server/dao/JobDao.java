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
package com.jobxhub.server.dao;

import com.jobxhub.server.domain.JobBean;
import com.jobxhub.server.tag.PageBean;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface JobDao {

    List<JobBean> getAll();

    List<JobBean> getByPageBean(@Param("pager") PageBean pageBean);

    int getCount(@Param("filter") Map<String, Object> filter);

    JobBean getById(Long id);

    List<JobBean> getByAgent(Long agentId);

    int existsCount(@Param("jobId") Long jobId, @Param("agentId") Long agentId, @Param("name") String name);

    List<JobBean> getScheduleJob();

    void save(JobBean job);

    void update(JobBean job);

    void delete(Long id);

    void updateToken(@Param("jobId") Long jobId,@Param("token") String token);

    void pause(@Param("jobId") Long jobId,@Param("pause") Boolean pause);
}
