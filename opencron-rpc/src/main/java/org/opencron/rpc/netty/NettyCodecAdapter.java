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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import org.opencron.common.Constants;
import org.opencron.common.ext.ExtensionLoader;
import org.opencron.common.serialize.ObjectInput;
import org.opencron.common.serialize.ObjectOutput;
import org.opencron.common.serialize.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.opencron.common.util.ExceptionUtils.stackTrace;

public class NettyCodecAdapter<T> {

    private static Logger logger = LoggerFactory.getLogger(NettyCodecAdapter.class);

    private static Serializer serializer = ExtensionLoader.load(Serializer.class);

    private ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    public static NettyCodecAdapter getCodecAdapter() {
        return new NettyCodecAdapter();
    }

    public Encoder getEncoder(Class<T> type) throws Exception {
        return new Encoder(type);
    }

    public Decoder getDecoder(Class<T> type) throws IOException {
        return new Decoder(type);
    }

    private class Encoder<T> extends MessageToByteEncoder {

        private Class<?> type = null;

        public Encoder(Class<T> type) {
            this.type = type;
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
            try {
                if (type.isInstance(msg)) {

                    ObjectOutput objectOutput = serializer.serialize(outputStream);
                    objectOutput.writeObject(msg);
                    objectOutput.flushBuffer();
                    byte[] data = outputStream.toByteArray();
                    outputStream.reset();

                    out.writeInt(data.length);
                    out.writeBytes(data);
                } else {
                    if (logger.isErrorEnabled()) {
                        logger.error("[opencron] NettyCodecAdapter encode error: this encode target is not instanceOf {}", this.type.getName());
                    }
                }
            } catch (Exception e) {
                if (logger.isErrorEnabled()) {
                    logger.error("[opencron] NettyCodecAdapter encode error:", stackTrace(e));
                }
            }

        }
    }

    private class Decoder<T> extends ByteToMessageDecoder {

        private Class<T> type;

        public Decoder(Class<T> type) {
            this.type = type;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            try {
                if (in.readableBytes() < Constants.HEADER_SIZE) {
                    return;
                }
                in.markReaderIndex();
                int dataLength = in.readInt();
                if (in.readableBytes() < dataLength) {
                    in.resetReaderIndex();
                    return;
                }
                byte[] data = new byte[dataLength];
                in.readBytes(data);

                ObjectInput objectInput = serializer.deserialize(new ByteArrayInputStream(data));
                out.add(objectInput.readObject(type));

            } catch (Exception e) {
                if (logger.isErrorEnabled()) {
                    logger.error("[opencron] NettyCodecAdapter decode error:", stackTrace(e));
                }
            }
        }
    }

}
