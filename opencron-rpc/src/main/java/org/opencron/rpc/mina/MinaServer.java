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
package org.opencron.rpc.mina;


import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.executor.ExecutorFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.opencron.common.job.Request;
import org.opencron.common.job.Response;
import org.opencron.rpc.Server;
import org.opencron.rpc.ServerHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import static org.opencron.common.util.ExceptionUtils.stackTrace;

public class MinaServer implements Server {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private NioSocketAcceptor acceptor;

    private InetSocketAddress socketAddress;

    @Override
    public void start(final int port, ServerHandler handler) {
        final MinaServerHandler serverHandler = new MinaServerHandler(handler);
        this.socketAddress = new InetSocketAddress(port);

        acceptor = new NioSocketAcceptor();
        acceptor.getFilterChain().addLast("threadPool", new ExecutorFilter(Executors.newCachedThreadPool()));
        acceptor.getFilterChain().addLast("codec", new ProtocolCodecFilter(new MinaCodecAdapter(Response.class, Request.class)));
        acceptor.setHandler(serverHandler);
        try {
            acceptor.bind(this.socketAddress);
            if (logger.isInfoEnabled()) {
                logger.info("[opencron] MinaServer start at address:{} success", port);
            }
        } catch (IOException e) {
            logger.error("[opencron] MinaServer start failure: {}", stackTrace(e));
        }
    }

    @Override
    public void destroy() throws Throwable {
        try {
            if (acceptor != null) {
                acceptor.dispose();
            }
            if (logger.isInfoEnabled()) {
                logger.info("[opencron] MinaServer stoped!");
            }
        } catch (Throwable e) {
            if (logger.isErrorEnabled()) {
                logger.error("[opencron] MinaServer stop error:{}", stackTrace(e));
            }
        }
    }

}
