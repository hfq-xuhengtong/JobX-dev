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

import com.jcraft.jsch.*;
import org.opencron.common.Constants;
import org.opencron.common.util.IOUtils;
import org.opencron.server.dao.QueryDao;
import org.opencron.server.domain.Terminal;
import org.opencron.server.domain.User;
import org.opencron.server.support.OpencronTools;
import org.opencron.server.support.SshUserInfo;
import org.opencron.server.support.TerminalClient;
import org.opencron.server.tag.PageBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.BadPaddingException;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.util.*;

import static org.opencron.common.util.CommonUtils.notEmpty;

/**
 * @author <a href="mailto:benjobs@qq.com">benjobs@qq.com</a>
 * @name:CommonUtil
 * @version: 1.0.0
 * @company: org.opencron
 * @description: webconsole核心类
 * @date: 2016-05-25 10:03<br/><br/>
 * <p>
 * <b style="color:RED"></b><br/><br/>
 * 你快乐吗?<br/>
 * 风轻轻的问我<br/>
 * 曾经快乐过<br/>
 * 那时的湖面<br/>
 * 她踏着轻舟泛过<br/><br/>
 * <p>
 * 你忧伤吗?<br/>
 * 雨悄悄的问我<br/>
 * 一直忧伤着<br/>
 * 此时的四季<br/>
 * 全是她的柳絮飘落<br/><br/>
 * <p>
 * 你心痛吗?<br/>
 * 泪偷偷的问我<br/>
 * 心痛如刀割<br/>
 * 收到记忆的包裹<br/>
 * 都是她冰清玉洁还不曾雕琢<br/><br/>
 * <p>
 * <hr style="color:RED"/>
 */

@Service
@Transactional
public class TerminalService {

    private static Logger logger = LoggerFactory.getLogger(TerminalService.class);

    @Autowired
    private QueryDao queryDao;

    public boolean exists(String userName, String host) throws Exception {
        Integer count = queryDao.hqlIntUniqueResult("select count(1) from Terminal where userName=? and host=?", userName, host);
        return count > 0;
    }

    public boolean merge(Terminal term) throws Exception {
        try {
            if (term.getId() == null) {
                queryDao.save(term);
            } else {
                Terminal terminal = queryDao.get(Terminal.class, term.getId());
                term.setId(terminal.getId());
                queryDao.merge(term);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public Terminal.AuthStatus auth(Terminal terminal) {
        JSch jSch = new JSch();
        Session session = null;
        try {
            session = jSch.getSession(terminal.getUserName(), terminal.getHost(), terminal.getPort());
            Constants.SshType sshType = Constants.SshType.getByType(terminal.getSshType());
            switch (sshType) {
                case SSHKEY:
                    //需要读取用户上传的sshKey
                    if (terminal.getSshKeyFile() != null) {
                        //将keyfile读取到数据库
                        terminal.setPrivateKey(terminal.getSshKeyFile().getBytes());
                    }
                    if (notEmpty(terminal.getPrivateKey())) {
                        File keyFile = new File(terminal.getPrivateKeyPath());
                        if (keyFile.exists()) {
                            keyFile.delete();
                        }
                        //将数据库中的私钥写到用户的机器上
                        IOUtils.writeFile(keyFile, new ByteArrayInputStream(terminal.getPrivateKey()));
                        if (notEmpty(terminal.getPhrase())) {
                            //设置带口令的密钥
                            jSch.addIdentity(terminal.getPrivateKeyPath(), terminal.getPhrase());
                        } else {
                            //设置不带口令的密钥
                            jSch.addIdentity(terminal.getPrivateKeyPath());
                        }
                        UserInfo userInfo = new SshUserInfo();
                        session.setUserInfo(userInfo);
                    }
                    break;
                case ACCOUNT:
                    session.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password");
                    session.setPassword(terminal.getPassword());
                    break;
            }
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(TerminalClient.SESSION_TIMEOUT);
            return Terminal.AuthStatus.SUCCESS;
        } catch (Exception e) {
            if (e.getMessage().toLowerCase().contains("userauth fail")) {
                return Terminal.AuthStatus.PUBLIC_KEY_FAIL;
            } else if (e.getMessage().toLowerCase().contains("auth fail") || e.getMessage().toLowerCase().contains("auth cancel")) {
                return Terminal.AuthStatus.AUTH_FAIL;
            } else if (e.getMessage().toLowerCase().contains("unknownhostexception")) {
                if (logger.isInfoEnabled()) {
                    logger.info("[opencron]:error: DNS Lookup Failed ");
                }
                return Terminal.AuthStatus.HOST_FAIL;
            } else if (e instanceof BadPaddingException) {//RSA解码错误..密码错误...
                return Terminal.AuthStatus.AUTH_FAIL;
            } else {
                return Terminal.AuthStatus.GENERIC_FAIL;
            }
        } finally {
            if (session != null) {
                session.disconnect();
            }
        }
    }

    public PageBean<Terminal> getPageBeanByUser(PageBean pageBean, Long userId) {
        String hql = "from  Terminal where userId = ? order by ";
        pageBean.verifyOrderBy("name", "name", "host", "port", "sshType", "logintime");
        hql += pageBean.getOrderBy() + " " + pageBean.getOrder();
        return queryDao.hqlPageQuery(hql, pageBean, userId);
    }

    public Terminal getById(Long id) {
        return queryDao.get(Terminal.class, id);
    }

    public String delete(HttpSession session, Long id) {
        Terminal term = getById(id);
        if (term == null) {
            return "error";
        }
        User user = OpencronTools.getUser(session);

        if (!OpencronTools.isPermission(session) && !user.getUserId().equals(term.getUserId())) {
            return "error";
        }

        int count = queryDao.createQuery("delete from Terminal where id=?", term.getId()).executeUpdate();
        return String.valueOf(count > 0);
    }

    public void login(Terminal terminal) {
        terminal = getById(terminal.getId());
        terminal.setLogintime(new Date());
        queryDao.merge(terminal);
    }

    public List<Terminal> getListByUser(User user) {
        return queryDao.hqlQuery("from  Terminal where userId =?", user.getUserId());
    }

    public void theme(Terminal terminal, String theme) throws Exception {
        terminal.setTheme(theme);
        merge(terminal);
    }


}


