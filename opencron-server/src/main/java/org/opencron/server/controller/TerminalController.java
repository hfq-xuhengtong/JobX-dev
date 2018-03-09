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

package org.opencron.server.controller;

import org.opencron.common.Constants;
import org.opencron.common.util.CommonUtils;
import org.opencron.common.util.collection.ParamsMap;
import org.opencron.server.domain.Terminal;
import org.opencron.server.domain.User;

import org.opencron.server.support.*;
import org.opencron.server.service.TerminalService;
import org.opencron.server.tag.PageBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.File;
import java.net.URLDecoder;
import java.util.List;
import java.util.Map;

import static org.opencron.common.util.WebUtils.*;

/**
 * benjobs..
 * 终端,分布式下有bug,发送命令,上传文件等,均可能发送到另一台server的情况..目前没有特别好的方案
 * 待解决...
 */
@Controller
@RequestMapping("terminal")
public class TerminalController extends BaseController {

    @Autowired
    private TerminalService termService;

    @Autowired
    private TerminalContext terminalContext;

    @Autowired
    private TerminalProcessor terminalProcessor;

    @RequestMapping(value = "ssh.do", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, String> ssh(HttpSession session, Terminal terminal) throws Exception {
        User user = OpencronTools.getUser(session);

        terminal = termService.getById(terminal.getId());

        Terminal.AuthStatus authStatus = termService.auth(terminal);
        //登陆认证成功
        if (authStatus.equals(Terminal.AuthStatus.SUCCESS)) {
            String token = CommonUtils.uuid();
            terminal.setUser(user);
            terminalContext.put(token, terminal);
            OpencronTools.setSshSessionId(session, token);

            return ParamsMap.map()
                    .set("status", "success")
                    .set("url", "/terminal/open.htm?token=" + token);
        } else {
            return ParamsMap.map().set("status", authStatus.status);
        }
    }

    @RequestMapping("ssh2.htm")
    public String ssh2(HttpSession session, Terminal terminal) throws Exception {
        User user = OpencronTools.getUser(session);

        terminal = termService.getById(terminal.getId());
        Terminal.AuthStatus authStatus = termService.auth(terminal);
        //登陆认证成功
        if (authStatus.equals(Terminal.AuthStatus.SUCCESS)) {
            String token = CommonUtils.uuid();
            terminal.setUser(user);
            terminalContext.put(token, terminal);
            OpencronTools.setSshSessionId(session, token);
            return "redirect:/terminal/open.htm?token=" + token;
        } else {
            //重新输入密码进行认证...
            return "redirect:/terminal/open.htm?id=" + terminal.getId();
        }

    }

    @RequestMapping(value = "detail.do", method = RequestMethod.POST)
    @ResponseBody
    public Terminal detail(Terminal terminal) throws Exception {
        return termService.getById(terminal.getId());
    }

    @RequestMapping(value = "exists.do", method = RequestMethod.POST)
    @ResponseBody
    public boolean exists(Terminal terminal) throws Exception {
        return termService.exists(terminal.getUserName(), terminal.getHost());
    }

    @RequestMapping("view.htm")
    public String view(HttpSession session, PageBean pageBean, Model model) {
        pageBean = termService.getPageBeanByUser(pageBean, OpencronTools.getUserId(session));
        model.addAttribute("pageBean", pageBean);
        return "/terminal/view";
    }

    @RequestMapping("open.htm")
    public String open(HttpServletRequest request, String token, Long id) {
        //登陆失败
        if (token == null && id != null) {
            Terminal terminal = termService.getById(id);
            request.setAttribute("terminal", terminal);
            return "/terminal/error";
        }
        Terminal terminal = terminalContext.get(token);
        if (terminal != null) {
            request.setAttribute("name", terminal.getName() + "(" + terminal.getHost() + ")");
            request.setAttribute("token", token);
            request.setAttribute("id", terminal.getId());
            request.setAttribute("theme", terminal.getTheme());
            List<Terminal> terminas = termService.getListByUser(terminal.getUser());
            request.setAttribute("terms", terminas);
            return "/terminal/console";
        }
        return "/terminal/error";
    }

    /**
     * 不能重复复制会话,可以通过ajax的方式重新生成token解决....
     *
     * @param session
     * @param id
     * @param token
     * @return
     * @throws Exception
     */
    @RequestMapping("reopen.htm")
    public String reopen(HttpSession session, Long id, String token) throws Exception {
        String reKey = id + "_" + token;
        Terminal terminal = terminalContext.remove(reKey);//reKey
        if (terminal != null) {
            token = CommonUtils.uuid();
            terminalContext.put(token, terminal);
            session.setAttribute(Constants.PARAM_SSH_SESSION_ID_KEY, token);
            return "redirect:/terminal/open.htm?token=" + token;
        }
        return "/terminal/error";
    }

    @RequestMapping(value = "resize.do", method = RequestMethod.POST)
    public void resize(HttpServletResponse response,String token, Integer cols, Integer rows, Integer width, Integer height) throws Exception {
        terminalProcessor.doWork("resize.do",response,token,cols,rows,width,height);
    }

    @RequestMapping(value = "sendAll.do", method = RequestMethod.POST)
    public void sendAll(HttpServletResponse response,String token, String cmd) throws Exception {
        terminalProcessor.doWork("sendAll.do",response,token,cmd);
    }

    @RequestMapping(value = "theme.do", method = RequestMethod.POST)
    public void theme(HttpServletResponse response,String token, String theme) throws Exception {
        terminalProcessor.doWork("theme.do",response,token,theme);
    }

    @RequestMapping(value = "upload.do", method = RequestMethod.POST)
    public void upload(HttpSession httpSession, HttpServletResponse response, String token, @RequestParam(value = "file", required = false) MultipartFile[] file, String path) {
        terminalProcessor.doWork("upload.do",response,token,httpSession,file,path);
    }

    @RequestMapping(value = "save.do", method = RequestMethod.POST)
    @ResponseBody
    public String save(HttpSession session, Terminal term, @RequestParam(value = "sshkey", required = false) MultipartFile sshkey) throws Exception {
        term.setSshKeyFile(sshkey);
        Terminal.AuthStatus authStatus = termService.auth(term);
        if (authStatus.equals(Terminal.AuthStatus.SUCCESS)) {
            User user = OpencronTools.getUser(session);
            term.setUserId(user.getUserId());
            termService.merge(term);
        }
        return authStatus.status;
    }


    @RequestMapping(value = "delete.do", method = RequestMethod.POST)
    @ResponseBody
    public String delete(HttpSession session, Terminal term) throws Exception {
        return termService.delete(session, term.getId());
    }

}