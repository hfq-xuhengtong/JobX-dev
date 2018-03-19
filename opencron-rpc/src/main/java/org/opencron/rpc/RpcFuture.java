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
package org.opencron.rpc;

import org.opencron.common.job.Request;
import org.opencron.common.job.Response;

import java.io.IOException;
import java.util.concurrent.*;

/**
 * @author benjobs
 */
public class RpcFuture {

    private static final CancellationException CANCELLED = new CancellationException();
    private volatile boolean haveResult;
    private volatile Integer futureId;
    private volatile Request request;
    private volatile Response response;
    private volatile Throwable cause;
    private CountDownLatch latch;
    private final long beginTime = System.currentTimeMillis();
    private TimeUnit unit;
    private InvokeCallback callback;

    public RpcFuture() {
    }

    public RpcFuture(Request request) {
        this.request = request;
        this.futureId = request.getId();
        this.unit = TimeUnit.SECONDS;
    }

    public RpcFuture(Request request, InvokeCallback callback) {
        this(request);
        this.callback = callback;
    }

    public void invokeCallback() {
        if (isDone()) {
            if (this.cause != null) {
                this.callback.failure(this.cause);
            } else {
                this.callback.success(this.response);
            }
        }
    }

    public boolean isAsync() {
        return this.callback != null;
    }

    public boolean isCancelled() {
        return this.cause == CANCELLED;
    }

    public boolean isDone() {
        return this.haveResult;
    }

    public void done(Response response) {
        synchronized (this) {
            if (!this.haveResult) {
                this.response = response;
                this.haveResult = true;
                if (this.latch != null) {
                    this.latch.countDown();
                }
            }
        }
    }

    public void failure(Throwable throwable) {
        if (!(throwable instanceof IOException) && !(throwable instanceof SecurityException)) {
            throwable = new IOException(throwable);
        }
        synchronized (this) {
            if (!this.haveResult) {
                this.cause = throwable;
                this.haveResult = true;
                if (this.latch != null) {
                    this.latch.countDown();
                }
                this.getCallback().failure(throwable);
            }
        }
    }

    public Response get() throws InterruptedException, ExecutionException {
        if (!this.haveResult) {
            boolean wait = this.prepareForWait();
            if (wait) {
                this.latch.await();
            }
        }
        return returnResult();
    }

    public Response get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (!this.haveResult) {
            boolean wait = this.prepareForWait();
            if (wait && !this.latch.await(timeout, unit)) {
                throw new TimeoutException();
            }
        }
        return returnResult();
    }

    private Response returnResult() throws ExecutionException {
        if (this.cause != null) {
            if (this.cause == CANCELLED) {
                throw new CancellationException();
            } else {
                throw new ExecutionException(this.cause);
            }
        } else {
            return this.response;
        }
    }

    private boolean prepareForWait() {
        synchronized (this) {
            if (this.haveResult) {
                return false;
            } else {
                if (this.latch == null) {
                    this.latch = new CountDownLatch(1);
                }
                return true;
            }
        }
    }

    public InvokeCallback getCallback() {
        return callback;
    }

    public long getBeginTime() {
        return beginTime;
    }

    public long getTimeoutMillis() {
        return unit.toMillis(this.request.getTimeOut());
    }

    public Integer getFutureId() {
        return futureId;
    }

    public void setFutureId(Integer futureId) {
        this.futureId = futureId;
    }

    public Request getRequest() {
        return request;
    }

    public void setRequest(Request request) {
        this.request = request;
    }
}