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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import picocli.CommandLine;

import java.util.concurrent.Callable;

public abstract class AbstractHelpfulCommand implements Callable<Integer> {
    @CommandLine.Option(names = {"-h",
            "--help"}, usageHelp = true, description = "Displays information describing the command options")
    @SuppressFBWarnings("URF_UNREAD_FIELD")
    private boolean helpRequested = false;
    @CommandLine.Option(names = {"--debug"}, description = "Enable debug output")
    private boolean debug = false;

    protected boolean isDebug() {
        return debug;
    }
}
