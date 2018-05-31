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
package com.jobxhub.rpc.support;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import com.jobxhub.common.Constants;
import com.jobxhub.common.job.Request;
import com.jobxhub.common.job.Response;
import com.jobxhub.common.util.HttpUtils;
import com.jobxhub.rpc.Client;
import com.jobxhub.rpc.InvokeCallback;
import com.jobxhub.rpc.RpcFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.jobxhub.common.util.collection.HashMap;

/**
 * @author benjobs
 */
public abstract class AbstractClient implements Client {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected NioSocketConnector connector;

    protected Bootstrap bootstrap;

    public final Map<Long, RpcFuture> futureTable = new HashMap<Long, RpcFuture>(256);

    private final Map<String,Long> clientMap = new HashMap<String, Long>(0);

    @Override
    public Response sentSync(Request request) throws Exception {
        this.doConnect(request);
        return invokeSync(request);
    }

    @Override
    public void sentOneWay(Request request) throws Exception {
        this.doConnect(request);
        invokeOneWay(request);
    }

    @Override
    public void sentAsync(Request request, InvokeCallback callback) throws Exception {
        this.doConnect(request);
        invokeAsync(request,callback);
    }

    public ConnectFuture getConnect(final Request request) {
        synchronized (this) {
            return connector.connect(HttpUtils.parseSocketAddress(request.getAddress()));
        }
    }

    public Channel getChannel(Request request) {
        synchronized (this) {
            // 发起异步连接操作
            ChannelFuture channelFuture = this.bootstrap.connect(HttpUtils.parseSocketAddress(request.getAddress()));
            boolean ret = channelFuture.awaitUninterruptibly(Constants.RPC_TIMEOUT, TimeUnit.MILLISECONDS);
            if (ret && channelFuture.isSuccess()) {
                return channelFuture.channel();
            }
            return null;
        }
    }

    public class FutureListener implements ChannelFutureListener, IoFutureListener {

        private RpcFuture rpcFuture;

        public FutureListener(RpcFuture rpcFuture) {
            if (rpcFuture != null) {
                this.rpcFuture = rpcFuture;
                futureTable.put(rpcFuture.getFutureId(), rpcFuture);
            }
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (future.isSuccess()) {
                if (logger.isInfoEnabled()) {
                    logger.info("[JobX] NettyRPC sent success, request id:{}", rpcFuture.getRequest().getId());
                }
                return;
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("[JobX] NettyRPC sent failure, request id:{}", rpcFuture.getRequest().getId());
                }
                if (this.rpcFuture != null) {
                    rpcFuture.caught(future.cause());
                }
            }
            futureTable.remove(rpcFuture.getFutureId());
        }

        @Override
        public void operationComplete(IoFuture future) {
            if (future.isDone()) {
                if (logger.isInfoEnabled()) {
                    logger.info("[JobX] MinaRPC sent success, request id:{}", rpcFuture.getRequest().getId());
                }
                return;
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("[JobX] MinaRPC sent failure, request id:{}", rpcFuture.getRequest().getId());
                }
                if (rpcFuture != null) {
                    rpcFuture.caught(getConnect(rpcFuture.getRequest()).getException());
                }
            }
            futureTable.remove(rpcFuture.getRequest().getId());
        }

    }

    private void doConnect(Request request) {
        if (!clientMap.containsKey(request.getAddress())) {
            this.connect(request);
            clientMap.put(request.getAddress(),request.getId());
        }
    }

    @Override
    public abstract void connect(Request request);

    public abstract Response invokeSync(Request request) throws Exception;

    public abstract void invokeOneWay(Request request) throws Exception;

    public abstract void invokeAsync(Request request, InvokeCallback callback) throws Exception;

    @Override
    public void disconnect() throws Throwable {
        if (this.connector != null) {
            this.connector.dispose();
            this.connector = null;
        }
    }

    public synchronized RpcFuture getRpcFuture(Long id) {
        return this.futureTable.get(id);
    }

}
