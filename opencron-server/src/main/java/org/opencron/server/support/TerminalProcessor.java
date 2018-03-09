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

package org.opencron.server.support;


import com.alibaba.fastjson.JSON;
import org.apache.zookeeper.data.Stat;
import org.opencron.common.util.CommonUtils;
import org.opencron.common.util.WebUtils;
import org.opencron.server.service.TerminalService;
import org.opencron.server.vo.Status;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.net.URLDecoder;
import java.util.List;

@Component
public class TerminalProcessor {


    @Autowired
    private TerminalService termService;

    /**
     * 分配分布式任务
     * @param methodName
     * @param token
     * @param args
     */
    public void doWork(String methodName,HttpServletResponse response, String token, Object...args) {

    }

    public void sendAll(HttpServletResponse response,String token, String cmd) throws Exception {
        cmd = URLDecoder.decode(cmd, "UTF-8");
        TerminalClient terminalClient = TerminalSession.get(token);
        if (terminalClient != null) {
            List<TerminalClient> terminalClients = TerminalSession.findClient(terminalClient.getHttpSessionId());
            for (TerminalClient client : terminalClients) {
                client.write(cmd);
            }
        }
        WebUtils.writeJson(response, JSON.toJSONString(Status.TRUE));
    }

    @RequestMapping(value = "theme.do", method = RequestMethod.POST)
    public void theme(HttpServletResponse response,String token, String theme) throws Exception {
        TerminalClient terminalClient = TerminalSession.get(token);
        if (terminalClient != null) {
            termService.theme(terminalClient.getTerminal(), theme);
        }
        WebUtils.writeJson(response, JSON.toJSONString(Status.TRUE));
    }

    public void resize(HttpServletResponse response,String token, Integer cols, Integer rows, Integer width, Integer height) throws Exception {
        TerminalClient terminalClient = TerminalSession.get(token);
        if (terminalClient != null) {
            terminalClient.resize(cols, rows, width, height);
        }
        WebUtils.writeJson(response, JSON.toJSONString(Status.TRUE));
    }


    public void upload(HttpSession httpSession, HttpServletResponse response, String token, @RequestParam(value = "file", required = false) MultipartFile[] file, String path) {
        TerminalClient terminalClient = TerminalSession.get(token);
        boolean success = true;
        if (terminalClient != null) {
            for (MultipartFile ifile : file) {
                String tmpPath = httpSession.getServletContext().getRealPath("/") + "upload" + File.separator;
                File tempFile = new File(tmpPath, ifile.getOriginalFilename());
                try {
                    ifile.transferTo(tempFile);
                    if (CommonUtils.isEmpty(path)) {
                        path = ".";
                    } else {
                        if (path.endsWith("/")) {
                            path = path.substring(0, path.lastIndexOf("/"));
                        }
                    }
                    terminalClient.upload(tempFile.getAbsolutePath(), path + "/" + ifile.getOriginalFilename(), ifile.getSize());
                    tempFile.delete();
                } catch (Exception e) {
                    success = false;
                }
            }
        }
        WebUtils.writeJson(response, JSON.toJSONString(new Status(success)));
    }

}
