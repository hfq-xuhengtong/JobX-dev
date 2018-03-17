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

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.DefaultSocketSessionConfig;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.opencron.common.job.Request;
import org.opencron.common.job.Response;
import org.opencron.common.logging.LoggerFactory;
import org.opencron.rpc.Client;
import org.opencron.rpc.InvokeCallback;
import org.opencron.rpc.RpcFuture;
import org.opencron.rpc.support.AbstractClient;
import org.slf4j.Logger;

public class MinaClient extends AbstractClient implements Client {

    private static Logger logger = LoggerFactory.getLogger(MinaClient.class);

    @Override
    public void connect() {
        connector = new NioSocketConnector();
        connector.getFilterChain().addLast("codec", new ProtocolCodecFilter(new MinaCodecAdapter(Request.class, Response.class)));
        connector.setHandler(new MinaClientHandler(this));
        connector.setConnectTimeoutMillis(5000);
        DefaultSocketSessionConfig sessionConfiguration = (DefaultSocketSessionConfig) connector.getSessionConfig();
        sessionConfiguration.setTcpNoDelay(true);
        sessionConfiguration.setKeepAlive(true);
        sessionConfiguration.setWriteTimeout(5);
    }

    @Override
    public void disconnect() throws Throwable {
        if (this.connector != null) {
            this.connector.dispose();
            this.connector = null;
        }
    }

    @Override
    public Response sentSync(final Request request) throws Exception {
        final ConnectFuture connect = super.getConnect(request);
        if (connect != null && connect.isConnected()) {
            RpcFuture rpcFuture = new RpcFuture(request.getTimeOut());
            //写数据
            connect.addListener(new AbstractClient.FutureListener(request, rpcFuture));
            connect.getSession().write(request);
            return rpcFuture.get();
        } else {
            throw new IllegalArgumentException("[opencron] MinaRPC channel not active. request id:" + request.getId());
        }
    }

    @Override
    public void sentOneway(final Request request) throws Exception {
        ConnectFuture connect = super.getConnect(request);
        if (connect != null && connect.isConnected()) {
            connect.addListener(new AbstractClient.FutureListener(request, null));
            connect.getSession().write(request);
        } else {
            throw new IllegalArgumentException("[opencron] MinaRPC channel not active. request id:" + request.getId());
        }
    }

    @Override
    public void sentAsync(final Request request, final InvokeCallback callback) throws Exception {
        final ConnectFuture connect = super.getConnect(request);
        if (connect != null && connect.isConnected()) {
            RpcFuture rpcFuture = new RpcFuture(request.getTimeOut(), callback);
            connect.addListener(new AbstractClient.FutureListener(request, rpcFuture));
            connect.getSession().write(request);
        } else {
            throw new IllegalArgumentException("[opencron] MinaRPC sentAsync channel not active. request id:" + request.getId());
        }
    }

}
