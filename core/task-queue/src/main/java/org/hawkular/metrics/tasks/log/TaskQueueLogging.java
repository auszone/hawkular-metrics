/*
 * Copyright 2014-2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.metrics.tasks.log;

import org.jboss.logging.Logger;

/**
 * Simplify logger lookup.
 *
 * @author Thomas Segismont
 */
public class TaskQueueLogging {

    public static TaskQueueLogger getTaskQueueLogger(Class<?> clazz) {
        return Logger.getMessageLogger(TaskQueueLogger.class, clazz.getName());
    }

    private TaskQueueLogging() {
        // Utility class
    }
}
