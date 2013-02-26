/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package com.thoughtworks.i0.container.grizzly.internal.tryus;

import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.tyrus.websockets.Connection;
import org.glassfish.tyrus.websockets.DataFrame;
import org.glassfish.tyrus.websockets.WebSocketResponse;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
class ConnectionImpl extends Connection {

    private final FilterChainContext ctx;
    private final HttpContent httpContent;
    private final org.glassfish.grizzly.Connection connection;


    public ConnectionImpl(final FilterChainContext ctx, final HttpContent httpContent) {
        this.ctx = ctx;
        this.connection = ctx.getConnection();
        this.httpContent = httpContent;
    }

    public ConnectionImpl(final org.glassfish.grizzly.Connection connection) {
        this.connection = connection;
        this.ctx = null;
        this.httpContent = null;
    }


    @Override
    @SuppressWarnings({"unchecked"})
    public Future<DataFrame> write(final DataFrame frame, final CompletionHandler completionHandler) {

        final WebSocketFilter.ResultFuture<DataFrame> localFuture = new WebSocketFilter.ResultFuture<DataFrame>();

        connection.write(frame, new EmptyCompletionHandler() {

            @Override
            public void completed(Object result) {
                if (completionHandler != null) {
                    completionHandler.completed(frame);
                }

                localFuture.result(frame);
            }

            @Override
            public void failed(Throwable throwable) {
                if (completionHandler != null) {
                    completionHandler.failed(throwable);
                }

                localFuture.failure(throwable);
            }
        }
        );

        return localFuture;
    }

    @Override
    public void write(WebSocketResponse response) {
        if (ctx == null) {
            throw new UnsupportedOperationException("not supported on client side");
        }

        final HttpResponsePacket responsePacket = ((HttpRequestPacket) httpContent.getHttpHeader()).getResponse();
        responsePacket.setProtocol(Protocol.HTTP_1_1);
        responsePacket.setStatus(response.getStatus());

        for (Map.Entry<String, String> entry : response.getHeaders().entrySet()) {
            responsePacket.setHeader(entry.getKey(), entry.getValue());
        }

        ctx.write(HttpContent.builder(responsePacket).build());
    }

    @Override
    public void addCloseListener(final CloseListener closeListener) {

        final org.glassfish.tyrus.websockets.Connection webSocketConnection = this;

        connection.addCloseListener(new org.glassfish.grizzly.Connection.CloseListener() {
            @Override
            public void onClosed(org.glassfish.grizzly.Connection connection, org.glassfish.grizzly.Connection.CloseType closeType) throws IOException {
                closeListener.onClose(webSocketConnection);
            }
        });
    }

    @Override
    public void closeSilently() {
        connection.closeSilently();
    }

    @Override
    public int hashCode() {
        return connection.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Connection && connection.equals(((Connection) obj).getUnderlyingConnection());
    }

    @Override
    public Object getUnderlyingConnection() {
        return connection;
    }

    public String toString() {
        return this.getClass().getName() + " " + connection.toString() + " " + connection.hashCode();
    }
}
