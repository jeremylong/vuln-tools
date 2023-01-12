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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.diogonunes.jcolor.Attribute;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.jeremylong.vulnz.cli.model.CveOutput;
import io.github.jeremylong.nvdlib.NvdCveApi;
import io.github.jeremylong.nvdlib.NvdCveApiBuilder;
import io.github.jeremylong.nvdlib.nvd.DefCveItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;

import static com.diogonunes.jcolor.Ansi.colorize;

@Component
@CommandLine.Command(name = "cve", description = "Client for the NVD Vulnerability API")
public class CveCommand extends AbstractNvdCommand {
    /**
     * Reference to the logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(CveCommand.class);
    @CommandLine.ArgGroup(exclusive = false)
    ModifiedRange modifiedRange;
    @CommandLine.ArgGroup(exclusive = false)
    PublishedRange publishedRange;
    @CommandLine.Option(names = {"--cpeName"}, description = "")
    private String cpeName;
    @CommandLine.Option(names = {"--cveId"}, description = "The CVE ID")
    private String cveId;
    @CommandLine.Option(names = {"--cvssV2Metrics"}, description = "")
    private String cvssV2Metrics;
    @CommandLine.Option(names = {"--cvssV3Metrics"}, description = "")
    private String cvssV3Metrics;
    @CommandLine.Option(names = {"--keywordExactMatch"}, description = "")
    private String keywordExactMatch;
    @CommandLine.Option(names = {"--keywordSearch"}, description = "")
    private String keywordSearch;
    @CommandLine.Option(names = {"--hasCertAlerts"}, description = "")
    private boolean hasCertAlerts;
    @CommandLine.Option(names = {"--hasCertNotes"}, description = "")
    private boolean hasCertNotes;
    @CommandLine.Option(names = {"--hasKev"}, description = "")
    private boolean hasKev;
    @CommandLine.Option(names = {"--hasOval"}, description = "")
    private boolean hasOval;
    @CommandLine.Option(names = {"--isVulnerable"}, description = "")
    private boolean isVulnerable;
    @CommandLine.Option(names = {"--cvssV2Severity"}, description = "")
    private NvdCveApiBuilder.CvssV2Severity cvssV2Severity;
    @CommandLine.Option(names = {"--cvssV3Severity"}, description = "")
    private NvdCveApiBuilder.CvssV3Severity cvssV3Severity;

    @Override
    public Integer timedCall() throws Exception {
        if (isDebug()) {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            loggerContext.getLogger("io.github.jeremylong").setLevel(Level.DEBUG);
        }
        NvdCveApiBuilder builder = NvdCveApiBuilder.aNvdCveApi().withApiKey(getApiKey());
        if (getDelay() > 0) {
            builder.withDelay(getDelay());
        }
        if (cveId != null) {
            builder.withFilter(NvdCveApiBuilder.Filter.CVE_ID, cveId);
        }
        if (cpeName != null) {
            builder.withFilter(NvdCveApiBuilder.Filter.CPE_NAME, cpeName);
        }
        if (cvssV2Metrics != null) {
            builder.withFilter(NvdCveApiBuilder.Filter.CVSS_V2_METRICS, cvssV2Metrics);
        }
        if (cvssV3Metrics != null) {
            builder.withFilter(NvdCveApiBuilder.Filter.CVSS_V3_METRICS, cvssV3Metrics);
        }
        if (keywordExactMatch != null) {
            builder.withFilter(NvdCveApiBuilder.Filter.KEYWORD_EXACT_MATCH, keywordExactMatch);
        }
        if (keywordSearch != null) {
            builder.withFilter(NvdCveApiBuilder.Filter.KEYWORD_SEARCH, keywordSearch);
        }
        if (hasCertAlerts) {
            builder.withFilter(NvdCveApiBuilder.BooleanFilter.HAS_CERT_ALERTS);
        }
        if (hasCertNotes) {
            builder.withFilter(NvdCveApiBuilder.BooleanFilter.HAS_CERT_NOTES);
        }
        if (hasKev) {
            builder.withFilter(NvdCveApiBuilder.BooleanFilter.HAS_KEV);
        }
        if (hasOval) {
            builder.withFilter(NvdCveApiBuilder.BooleanFilter.HAS_OVAL);
        }
        if (isVulnerable) {
            builder.withFilter(NvdCveApiBuilder.BooleanFilter.IS_VULNERABLE);
        }
        if (cvssV2Severity != null) {
            builder.withCvssV2SeverityFilter(cvssV2Severity);
        }
        if (cvssV3Severity != null) {
            builder.withCvssV3SeverityFilter(cvssV3Severity);
        }
        if (publishedRange != null && publishedRange.pubStartDate != null && publishedRange.pubEndDate != null) {
            builder.withPublishedDateFilter(publishedRange.pubStartDate, publishedRange.pubEndDate);
        }
        if (modifiedRange != null && modifiedRange.lastModStartDate != null) {
            LocalDateTime end = modifiedRange.lastModEndDate;
            if (end == null) {
                end = modifiedRange.lastModStartDate.minusDays(-120);
            }
            builder.withLastModifiedFilter(modifiedRange.lastModStartDate, end);
        }
        int recordCount = getRecordsPerPage();
        if (recordCount > 0 && recordCount <= 2000) {
            builder.withResultsPerPage(recordCount);
        }
        if (getPageCount() > 0) {
            builder.withMaxPageCount(getPageCount());
        }
        if (getThreads() > 0) {
            builder.withThreadCount(getThreads());
        }
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        JsonFactory jfactory = objectMapper.getFactory();
        // JsonFactory jfactory = new JsonFactory();
        JsonGenerator jsonOut = jfactory.createGenerator(System.out, JsonEncoding.UTF8);
        if (isPrettyPrint()) {
            jsonOut.useDefaultPrettyPrinter();
        }

        jsonOut.writeStartObject();
        jsonOut.writeFieldName("cves");
        jsonOut.writeStartArray();
        CveOutput output = new CveOutput();
        try (NvdCveApi api = builder.build()) {
            while (api.hasNext()) {
                Collection<DefCveItem> list = api.next();
                if (list != null) {
                    output.setSuccess(true);
                    output.addCount(list.size());
                    for (DefCveItem c : list) {
                        jsonOut.writeObject(c.getCve());
                    }
                    if (output.getLastModifiedDate() == null || output.getLastModifiedDate()
                            .toEpochSecond(ZoneOffset.UTC) < api.getLastModifiedRequest()) {
                        output.setLastModifiedDate(api.getLastModifiedRequest());
                    }
                } else {
                    output.setSuccess(false);
                    output.setReason(String.format("Received HTTP Status Code: %s", api.getLastStatusCode()));
                }
            }
            jsonOut.writeEndArray();
            jsonOut.writeObjectField("results", output);
            jsonOut.writeEndObject();
            jsonOut.close();

            if (!output.isSuccess()) {
                String msg = String.format("%nFAILED: %s", output.getReason());
                LOG.info(colorize(msg, Attribute.RED_TEXT()));
                return 2;
            }
            LOG.info(colorize("\nSUCCESS", Attribute.GREEN_TEXT()));
            return 0;
        } catch (Exception ex) {
            LOG.error("\nERROR", ex);
        }
        return 1;
    }

    static class ModifiedRange {
        @CommandLine.Option(names = "--lastModStartDate", required = true, description = "")
        LocalDateTime lastModStartDate;
        @CommandLine.Option(names = "--lastModEndDate", description = "")
        LocalDateTime lastModEndDate;
    }

    static class PublishedRange {
        @CommandLine.Option(names = "--pubStartDate", required = true)
        LocalDateTime pubStartDate;
        @CommandLine.Option(names = "--pubEndDate", required = true)
        LocalDateTime pubEndDate;
    }
}
