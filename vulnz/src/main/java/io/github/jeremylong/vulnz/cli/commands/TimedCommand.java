/*
 *  Copyright 2022-2023 Jeremy Long
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.github.jeremylong.vulnz.cli.commands;

import com.diogonunes.jcolor.Attribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.diogonunes.jcolor.Ansi.colorize;

public abstract class TimedCommand extends AbstractHelpfulCommand {
    /**
     * Reference to the logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(TimedCommand.class);

    public abstract Integer timedCall() throws Exception;

    @Override
    public Integer call() throws Exception {
        final long startTime = System.currentTimeMillis();
        Integer results = timedCall();
        final long endTime = System.currentTimeMillis();
        final long duration = (endTime - startTime) / 1000;
        String msg = String.format("Completed in %s seconds", duration);
        LOG.info(colorize(msg, Attribute.DIM()));
        return results;
    }
}
