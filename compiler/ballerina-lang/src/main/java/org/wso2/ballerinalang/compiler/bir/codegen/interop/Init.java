/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.ballerinalang.compiler.bir.codegen.interop;

import org.ballerinalang.jvm.scheduling.Strand;
import org.ballerinalang.jvm.values.ArrayValue;
import org.ballerinalang.jvm.values.ObjectValue;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;

import static org.wso2.ballerinalang.compiler.bir.codegen.interop.JInterop.CLASS_LOADER_DATA;

/**
 * Native class for jvm interop validator creation.
 */
public class Init {

    public static void init(Strand strand, ObjectValue interopValidatorStruct, ArrayValue jarUrls,
                            boolean useSystemClassLoader) {

        try {
            String[] moduleDependencySet = jarUrls.getStringArray();
            URL[] urls = new URL[moduleDependencySet.length];
            int i = 0;
            for (String jarPath : moduleDependencySet) {
                urls[i] = Paths.get(jarPath).toUri().toURL();
                i++;
            }
            AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                if (useSystemClassLoader) {
                    interopValidatorStruct.addNativeData(CLASS_LOADER_DATA, new URLClassLoader(urls));
                } else {
                    interopValidatorStruct.addNativeData(CLASS_LOADER_DATA, new URLClassLoader(urls, null));
                }
                return null;
            });
        } catch (MalformedURLException e) {
            throw new JInteropException(JInteropException.CLASS_LOADER_INIT_FAILED_REASON,
                                        e.getMessage());
        }
    }
}

