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
package io.github.jeremylong.nvdlib;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Used to build an NVD CVE API client. As the NvdCveApi client is autoclosable the builder should be used in a try with
 * resources:
 *
 * <pre>
 * try (NvdCveApi api = NvdCveApiBuilder.aNvdCveApi().build()) {
 *     while (api.hasNext()) {
 *         Collection&lt;DefCveItem&gt; items = api.next();
 *     }
 * }
 * </pre>
 */
public final class NvdCveApiBuilder {

    /**
     * Reference to the logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(NvdCveApiBuilder.class);
    /**
     * A list of filters to apply to the request.
     */
    private final List<NameValuePair> filters = new ArrayList<>();
    /**
     * The NVD CVE API key.
     */
    private String apiKey;
    /**
     * The endpoint for the NVD CVE API.
     */
    private String endpoint;
    /**
     * The number of results per page.
     */
    private int resultsPerPage;
    /**
     * The minimum delay between API calls in milliseconds.
     */
    private long delay;
    /**
     * The number of threads to use when calling the NVD API.
     */
    private int threadCount = 1;
    /**
     * The maximum number of pages to retrieve from the NVD API.
     */
    private int maxPageCount = 0;

    /**
     * Private constructor for a builder.
     */
    private NvdCveApiBuilder() {
    }

    /**
     * Begin building the NVD CVE API Object.
     *
     * @return the builder
     */
    public static NvdCveApiBuilder aNvdCveApi() {
        return new NvdCveApiBuilder();
    }

    /**
     * Use an NVD CVE API key.
     *
     * @param apiKey the NVD CVE API key.
     * @return the builder
     * @see <a href="https://nvd.nist.gov/developers/request-an-api-key">NVD CVE API Request an API Key</a>
     */
    public NvdCveApiBuilder withApiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    /**
     * Use an alternative endpoint for the NVD CVE API.
     *
     * @param endpoint the endpoint for the NVD CVE API
     * @return the builder
     */
    public NvdCveApiBuilder withEndpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    /**
     * Use a minimum delay in milliseconds between API calls; useful if you run into issues with rate limiting.
     *
     * @param milliseconds the minimum number of milliseconds between API calls to the NVD CVE API
     * @return the builder
     */
    public NvdCveApiBuilder withDelay(long milliseconds) {
        this.delay = milliseconds;
        return this;
    }

    /**
     * Set the number of threads to use when calling the NVD API.
     *
     * @param count the number of threads to use when calling the NVD API
     * @return the builder
     */
    public NvdCveApiBuilder withThreadCount(int count) {
        this.threadCount = count;
        return this;
    }

    /**
     * Set the maximum number of pages to retrieve from the NVD API.
     *
     * @param count the maximum number of pages to retrieve from the NVD API
     * @return the builder
     */
    public NvdCveApiBuilder withMaxPageCount(int count) {
        this.maxPageCount = count;
        return this;
    }

    /**
     * Use a specific number of results per page. Value must be between 1 and 2000. The default value is 2000.
     *
     * @param resultsPerPage the number of results per page
     * @return the builder
     */
    public NvdCveApiBuilder withResultsPerPage(int resultsPerPage) {
        if (resultsPerPage > 0 && resultsPerPage <= 2000) {
            this.resultsPerPage = resultsPerPage;
        } else {
            LOG.warn("Invalid results per page - must be between 1 and 2000: {}", resultsPerPage);
        }
        return this;
    }

    /**
     * Add a querystring parameter to filter the call to the NVD CVE API.
     *
     * @param filter the querystring parameter
     * @param value the querystring parameter value
     * @return the builder
     */
    public NvdCveApiBuilder withFilter(String filter, String value) {
        filters.add(new BasicNameValuePair(filter, value));
        return this;
    }

    /**
     * Add a querystring parameter to filter the call to the NVD CVE API.
     *
     * @param filter the querystring parameter
     * @param value the querystring parameter value
     * @return the builder
     */
    public NvdCveApiBuilder withFilter(Filter filter, String value) {
        filters.add(new BasicNameValuePair(filter.toParameterName(), value));
        return this;
    }

    /**
     * Add a querystring parameter to filter the call to the NVD CVE API.
     *
     * @param filter the querystring parameter
     * @return the builder
     */
    public NvdCveApiBuilder withFilter(BooleanFilter filter) {
        filters.add(new BasicNameValuePair(filter.toParameterName(), null));
        return this;
    }

    /**
     * Use a range of no more than 120 days on the last modified dates to filter the results.
     *
     * @param utcStartDate the UTC date time for the range start
     * @param utcEndDate the UTC date time for the range end
     * @return the builder
     */
    public NvdCveApiBuilder withLastModifiedFilter(LocalDateTime utcStartDate, LocalDateTime utcEndDate) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssX");
        filters.add(new BasicNameValuePair("lastModStartDate", utcStartDate.atOffset(ZoneOffset.UTC).format(dtf)));
        filters.add(new BasicNameValuePair("lastModEndDate", utcEndDate.atOffset(ZoneOffset.UTC).format(dtf)));
        return this;
    }

    /**
     * Filter the results with a range of published date times.
     *
     * @param utcStartDate the UTC date time for the range start
     * @param utcEndDate the UTC date time for the range end
     * @return the builder
     */
    public NvdCveApiBuilder withPublishedDateFilter(LocalDateTime utcStartDate, LocalDateTime utcEndDate) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssX");
        filters.add(new BasicNameValuePair("pubStartDate", utcStartDate.atOffset(ZoneOffset.UTC).format(dtf)));
        filters.add(new BasicNameValuePair("pubEndDate", utcEndDate.atOffset(ZoneOffset.UTC).format(dtf)));
        return this;
    }

    /**
     * Filter the results for a specific CVSS V2 Severity.
     *
     * @param severity the severity
     * @return the builder
     */
    public NvdCveApiBuilder withCvssV2SeverityFilter(CvssV2Severity severity) {
        withFilter("cvssV2Severity", severity.toString());
        return this;
    }

    /**
     * Filter the results for a specific CVSS V3 Severity.
     *
     * @param severity the severity
     * @return the builder
     */
    public NvdCveApiBuilder withCvssV3SeverityFilter(CvssV3Severity severity) {
        withFilter("cvssV3Severity", severity.toString());
        return this;
    }

    /**
     * Build the NVD CVE API client.
     *
     * @return the NVD CVE API client
     */
    public NvdCveApi build() {
        NvdCveApi client;
        if (delay > 0) {
            client = new NvdCveApi(apiKey, endpoint, delay, threadCount, maxPageCount);
        } else {
            client = new NvdCveApi(apiKey, endpoint, threadCount, maxPageCount);
        }
        if (!filters.isEmpty()) {
            client.setFilters(filters);
        }
        if (resultsPerPage > 0) {
            client.setResultsPerPage(resultsPerPage);
        }
        return client;
    }

    /**
     * Parameters to the NVD CVE API used to filter the results.
     */
    public enum Filter {
        /**
         * Returns the vulnerabilties associated with a specific CPE.
         *
         * <pre>
         * cpeName=cpe:2.3:a:apache:log4j:2.0:*:*:*:*:*:*:*
         * </pre>
         *
         * @see <a href="https://nvd.nist.gov/developers/vulnerabilities#cves-cpeName">NVD CVE API</a>
         */
        CPE_NAME,
        /**
         * Returns a specific vulnerability.
         *
         * <pre>
         * cveId = CVE - 2021 - 44228
         * </pre>
         *
         * @see <a href="https://nvd.nist.gov/developers/vulnerabilities#cves-cveId">NVD CVE API</a>
         */
        CVE_ID,
        /**
         * Returns vulnerabilities that match a specific CVSS V2 Metric; full or partial vector strings may be used.
         *
         * <pre>
         * parameter: cvssV2Metrics=AV:L/AC:L/PR:L/UI:R/S:U/C:N/I:L/A:L
         * </pre>
         *
         * @see <a href="https://nvd.nist.gov/developers/vulnerabilities#cves-cvssV3Metrics">NVD CVE API</a>
         */
        CVSS_V2_METRICS,
        /**
         * Returns vulnerabilities that match a specific CVSS V3 Metric; full or partial vector strings may be used.
         *
         * <pre>
         * cvssV3Metrics=AV:L/AC:L/PR:L/UI:R/S:U/C:N/I:L/A:L
         * </pre>
         *
         * @see <a href="https://nvd.nist.gov/developers/vulnerabilities#cves-cvssV3Severity">NVD CVE API</a>
         */
        CVSS_V3_METRICS,
        /**
         * Returns vulnerabilities that have a specific CWE.
         *
         * <pre>
         * cweId = CWE - 287
         * </pre>
         *
         * @see <a href="https://nvd.nist.gov/developers/vulnerabilities#cves-cweId">NVD CVE API</a>
         */
        CWE_ID,
        /**
         * Returns vulnerabilities that have an exact key word sequence in the description.
         *
         * <pre>
         * keywordExactMatch=exact words
         * </pre>
         *
         * @see <a href="https://nvd.nist.gov/developers/vulnerabilities#cves-keywordExactMatch">NVD CVE API</a>
         */
        KEYWORD_EXACT_MATCH,
        /**
         * Returns vulnerabilities where all of the keywords are in the description.
         *
         * <pre>
         * keywordSearch = words
         * </pre>
         *
         * @see <a href="https://nvd.nist.gov/developers/vulnerabilities#cves-keywordSearch">NVD CVE API</a>
         */
        KEYWORD_SEARCH;

        /**
         * Returns the API querystring parameter.
         *
         * @return the API querystring parameter
         */
        public String toParameterName() {
            switch (this) {
                case CPE_NAME:
                    return "cpeName";
                case CVE_ID:
                    return "cveId";
                case CVSS_V2_METRICS:
                    return "cvssV2Metrics";
                case CVSS_V3_METRICS:
                    return "cvssV3Metrics";
                case CWE_ID:
                    return "cweId";
                case KEYWORD_EXACT_MATCH:
                    return "keywordExactMatch";
                case KEYWORD_SEARCH:
                    return "keywordSearch";
            }
            return "unknown";
        }
    }

    /**
     * Filters for the NVD CVE API that are used without parameters.
     */
    public enum BooleanFilter {
        /**
         * Returns vulnerabilities with have CERT alerts.
         *
         * <pre>
         * hasCertAlerts
         * </pre>
         *
         * @see <a href="https://nvd.nist.gov/developers/vulnerabilities#cves-hasCertAlerts">NVD CVE API</a>
         */
        HAS_CERT_ALERTS,
        /**
         * Returns vulnerabilities with have CERT notes.
         *
         * <pre>
         * hasCertNotes
         * </pre>
         *
         * @see <a href="https://nvd.nist.gov/developers/vulnerabilities#cves-hasCertNotes">NVD CVE API</a>
         */
        HAS_CERT_NOTES,
        /**
         * Returns vulnerabilities with Known Exploited Vulnerabilities information.
         *
         * <pre>
         * hasKev
         * </pre>
         *
         * @see <a href="https://nvd.nist.gov/developers/vulnerabilities#cves-hasKev">NVD CVE API</a>
         */
        HAS_KEV,
        /**
         * Returns vulnerabilities that have OVAL information.
         *
         * <pre>
         * hasOval
         * </pre>
         *
         * @see <a href="https://nvd.nist.gov/developers/vulnerabilities#cves-hasOval">NVD CVE API</a>
         */
        HAS_OVAL,
        /**
         * Used in conjunction with the CPE Search and returns only those considered vulnerable.
         *
         * <pre>
         * isVulnerable
         * </pre>
         *
         * @see <a href="https://nvd.nist.gov/developers/vulnerabilities#ccves-isVulnerable">NVD CVE API</a>
         */
        IS_VULNERABLE;

        /**
         * Returns the API querystring parameter.
         *
         * @return the API querystring parameter
         */
        public String toParameterName() {
            switch (this) {
                case HAS_CERT_ALERTS:
                    return "hasCertAlerts";
                case HAS_CERT_NOTES:
                    return "hasCertNotes";
                case HAS_KEV:
                    return "hasKev";
                case HAS_OVAL:
                    return "hasOval";
                case IS_VULNERABLE:
                    return "isVulnerable";
            }
            return "unknown";
        }
    }

    /**
     * The CVSS V2 Severity.
     */
    public enum CvssV2Severity {
        LOW, MEDIUM, HIGH
    }

    /**
     * The CVSS V3 Severity.
     */
    public enum CvssV3Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
