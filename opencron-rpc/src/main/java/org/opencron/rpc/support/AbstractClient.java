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
package org.opencron.rpc.support;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.opencron.common.job.Request;
import org.opencron.common.util.HttpUtils;
import org.opencron.common.util.IdGenerator;
import org.opencron.rpc.InvokeCallback;
import org.opencron.rpc.RpcFuture;
import org.opencron.rpc.mina.ConnectWrapper;
import org.opencron.rpc.netty.ChannelWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public abstract class AbstractClient {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    //for mina
    protected final ConcurrentHashMap<String, ConnectWrapper> connectTable = new ConcurrentHashMap<String, ConnectWrapper>();

    protected NioSocketConnector connector;

    //for netty
    protected final ConcurrentHashMap<String, ChannelWrapper> channelTable = new ConcurrentHashMap<String, ChannelWrapper>();

    public final ConcurrentHashMap<Integer, RpcFuture> futureTable = new ConcurrentHashMap<Integer, RpcFuture>(256);

    protected ScheduledThreadPoolExecutor executor;

    public AbstractClient() {

        connect();

        this.executor = new ScheduledThreadPoolExecutor(5, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "RPC_" + IdGenerator.getId());
            }
        });
        this.executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                Iterator<Map.Entry<Integer, RpcFuture>> it = futureTable.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Integer, RpcFuture> next = it.next();
                    RpcFuture rep = next.getValue();
                    //超时
                    if ((rep.getBeginTime() + rep.getTimeoutMillis() + 1000) <= System.currentTimeMillis()) {
                        it.remove();
                    }
                }
            }
        }, 500, 500, TimeUnit.MILLISECONDS);
    }

    public ConnectFuture getConnect(Request request) {

        ConnectWrapper connectWrapper = this.connectTable.get(request.getAddress());

        if (connectWrapper != null) {
            if (connectWrapper.isActive()) {
                return connectWrapper.getConnectFuture();
            }
            connectWrapper.close();
        }

        synchronized (this) {
            // 发起异步连接操作
            ConnectFuture connectFuture = connector.connect(HttpUtils.parseSocketAddress(request.getAddress()));
            connectWrapper = new ConnectWrapper(connectFuture);
            this.connectTable.put(request.getAddress(), connectWrapper);
        }

        if (connectWrapper != null) {
            ConnectFuture connectFuture = connectWrapper.getConnectFuture();
            long timeout = 5000;
            if (connectFuture.awaitUninterruptibly(timeout)) {
                if (connectWrapper.isActive()) {
                    if (logger.isInfoEnabled()) {
                        logger.info("[opencron] MinaRPC getConnect: connect remote host[{}] success, {}", request.getAddress(), connectFuture.toString());
                    }
                    return connectWrapper.getConnectFuture();
                } else {
                    if (logger.isWarnEnabled()) {
                        logger.warn("[opencron] MinaRPC getConnect: connect remote host[" + request.getAddress() + "] failed, " + connectFuture.toString(), connectFuture.getException());
                    }
                }
            } else {
                if (logger.isWarnEnabled()) {
                    logger.warn("[opencron] MinaRPC getConnect: connect remote host[{}] timeout {}ms, {}", request.getAddress(), timeout, connectFuture);
                }
            }
        }
        return null;
    }

    public Channel getChannel(Bootstrap bootstrap, Request request) {

        ChannelWrapper channelWrapper = this.channelTable.get(request.getAddress());

        if (channelWrapper != null && channelWrapper.isActive()) {
            return channelWrapper.getChannel();
        }

        synchronized (this) {
            // 发起异步连接操作
            ChannelFuture channelFuture = bootstrap.connect(HttpUtils.parseSocketAddress(request.getAddress()));
            channelWrapper = new ChannelWrapper(channelFuture);
            this.channelTable.put(request.getAddress(), channelWrapper);
        }
        if (channelWrapper != null) {
            ChannelFuture channelFuture = channelWrapper.getChannelFuture();
            long timeout = 5000;
            if (channelFuture.awaitUninterruptibly(timeout)) {
                if (channelWrapper.isActive()) {
                    if (logger.isInfoEnabled()) {
                        logger.info("[opencron] NettyRPC getChannel: connect remote host[{}] success, {}", request.getAddress(), channelFuture.toString());
                    }
                    return channelWrapper.getChannel();
                } else {
                    if (logger.isWarnEnabled()) {
                        logger.warn("[opencron] NettyRPC getChannel: connect remote host[" + request.getAddress() + "] failed, " + channelFuture.toString(), channelFuture.cause());
                    }
                }
            } else {
                if (logger.isWarnEnabled()) {
                    logger.warn("[opencron] NettyRPC getChannel: connect remote host[{}] timeout {}ms, {}", request.getAddress(), timeout, channelFuture);
                }
            }
        }
        return null;
    }

    public class FutureListener implements ChannelFutureListener, IoFutureListener {
        private RpcFuture rpcFuture;
        private Request request;

        public FutureListener(Request request, RpcFuture rpcFuture) {
            this.request = request;
            this.rpcFuture = rpcFuture;
            if (request != null) {
                futureTable.put(request.getId(), rpcFuture);
            }
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {

            if (future.isSuccess()) {
                if (rpcFuture != null) {
                    rpcFuture.setSendDone(true);
                }
                if (logger.isInfoEnabled()) {
                    logger.info("[opencron] NettyRPC sent success, request id:{}", request.getId());
                }
                return;
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("[opencron] NettyRPC sent failure, request id:{}", request.getId());
                }
                if (this.rpcFuture != null) {
                    futureTable.remove(request.getId());
                    rpcFuture.setSendDone(false);
                    rpcFuture.setFailure(future.cause());
                    rpcFuture.getCallback().failure(future.cause());
                }
            }
        }

        @Override
        public void operationComplete(IoFuture future) {
            if (future.isDone()) {
                if (logger.isInfoEnabled()) {
                    logger.info("[opencron] MinaRPC sent success, request id:{}", request.getId());
                }
                if (rpcFuture != null) {
                    rpcFuture.setSendDone(true);
                }
                return;
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("[opencron] MinaRPC sent failure, request id:{}", request.getId());
                }
                if (rpcFuture != null) {
                    futureTable.remove(request.getId());
                    rpcFuture.setSendDone(false);
                    rpcFuture.setFailure(getConnect(request).getException());
                    rpcFuture.getCallback().failure(getConnect(request).getException());
                }
            }
        }

    }

    protected abstract void connect();

}
