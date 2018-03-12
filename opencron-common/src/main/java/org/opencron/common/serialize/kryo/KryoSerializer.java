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
package org.opencron.common.serialize.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.opencron.common.serialize.Serializer;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author benjobs
 */

public class KryoSerializer implements Serializer {


    private static final ThreadLocal<Kryo> KryoHolder = new ThreadLocal<Kryo>() {
        @Override
        protected Kryo initialValue() {
            Kryo kryo = new Kryo();
            kryo.setReferences(false);
            kryo.setInstantiatorStrategy(new Kryo.DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
            return kryo;
        }
    };

    @Override
    public byte[] encode(Object obj) throws IOException {
        ByteArrayOutputStream byteOut = null;
        Output output = null;
        try {
            byteOut = new ByteArrayOutputStream();
            output = new Output(byteOut);
            Kryo kryo = KryoHolder.get();
            kryo.register(obj.getClass());
            kryo.writeObject(output, obj);
            return output.toBytes();
        } finally {
            if (null != byteOut) {
                try {
                    byteOut.close();
                    byteOut = null;
                } catch (IOException e) {
                }
            }
            if (null != output) {
                output.close();
                output = null;
            }
        }
    }

    @Override
    public <T> T decode(byte[] bytes, Class<T> type) throws IOException {
        ByteArrayInputStream inputStream = null;
        Input input = null;
        try {
            inputStream =  new ByteArrayInputStream(bytes);
            input = new Input(inputStream);
            Kryo kryo = KryoHolder.get();
            kryo.register(type);
            return kryo.readObject(input,type);
        } finally {
            if (null != inputStream) {
                inputStream.close();
                inputStream = null;
            }
            if (null != input) {
                input.close();
                input = null;
            }
        }
    }

}
