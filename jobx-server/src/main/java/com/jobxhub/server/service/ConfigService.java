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

import com.jobxhub.common.util.PropertyPlaceholder;
import com.jobxhub.common.util.ReflectUtils;
import org.hibernate.Query;
import com.jobxhub.common.util.CommonUtils;
import com.jobxhub.common.util.DigestUtils;
import com.jobxhub.server.dao.QueryDao;
import com.jobxhub.server.domain.*;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.Table;
import java.lang.annotation.Annotation;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Created by ChenHui on 2016/2/17.
 */
@Service
@Transactional
public class ConfigService {

    @Autowired
    private QueryDao queryDao;

    @Autowired
    private UserService userService;

    @Autowired
    private JobService jobService;

    public Config getSysConfig() {
        return queryDao.hqlUniqueQuery("from Config where configId = 1");
    }

    public void update(Config config) {
        queryDao.save(config);
    }

    public void initDB() throws SQLException {

        /**
         * for version 1.1.0 update to version 1.2.0(ip rename to host)
         * alter status boolean to int
         */
        try {
            String url = PropertyPlaceholder.get("jdbc.url");
            String userName = PropertyPlaceholder.get("jdbc.username");
            String password =  PropertyPlaceholder.get("jdbc.password");
            String tableName = getTableName(Agent.class);

            Connection connection = DriverManager.getConnection(url,userName,password);
            connection.setAutoCommit(true);
            String sql = String.format("alter table %s modify `status` int",tableName);
            connection.prepareStatement(sql).execute();

            sql = String.format("update %s set `host`=ip where `host` is null and ip is not null",tableName);
            connection.prepareStatement(sql).execute();

            sql = String.format("alter table %s drop `ip`",tableName);
            connection.prepareStatement(sql).execute();
        }catch (Exception e) {
            //skip.....
        }

        /**
         * for version 1.1.0 update to version 1.2.0(api support,init token)
         */
        List<Job> jobs = queryDao.createQuery("from Job where token=null").list();
        if (CommonUtils.notEmpty(jobs)) {
            for (Job job : jobs) {
                if (job.getToken() == null) {
                    job.setToken(CommonUtils.uuid());
                }
                jobService.merge(job);
            }
        }

        //init config
        long count = queryDao.hqlCount("from Config");
        if (count == 0) {
            Config config = new Config();
            config.setConfigId(1L);
            config.setSenderEmail("you_mail_name");
            config.setSmtpHost("smtp.exmail.qq.com");
            config.setSmtpPort(465);
            config.setPassword("your_mail_pwd");
            config.setSendUrl("http://your_url");
            config.setSpaceTime(30);
            queryDao.save(config);
        }

        Role admin = queryDao.hqlUniqueQuery("from Role where roleId=1");
        if (admin == null) {
            Role role = new Role();
            role.setRoleId(1L);
            role.setRoleName("admin");
            role.setDescription("view privileges");
            queryDao.save(role);
        }


        Role superAdmin = queryDao.hqlUniqueQuery("from Role where roleId=999");
        if (superAdmin == null) {
            Role role = new Role();
            role.setRoleId(999L);
            role.setRoleName("superAdmin");
            role.setDescription("view privileges");
            queryDao.save(role);
        }

        User user = queryDao.hqlUniqueQuery("from User where userName=?","jobx");
        if (user == null) {
            user = new User();
            user.setUserName("jobx");
            user.setPassword(DigestUtils.md5Hex("jobx").toUpperCase());
            user.setRoleId(999L);
            user.setRealName("jobx");
            user.setEmail("benjobs@qq.com");
            user.setQq("benjobs@qq.com");
            user.setContact("18500193260");
            userService.addUser(user);
        }

    }

    private String getTableName(Class<?> clazz) {
        Table table = clazz.getAnnotation(Table.class);
        if (table == null||table.name()==null) {
            return "t_".concat(clazz.getSimpleName());
        }
        return table.name();
    }


}
