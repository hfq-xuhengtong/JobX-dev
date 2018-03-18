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
package org.opencron.rpc.netty;

import io.netty.channel.*;
import org.opencron.common.job.Request;
import org.opencron.common.job.Response;
import org.opencron.common.job.RpcType;
import org.opencron.common.logging.LoggerFactory;
import org.opencron.rpc.ServerHandler;
import org.slf4j.Logger;


/**
 * @author benjobs
 */

public class NettyServerHandler extends SimpleChannelInboundHandler<Request> {

    private Logger logger = LoggerFactory.getLogger(NettyServerHandler.class);

    private ServerHandler handler;

    public NettyServerHandler(ServerHandler handler) {
        this.handler = handler;
    }

    @Override
    public void channelActive(ChannelHandlerContext handlerContext) {
        if (logger.isInfoEnabled()) {
            logger.info("[opencron] agent channelActive Active...");
        }
        handlerContext.fireChannelActive();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext handlerContext,final Request request) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("[opencron]Receive request {}" + request.getId());
        }
        Response response = handler.handle(request);
        if (request.getRpcType() != RpcType.ONE_WAY) {
            handlerContext.writeAndFlush(response).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                    if (logger.isInfoEnabled()) {
                        logger.info("[opencron] Send response for request id:{},action:{}", request.getId(), request.getAction());
                    }
                }
            });
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (logger.isErrorEnabled()) {
            logger.error("[opencron] agent channelInactive");
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

}