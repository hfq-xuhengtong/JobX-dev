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

package org.opencron.server.service;

import org.hibernate.Query;
import org.opencron.common.util.CommonUtils;
import org.opencron.common.util.DigestUtils;
import org.opencron.server.dao.QueryDao;
import org.opencron.server.domain.*;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public void initDB() {

        /**
         * for version 1.1.0 update to version 1.2.0(ip rename to host)
         */
        try {
            Query query = queryDao.createQuery("update Agent set host=ip where host=null and ip!=null");
            query.executeUpdate();
        } catch (Exception e) {
            //skip
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
        long count = queryDao.hqlCount("select count(1) from Config");
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

        User user = queryDao.hqlUniqueQuery("from User where userName=?","opencron");
        if (user == null) {
            user = new User();
            user.setUserName("opencron");
            user.setPassword(DigestUtils.md5Hex("opencron").toUpperCase());
            user.setRoleId(999L);
            user.setRealName("opencron");
            user.setEmail("benjobs@qq.com");
            user.setQq("benjobs@qq.com");
            user.setContact("18500193260");
            userService.addUser(user);
        }

    }


}
