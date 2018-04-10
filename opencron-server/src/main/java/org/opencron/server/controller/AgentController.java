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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opencron.common.job.Request;
import org.opencron.common.job.RequestFile;
import org.opencron.common.job.Response;
import org.opencron.common.util.CommonUtils;
import org.opencron.common.util.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.opencron.common.Constants;
import org.opencron.server.service.AgentService;
import org.opencron.server.service.ExecuteService;
import org.opencron.server.tag.PageBean;
import org.apache.commons.codec.digest.DigestUtils;
import org.opencron.server.domain.Agent;
import org.opencron.server.vo.Status;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static org.opencron.common.util.WebUtils.*;


@Controller
@RequestMapping("agent")
public class AgentController extends BaseController {

    @Autowired
    private AgentService agentService;

    @Autowired
    private ExecuteService executeService;

    @RequestMapping("view.htm")
    public String queryAllAgent(HttpSession session, HttpServletRequest request, Model model, PageBean pageBean) {
        agentService.getOwnerAgent(session, pageBean);
        model.addAttribute("connAgents", agentService.getAgentByConnType(Constants.ConnType.CONN));
        return "/agent/view";
    }

    @RequestMapping("refresh.htm")
    public String refreshAgent(HttpSession session, PageBean pageBean) {
        agentService.getOwnerAgent(session, pageBean);
        return "/agent/refresh";
    }

    @RequestMapping(value = "checkname.do", method = RequestMethod.POST)
    @ResponseBody
    public Status checkName(Long id, String name) {
        return Status.create(!agentService.existsName(id, name));
    }

    @RequestMapping(value = "checkdel.do", method = RequestMethod.POST)
    @ResponseBody
    public Status checkDelete(Long id) {
        return Status.create(agentService.checkDelete(id));
    }

    @RequestMapping(value = "delete.do", method = RequestMethod.POST)
    @ResponseBody
    public Status delete(Long id) {
        agentService.delete(id);
        return Status.TRUE;
    }

    @RequestMapping(value = "checkhost.do", method = RequestMethod.POST)
    @ResponseBody
    public Status checkhost(Long id, String host) {
        return Status.create(!agentService.existshost(id, host));
    }

    @RequestMapping("add.htm")
    public String addPage(Model model) {
        List<Agent> agentList = agentService.getAgentByConnType(Constants.ConnType.CONN);
        model.addAttribute("connAgents", agentList);
        return "/agent/add";
    }

    @RequestMapping(value = "add.do", method = RequestMethod.POST)
    public String add(Agent agent) throws Exception {
        if (!agent.getWarning()) {
            agent.setMobiles(null);
            agent.setEmailAddress(null);
        }
        //直联
        if (Constants.ConnType.CONN.getType().equals(agent.getProxy())) {
            agent.setProxyAgent(null);
        }
        agent.setPassword(DigestUtils.md5Hex(agent.getPassword()));
        agent.setStatus(true);
        agent.setDeleted(false);
        agentService.merge(agent);
        return "redirect:/agent/view.htm";
    }

    @RequestMapping(value = "get.do", method = RequestMethod.POST)
    @ResponseBody
    public Agent get(HttpServletResponse response, Long id) {
        Agent agent = agentService.getAgent(id);
        if (agent == null) {
            write404(response);
            return null;
        }
        return agent;
    }

    @RequestMapping(value = "edit.do", method = RequestMethod.POST)
    @ResponseBody
    public Status edit(Agent agentParam) {
        Agent agent = agentService.getAgent(agentParam.getAgentId());
        BeanUtils.copyProperties(agentParam, agent, "machineId", "host", "password", "deleted", "status", "proxyAgent", "mobiles", "emailAddress");
        if (Constants.ConnType.CONN.getType().equals(agentParam.getProxy())) {
            agent.setProxyAgent(null);
        } else {
            agent.setProxyAgent(agentParam.getProxyAgent());
        }
        if (agentParam.getWarning()) {
            agent.setEmailAddress(agentParam.getEmailAddress());
            agent.setMobiles(agentParam.getMobiles());
        }
        agentService.merge(agent);
        return Status.TRUE;
    }

    @RequestMapping(value = "pwd.do", method = RequestMethod.POST)
    @ResponseBody
    public String pwd(Boolean type, Long id, String pwd0, String pwd1, String pwd2) {
        return agentService.editPassword(id, type, pwd0, pwd1, pwd2);
    }

    @RequestMapping("detail/{id}.htm")
    public String showDetail(Model model, @PathVariable("id") Long id) {
        Agent agent = agentService.getAgent(id);
        if (agent == null) {
            return "/error/404";
        }
        model.addAttribute("agent", agent);
        return "/agent/detail";
    }

    @RequestMapping(value = "getConnAgents.do", method = RequestMethod.POST)
    @ResponseBody
    public List<Agent> getConnAgents() {
        return agentService.getAgentByConnType(Constants.ConnType.CONN);
    }

    @RequestMapping(value = "path.do", method = RequestMethod.POST)
    @ResponseBody
    public String getPath(Long agentId) {
        Agent agent = agentService.getAgent(agentId);
        String path = executeService.path(agent);
        return path == null ? "" : path + "/.password";
    }

    @RequestMapping(value = "listpath.do", method = RequestMethod.POST)
    @ResponseBody
    public Map getPath(Long agentId,String path) {
        if (CommonUtils.isEmpty(path)) {
            path = "/";
        }
        Agent agent = agentService.getAgent(agentId);
        Map<String,Object> map = new HashMap<String, Object>(0);
        Response response = executeService.listPath(agent,path);
        map.put("status",response.isSuccess());
        if (response.isSuccess()) {
            map.put("path",response.getResult().get(Constants.PARAM_LISTPATH_PATH_KEY));
        }
        return map;
    }

    @RequestMapping(value = "upload.do", method = RequestMethod.POST)
    @ResponseBody
    public Status upload(HttpSession httpSession,Long agentId,@RequestParam(value = "file", required = false) MultipartFile file,String savePath,String postcmd) throws IOException {
        Agent agent = agentService.getAgent(agentId);

        String rootPath = httpSession.getServletContext().getRealPath("/");
        String path = rootPath.replaceFirst("/$", "") + "/upload/";

        File upFile = new File(path, file.getOriginalFilename());
        if (!upFile.exists()) {
            upFile.mkdirs();
            file.transferTo(upFile);
        }else {
            String existMD5 = DigestUtils.md5Hex(file.getBytes());
            String thisMD5 = IOUtils.getFileMD5(upFile);
            //server端已经存在该文件
            if (!existMD5.equals(thisMD5)) {
                upFile.delete();
                file.transferTo(upFile);
            }
        }
        RequestFile requestFile = new RequestFile(upFile);
        requestFile.setSavePath(savePath);
        requestFile.setPostCmd(postcmd);
        Response response = executeService.upload(agent,requestFile);
        return Status.create(response.isSuccess());
    }


}
