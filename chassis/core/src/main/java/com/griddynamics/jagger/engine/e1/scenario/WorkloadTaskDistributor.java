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

package com.griddynamics.jagger.engine.e1.scenario;

import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Service;
import com.griddynamics.jagger.coordinator.*;
import com.griddynamics.jagger.engine.e1.Provider;
import com.griddynamics.jagger.engine.e1.aggregator.session.model.TaskData;
import com.griddynamics.jagger.engine.e1.collector.TestListener;
import com.griddynamics.jagger.engine.e1.collector.test.TestInfoStart;
import com.griddynamics.jagger.engine.e1.collector.test.TestInfoStatus;
import com.griddynamics.jagger.engine.e1.collector.test.TestInfoStop;
import com.griddynamics.jagger.engine.e1.process.PollWorkloadProcessStatus;
import com.griddynamics.jagger.engine.e1.process.StartWorkloadProcess;
import com.griddynamics.jagger.engine.e1.process.StopWorkloadProcess;
import com.griddynamics.jagger.master.AbstractDistributionService;
import com.griddynamics.jagger.master.AbstractDistributor;
import com.griddynamics.jagger.master.TaskExecutionStatusProvider;
import com.griddynamics.jagger.util.Injector;
import com.griddynamics.jagger.util.TimeUtils;
import com.griddynamics.jagger.util.TimeoutsConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import static com.griddynamics.jagger.util.TimeUtils.sleepMillis;

public class WorkloadTaskDistributor extends AbstractDistributor<WorkloadTask> {
    private static Logger log = LoggerFactory.getLogger(WorkloadTaskDistributor.class);

    private TimeoutsConfiguration timeoutsConfiguration;

    private TaskExecutionStatusProvider taskExecutionStatusProvider;

    private long logInterval;

    @Override
    public Set<Qualifier<?>> getQualifiers() {
        Set<Qualifier<?>> result = Sets.newHashSet();

        result.add(Qualifier.of(StartWorkloadProcess.class));
        result.add(Qualifier.of(StopWorkloadProcess.class));
        result.add(Qualifier.of(PollWorkloadProcessStatus.class));

        return result;
    }

    public void setTaskExecutionStatusProvider(TaskExecutionStatusProvider taskExecutionStatusProvider) {
        this.taskExecutionStatusProvider = taskExecutionStatusProvider;
    }

    @Required
    public void setTimeoutsConfiguration(TimeoutsConfiguration timeoutsConfiguration) {
        this.timeoutsConfiguration = timeoutsConfiguration;
    }

    @Override
    protected Service performDistribution(final ExecutorService executor, final String sessionId, final String taskId, final WorkloadTask task,
                                          final Map<NodeId, RemoteExecutor> remotes, final Multimap<NodeType, NodeId> availableNodes,
                                          final Coordinator coordinator, final NodeContext nodeContext) {

        return new AbstractDistributionService(executor) {
            @Override
            protected void run() throws Exception {
                //create test-listeners
                ArrayList<TestListener> listeners = new ArrayList<TestListener>(task.getTestListeners().size());
                for (Provider<TestListener> provider : task.getTestListeners()){
                    Injector.injectNodeContext(provider, sessionId, taskId, nodeContext);
                    listeners.add(provider.provide());
                }
                TestListener testListener = TestListener.Composer.compose(listeners);
                Long startTime = null;

                DefaultWorkloadController controller = null;
                try {
                    // create start status
                    TestInfoStart infoStart = new TestInfoStart();
                    infoStart.setTask(task);

                    // launch start actions
                    testListener.onStart(infoStart);

                    String line = " ---------------------------------------------------------------------------------------------------------------------------------------------------\n";
                    String report = "\n\n" + line + "S T A R T     W O R K L O A D\n" + line + "\n";
                    log.info(report);
                    log.info("Going to distribute workload task {}", task);

                    log.debug("Going to do calibration");
                    Calibrator calibrator = task.getCalibrator();
                    calibrator.calibrate(sessionId, taskId, task.getScenarioFactory(), remotes, timeoutsConfiguration.getCalibrationTimeout().getValue());
                    log.debug("Calibrator completed");

                    if (task.getStartDelay() > 0) {
                        log.info("Going to sleep '{}' ms before execute task: {}", task.getStartDelay(), task.getName());
                        TimeUtils.sleepMillis(task.getStartDelay());
                        log.info("Start execution of task: {}", task);
                    }

                    startTime = System.currentTimeMillis() ;
                    controller = new DefaultWorkloadController(sessionId, taskId, task, remotes, timeoutsConfiguration, startTime);

                    WorkloadClock clock = task.getClock();
                    TerminationStrategy terminationStrategy = task.getTerminationStrategy();

                    log.debug("Going to start workload");
                    controller.startWorkload(clock.getPoolSizes(controller.getNodes()));
                    log.debug("Workload started");

                    int sleepInterval = clock.getTickInterval();
                    long multiplicity = logInterval / sleepInterval;
                    long countIntervals = 0;

                    while (true) {
                        if (!isRunning()) {
                            log.info("Going to terminate work {}. Requested from outside",task.getName());
                            break;
                        }

                        WorkloadExecutionStatus status = controller.getStatus();

                        // create tick status
                        TestInfoStatus tickInfo = new TestInfoStatus();
                        tickInfo.setTask(task);
                        tickInfo.setSamples(status.getTotalSamples());
                        tickInfo.setStartedSamples(status.getTotalStartedSamples());
                        tickInfo.setThreads(status.getTotalThreads());

                        testListener.onTick(tickInfo);

                        if (terminationStrategy.isTerminationRequired(status)) {
                            report = "\n\n" + line + "S T O P     W O R K L O A D\n" + line + "\n";
                            log.info(report);
                            log.info("Going to terminate work {}. According to termination strategy",task.getName());
                            break;
                        }

                        clock.tick(status, controller);
                        if (--countIntervals <= 0) {
                            log.info("Status of execution {}", status);
                            countIntervals = multiplicity;
                        }

                        log.debug("Clock should continue. Going to sleep {} seconds", sleepInterval);
                        sleepMillis(sleepInterval);
                    }

                    taskExecutionStatusProvider.setStatus(taskId, TaskData.ExecutionStatus.SUCCEEDED);
                } catch (Exception e) {
                    taskExecutionStatusProvider.setStatus(taskId, TaskData.ExecutionStatus.FAILED);
                    log.error("Workload task error: ", e);
                }
                finally {
                    if(controller != null) {
                        log.debug("Going to stop workload");
                        controller.stopWorkload();
                        log.debug("Workload stopped");
                    }

                    // create stop info
                    TestInfoStop infoStop = new TestInfoStop();
                    infoStop.setTask(task);
                    if (startTime == null){
                        infoStop.setDuration(0L);
                    }else{
                        infoStop.setDuration(System.currentTimeMillis() - startTime);
                    }

                    testListener.onStop(infoStop);
                }
            }

            @Override
            public String toString() {
                return WorkloadTask.class.getName() + " distributor";
            }
        };
    }

    public void setLogInterval(long logInterval) {
        this.logInterval = logInterval;
    }

}
