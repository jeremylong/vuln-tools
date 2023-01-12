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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.File;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static com.diogonunes.jcolor.Ansi.colorize;

@Component
@CommandLine.Command(name = "install", description = "Used on mac or unix systems to create the vulnz symlink and add completion to the shell.")
public class InstallCommand extends AbstractHelpfulCommand {
    /**
     * Reference to the logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(InstallCommand.class);

    @Autowired
    private ResourceLoader resourceLoader;

    @Override
    @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
    public Integer call() throws Exception {

        final Path link;
        final File uLocalBin = new File("/usr/local/bin");
        if (uLocalBin.isDirectory()) {
            link = Paths.get(uLocalBin.getPath(), "vulnz");
        } else {
            link = Paths.get(".", "vulnz");
        }
        final File linkFile = link.toFile();
        if (linkFile.isFile() && !linkFile.delete()) {
            LOG.warn(colorize("Unable to delete existing link: " + link.toString(), Attribute.RED_TEXT()));
        }
        final String classResource = this.getClass().getResource(this.getClass().getSimpleName() + ".class").getPath();
        String bootJar = classResource.substring(0, classResource.indexOf("!/BOOT-INF/"));
        if (bootJar.startsWith("file:")) {
            bootJar = bootJar.substring(5);
        }
        final Path target = Paths.get(bootJar);

        Files.createSymbolicLink(link, target);

        LOG.info(colorize("vulnz link created: " + link.toString(), Attribute.GREEN_TEXT()));

        final Resource completion = resourceLoader.getResource("classpath:vulnz.completion.sh");
        if (completion.exists()) {
            final File zshlinux = new File("/etc/bash_completion.d");
            final File zshMac = new File("/usr/local/etc/bash_completion.d");

            final Path destination;
            if (zshlinux.isDirectory()) {
                destination = Paths.get(zshlinux.getPath(), "vulnz.completion.sh");
            } else if (zshMac.isDirectory()) {
                destination = Paths.get(zshMac.getPath(), "vulnz.completion.sh");
            } else {
                destination = Paths.get(".", "vulnz.completion.sh");
            }
            try (InputStream in = Channels.newInputStream(completion.readableChannel())) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            LOG.info(colorize("Created completion script: " + destination.toString(), Attribute.GREEN_TEXT()));
        } else {
            LOG.error("Unable to setup the completion file: {}", completion);
        }

        LOG.info(colorize("Setup complete", Attribute.GREEN_TEXT()));
        return 0;
    }
}
