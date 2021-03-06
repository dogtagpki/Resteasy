/**
 * Copyright 2019 Red Hat, Inc, and individual contributors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.resteasy.microprofile.client.header;

import static org.jboss.resteasy.microprofile.client.utils.ListCastUtils.castToListOfStrings;

import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;
import org.jboss.resteasy.microprofile.client.impl.MpClientInvocation;
import org.jboss.resteasy.microprofile.client.utils.ClientRequestContextUtils;

import javax.annotation.Priority;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


/**
 * First the headers from `@ClientHeaderParam` annotations are applied,
 * they can be overwritten by JAX-RS `@HeaderParam` (coming in the `requestContext`)
 *
 * Then, if a `ClientHeadersFactory` is defined, all the headers, together with incoming container headers,
 * are passed to it and it can overwrite them.
 */
@Priority(Integer.MIN_VALUE)
public class ClientHeadersRequestFilter implements ClientRequestFilter {

    private static final MultivaluedMap<String, String> EMPTY_MAP = new MultivaluedHashMap<>();

    @Override
    public void filter(ClientRequestContext requestContext) {
        Method method = ClientRequestContextUtils.getMethod(requestContext);

        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();

        Optional<ClientHeaderProvider> handler = ClientHeaderProviders.getProvider(method);
        handler.ifPresent(h -> h.addHeaders(headers));

        Optional<ClientHeadersFactory> factory = ClientHeaderProviders.getFactory(method.getDeclaringClass());

        requestContext.getHeaders().forEach(
                (key, values) -> headers.put(key, castToListOfStrings(values))
        );

        MultivaluedMap<String,String> containerHeaders = (MultivaluedMap<String, String>) requestContext.getProperty(MpClientInvocation.CONTAINER_HEADERS);
        if(containerHeaders == null)
            containerHeaders = EMPTY_MAP;
        // stupid final rules
        MultivaluedMap<String,String> incomingHeaders = containerHeaders;

        factory.map(f -> f.update(incomingHeaders, headers))
                .orElse(headers)
                .forEach(
                        (key, values) -> requestContext.getHeaders().put(key, castToListOfObjects(values))
                );
    }

    private static List<Object> castToListOfObjects(List<String> values) {
        return new ArrayList<>(values);
    }
}