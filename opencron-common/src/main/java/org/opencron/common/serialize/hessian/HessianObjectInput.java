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
package org.opencron.common.serialize.hessian;

import com.caucho.hessian.io.Hessian2Input;
import org.opencron.common.serialize.ObjectInput;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/**
 * Hessian2 Object input.
 */
public class HessianObjectInput implements ObjectInput {

    private final Hessian2Input mH2i;

    public HessianObjectInput(InputStream is) {
        mH2i = new Hessian2Input(is);
        mH2i.setSerializerFactory(HessianSerializerFactory.SERIALIZER_FACTORY);
    }

    public boolean readBool() throws IOException {
        return mH2i.readBoolean();
    }

    public byte readByte() throws IOException {
        return (byte) mH2i.readInt();
    }

    public short readShort() throws IOException {
        return (short) mH2i.readInt();
    }

    public int readInt() throws IOException {
        return mH2i.readInt();
    }

    public long readLong() throws IOException {
        return mH2i.readLong();
    }

    public float readFloat() throws IOException {
        return (float) mH2i.readDouble();
    }

    public double readDouble() throws IOException {
        return mH2i.readDouble();
    }

    public byte[] readBytes() throws IOException {
        return mH2i.readBytes();
    }

    public String readUTF() throws IOException {
        return mH2i.readString();
    }

    public Object readObject() throws IOException {
        return mH2i.readObject();
    }

    @SuppressWarnings("unchecked")
    public <T> T readObject(Class<T> cls) throws IOException,
            ClassNotFoundException {
        return (T) mH2i.readObject(cls);
    }

    public <T> T readObject(Class<T> cls, Type type) throws IOException, ClassNotFoundException {
        return readObject(cls);
    }

}