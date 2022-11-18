/*
 *  Copyright 2022 Jeremy Long
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.jeremylong.nvdlib.nvd.CveApiJson20;
import io.github.jeremylong.nvdlib.nvd.DefCveItem;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * A simple client for the NVD CVE API. Use the NvdCveApiBuilder with the desired filters to build the client and then
 * iterate over the results:
 *
 * <pre>
 * try (NvdCveApi api = NvdCveApiBuilder.aNvdCveApi().build()) {
 *     while (api.hasNext()) {
 *         Collection<DefCveItem> items = api.next();
 *     }
 * }
 * </pre>
 *
 * @author Jeremy Long
 * @see <a href="https://nvd.nist.gov/developers/vulnerabilities">NVD CVE API</a>
 */
public class NvdCveApi implements AutoCloseable, Iterator<Collection<DefCveItem>> {

    /**
     * Reference to the logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(NvdCveApi.class);
    /**
     * The default endpoint for the NVD CVE API.
     */
    private final static String DEFAULT_ENDPOINT = "https://services.nvd.nist.gov/rest/json/cves/2.0";
    /**
     * The header name for the NVD API Key.
     */
    private static final String API_KEY_NAME = "apiKey";
    /**
     * The NVD API key; can be null if a key is not used.
     */
    private final String apiKey;
    /**
     * The NVD API endpoint used to call the NVD CVE API.
     */
    private final String endpoint;
    /**
     * Jackson object mapper.
     */
    private final ObjectMapper objectMapper;
    /**
     * The rate limited HTTP client for calling the NVD APIs.
     */
    private RateLimitedClient client;
    /**
     * The index to obtain the paged results from the NVD CVE.
     */
    private int index = 0;
    /**
     * The number of results per page.
     */
    private int resultsPerPage = 2000;
    /**
     * The total results from the NVD CVE API call.
     */
    private int totalResults = -1;
    /**
     * The epoch time of the last modified request date (i.e., the UTC epoch of the last update of the entire NVD CVE
     * Data Set).
     */
    private long lastModifiedRequest = 0;
    /**
     * A list of filters to apply to the request.
     */
    private List<NameValuePair> filters;
    /**
     * The asynch future for the HTTP responses.
     */
    private Future<SimpleHttpResponse> future;
    /**
     * The last HTTP Status Code returned by the API.
     */
    private int lastStatusCode = 200;

    /**
     * Constructs a new NVD CVE API client.
     *
     * @param apiKey the api key; can be null
     * @param endpoint the endpoint for the NVD CVE API; if null the default endpoint is used
     */
    NvdCveApi(String apiKey, String endpoint) {
        this(apiKey, endpoint, apiKey == null ? 6500 : 600);
    }

    /**
     * Constructs a new NVD CVE API client.
     *
     * @param apiKey the api key; can be null
     * @param endpoint the endpoint for the NVD CVE API; if null the default endpoint is used
     */
    NvdCveApi(String apiKey, String endpoint, long delay) {
        this.apiKey = apiKey;
        if (endpoint == null) {
            this.endpoint = DEFAULT_ENDPOINT;
        } else {
            this.endpoint = endpoint;
        }
        if (apiKey == null) {
            client = new RateLimitedClient(delay, 5, 32500);
        } else {
            client = new RateLimitedClient(delay, 50, 32500);
        }
        objectMapper = new ObjectMapper();
        // objectMapper.findAndRegisterModules();
        objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Gets the UTC epoch of the last request. Used in subsequent calls to the API with the
     * {@link NvdCveApiBuilder#withLastModifiedFilter NvdCveApiBuilder#withLastModifiedFilter()}.
     *
     * @return the last modified datetime
     */
    public long getLastModifiedRequest() {
        return lastModifiedRequest;
    }

    /**
     * Set the filter parameters for the NVD CVE API calls.
     *
     * @param filters the list of parameters used to filter the results in the API call
     */
    void setFilters(List<NameValuePair> filters) {
        this.filters = filters;
    }

    /**
     * The number of results per page; the default is 2000.
     *
     * @param resultsPerPage the number of results per page
     */
    void setResultsPerPage(int resultsPerPage) {
        this.resultsPerPage = resultsPerPage;
    }

    /**
     * Returns the last HTTP Status Code.
     *
     * @return the last HTTP Status Code
     */
    public int getLastStatusCode() {
        return lastStatusCode;
    }

    /**
     * Asynchronously calls the NVD CVE API.
     *
     * @throws NvdApiException thrown if there is a problem calling the API
     */
    private void callApi() throws NvdApiException {
        try {
            URIBuilder uriBuilder = new URIBuilder(endpoint);
            if (filters != null) {
                uriBuilder.addParameters(filters);
            }
            uriBuilder.addParameter("resultsPerPage", Integer.toString(resultsPerPage));
            uriBuilder.addParameter("startIndex", Integer.toString(index));
            final SimpleRequestBuilder builder = SimpleRequestBuilder.get();
            if (apiKey != null) {
                builder.addHeader(API_KEY_NAME, apiKey);
            }
            URI uri = uriBuilder.build();
            if (LOG.isDebugEnabled()) {
                LocalTime time = LocalTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
                LOG.debug("Requested At: {}; URI: {}", time.format(formatter), uri);
            }
            final SimpleHttpRequest request = builder.setUri(uri).build();
            future = client.execute(request);
        } catch (URISyntaxException e) {
            throw new NvdApiException(e);
        }
    }

    /**
     * Resets the index and last status code so that the previous API call can be re-requested.
     */
    public void resetLastCall() {
        lastStatusCode = 200;
        index -= resultsPerPage;
        if (index < 0) {
            index = 0;
        }
    }

    @Override
    public void close() throws Exception {
        client.close();
        client = null;
    }

    @Override
    public boolean hasNext() {
        if (lastStatusCode != 200) {
            return false;
        }
        if (future == null || totalResults < 0) {
            return true;
        }
        return index < totalResults;
    }

    @Override
    public Collection<DefCveItem> next() {
        if (future == null) {
            callApi();
        }
        String json = "";
        try {
            SimpleHttpResponse response = future.get();
            if (response.getCode() == 200) {
                index += resultsPerPage;
                json = response.getBodyText();
                LOG.debug("Conent-Type Received: {}", response.getContentType());
                CveApiJson20 current = objectMapper.readValue(json, CveApiJson20.class);
                this.totalResults = current.getTotalResults();
                this.lastModifiedRequest = current.getTimestamp().toEpochSecond(ZoneOffset.UTC);
                if (index < this.totalResults) {
                    callApi();
                }
                return current.getVulnerabilities();
            } else {
                lastStatusCode = response.getCode();
                LOG.debug("Status Code: {}", lastStatusCode);
                LOG.debug("Response: {}", response.getBodyText());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NvdApiException(e);
        } catch (ExecutionException e) {
            throw new NvdApiException(e);
        } catch (JsonProcessingException e) {
            throw new NvdApiException(e);
        }
        return null;
    }
}
