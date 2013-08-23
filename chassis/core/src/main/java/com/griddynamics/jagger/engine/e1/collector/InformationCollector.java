/*
 * Copyright (c) 2010-2012 Grid Dynamics Consulting Services, Inc, All Rights Reserved
 * http://www.griddynamics.com
 *
 * This library is free software; you can redistribute it and/or modify it under the terms of
 * the GNU Lesser General Public License as published by the Free Software Foundation; either
 * version 2.1 of the License, or any later version.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.griddynamics.jagger.engine.e1.collector;

import com.griddynamics.jagger.coordinator.NodeContext;
import com.griddynamics.jagger.engine.e1.scenario.ScenarioCollector;
import com.griddynamics.jagger.invoker.InvocationException;
import com.griddynamics.jagger.storage.KeyValueStorage;
import com.griddynamics.jagger.storage.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.griddynamics.jagger.engine.e1.collector.CollectorConstants.*;

/**
 * Collects number of failures and total invocations count.
 *
 * @author Mairbek Khadikov
 */
public class InformationCollector extends ScenarioCollector {
    private static final Logger log = LoggerFactory.getLogger(InformationCollector.class);

    private List<Validator> validators;

    private Integer invoked = 0;
    private Integer failed = 0;

    public InformationCollector(String sessionId, String taskId, NodeContext kernelContext, List<Validator> validators) {
        super(sessionId, taskId, kernelContext);
        this.validators = validators;
    }

    private Namespace namespace() {
        return Namespace.of(sessionId, taskId, "InformationCollector",
                kernelContext.getId().toString());
    }

    @Override
    public void flush() {

        //flush validators
        for (Validator validator : validators){
            validator.flush();
        }

        log.debug("Going to store invoked/failed in key-value storage");

        Namespace namespace = namespace();

        KeyValueStorage keyValueStorage = kernelContext.getService(KeyValueStorage.class);

        keyValueStorage.put(namespace, INVOKED, invoked);
        keyValueStorage.put(namespace, FAILED, failed);

        log.debug("invoked {} failed {}", invoked, failed);
    }

    @Override
    public void onStart(Object query, Object endpoint) {
        invoked++;
    }

    @Override
    public void onSuccess(Object query, Object endpoint, Object result, long duration) {
        boolean success = true;
        for (Validator validator : validators){
            if (!validator.validate(query, endpoint, result, duration)){
                success = false;
                break;
            }
        }
        if (!success){
            failed++;
        }
    }

    @Override
    public void onFail(Object query, Object endpoint, InvocationException e) {
        failed++;
    }

    @Override
    public void onError(Object query, Object endpoint, Throwable error) {
        failed++;
    }

    private static final long serialVersionUID = 2860131810406271911L;
}
