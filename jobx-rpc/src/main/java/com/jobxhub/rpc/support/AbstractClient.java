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
import com.jobxhub.rpc.mina.ConnectWrapper;
import com.jobxhub.rpc.netty.ChannelWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author benjobs
 */
public abstract class AbstractClient implements Client {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected NioSocketConnector connector;

    protected Bootstrap bootstrap;

    protected final ConcurrentHashMap<String, ConnectWrapper> connectTable = new ConcurrentHashMap<String, ConnectWrapper>();

    protected final ConcurrentHashMap<String, ChannelWrapper> channelTable = new ConcurrentHashMap<String, ChannelWrapper>();

    public final ConcurrentHashMap<Long, RpcFuture> futureTable = new ConcurrentHashMap<Long, RpcFuture>(256);

    @Override
    public Response sentSync(Request request) throws Exception {
        this.connect(request);
        return invokeSync(request);
    }

    @Override
    public void sentOneWay(Request request) throws Exception {
        this.connect(request);
        invokeOneWay(request);
    }

    @Override
    public void sentAsync(Request request, InvokeCallback callback) throws Exception {
        this.connect(request);
        invokeAsync(request,callback);
    }

    public ConnectFuture getConnect(Request request) {

        ConnectWrapper connectWrapper = this.connectTable.get(request.getAddress());

        if (connectWrapper != null && connectWrapper.isActive()) {
            return connectWrapper.getConnectFuture();
        }

        synchronized (this) {
            ConnectFuture connectFuture = connector.connect(HttpUtils.parseSocketAddress(request.getAddress()));
            connectWrapper = new ConnectWrapper(connectFuture);
            if (connectFuture.awaitUninterruptibly(Constants.RPC_TIMEOUT)) {
                if (connectWrapper.isActive()) {
                    if (logger.isInfoEnabled()) {
                        logger.info("[JobX] MinaRPC getConnect: connect remote host[{}] success, {}", request.getAddress(), connectFuture.toString());
                    }
                    this.connectTable.put(request.getAddress(), connectWrapper);
                    return connectFuture;
                } else {
                    if (logger.isWarnEnabled()) {
                        logger.warn("[JobX] MinaRPC getConnect: connect remote host[" + request.getAddress() + "] failed, " + connectFuture.toString(), connectFuture.getException());
                    }
                }
            } else {
                if (logger.isWarnEnabled()) {
                    logger.warn("[JobX] MinaRPC getConnect: connect remote host[{}] timeout {}ms, {}", request.getAddress(), Constants.RPC_TIMEOUT, connectFuture);
                }
            }
        }
        return null;
    }

    public Channel getChannel(Request request) {

        ChannelWrapper channelWrapper = this.channelTable.get(request.getAddress());
        if (channelWrapper != null && channelWrapper.isActive()) {
            return channelWrapper.getChannel();
        }
        synchronized (this) {
            // 发起异步连接操作
            ChannelFuture channelFuture = this.bootstrap.connect(HttpUtils.parseSocketAddress(request.getAddress()));
            channelWrapper = new ChannelWrapper(channelFuture);
            if (channelFuture.awaitUninterruptibly(Constants.RPC_TIMEOUT)) {
                if (channelWrapper.isActive()) {
                    if (logger.isInfoEnabled()) {
                        logger.info("[JobX] NettyRPC getChannel: connect remote host[{}] success, {}", request.getAddress(), channelFuture.toString());
                    }
                    this.channelTable.put(request.getAddress(), channelWrapper);
                    return channelWrapper.getChannel();
                } else {
                    if (logger.isWarnEnabled()) {
                        logger.warn("[JobX] NettyRPC getChannel: connect remote host[" + request.getAddress() + "] failed, " + channelFuture.toString(), channelFuture.cause());
                    }
                }
            } else {
                if (logger.isWarnEnabled()) {
                    logger.warn("[JobX] NettyRPC getChannel: connect remote host[{}] timeout {}ms, {}", request.getAddress(), Constants.RPC_TIMEOUT, channelFuture);
                }
            }
        }
        return null;
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
        this.futureTable.clear();
        this.channelTable.clear();
    }

    public synchronized RpcFuture getRpcFuture(Long id) {
        return this.futureTable.get(id);
    }

}
