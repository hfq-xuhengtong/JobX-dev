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

import com.jobxhub.common.ext.ExtensionLoader;
import com.jobxhub.common.job.Request;
import com.jobxhub.common.job.Response;
import com.jobxhub.common.job.RpcType;
import com.jobxhub.rpc.Client;
import com.jobxhub.rpc.InvokeCallback;
import com.jobxhub.rpc.Invoker;


/**
 * @author <a href="mailto:benjobs@qq.com">B e n</a>
 * @version 1.0
 * @date 2016-03-27
 */

public abstract class AbstractInvoker implements Invoker {

    @Override
    public Response sentSync(Request request) {
        try {
            return getClient().sentSync(request.setRpcType(RpcType.SYNC));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void sentOneWay(Request request) {
        try {
            getClient().sentOneWay(request.setRpcType(RpcType.ONE_WAY));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void sentAsync(Request request, InvokeCallback callback) {
        try {
            getClient().sentAsync(request.setRpcType(RpcType.ASYNC), callback);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Client getClient(){
        return ExtensionLoader.load(Client.class);
    }

}
