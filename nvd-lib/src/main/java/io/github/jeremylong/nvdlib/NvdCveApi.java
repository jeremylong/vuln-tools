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
import java.time.ZoneOffset;
import java.util.ArrayList;
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
 *         Collection&lt;DefCveItem&gt; items = api.next();
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
     * The rate meter to limit traffic to the NVD API.
     */
    private RateMeter meter;
    /**
     * The rate limited HTTP client for calling the NVD APIs.
     */
    private List<RateLimitedClient> clients;
    /**
     * The list of future responses.
     */
    private List<Future<SimpleHttpResponse>> futures = new ArrayList<>();
    /**
     * Flag indicating if the first call has been made.
     */
    private boolean firstCall = true;
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
     * The maximum number of pages to retrieve from the NVD API.
     */
    private int maxPageCount;
    /**
     * A list of filters to apply to the request.
     */
    private List<NameValuePair> filters;

    /**
     * The last HTTP Status Code returned by the API.
     */
    private int lastStatusCode = 200;

    /**
     * Constructs a new NVD CVE API client.
     *
     * @param apiKey the api key; can be null
     * @param endpoint the endpoint for the NVD CVE API; if null the default endpoint is used
     * @param threadCount the number of threads to use when calling the NVD API.
     * @param maxPageCount the maximum number of pages to retrieve from the NVD API.
     */
    NvdCveApi(String apiKey, String endpoint, int threadCount, int maxPageCount) {
        this(apiKey, endpoint, apiKey == null ? 6500 : 600, threadCount, maxPageCount);
    }

    /**
     * Constructs a new NVD CVE API client.
     *
     * @param apiKey the api key; can be null
     * @param endpoint the endpoint for the NVD CVE API; if null the default endpoint is used
     * @param delay the delay in milliseconds between API calls on a single thread.
     * @param threadCount the number of threads to use when calling the NVD API.
     * @param maxPageCount the maximum number of pages to retrieve from the NVD API.
     */
    NvdCveApi(String apiKey, String endpoint, long delay, int threadCount, int maxPageCount) {
        this.apiKey = apiKey;
        if (endpoint == null) {
            this.endpoint = DEFAULT_ENDPOINT;
        } else {
            this.endpoint = endpoint;
        }
        if (threadCount <= 0) {
            threadCount = 1;
        }
        this.maxPageCount = maxPageCount;
        // configure the rate limit slightly higher then the published limits:
        // https://nvd.nist.gov/developers/start-here (see Rate Limits)
        if (apiKey == null) {
            if (threadCount > 1) {
                LOG.warn(
                        "No api key provided; as such the thread count has been reset to 1 instead of the requestsed {}",
                        threadCount);
                threadCount = 1;
            }
            meter = new RateMeter(5, 32500);
        } else {
            meter = new RateMeter(50, 32500);
        }
        clients = new ArrayList<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            clients.add(new RateLimitedClient(delay, meter));
        }
        objectMapper = new ObjectMapper();
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
     * @param startIndex the start index to request
     * @return the future
     * @throws NvdApiException thrown if there is a problem calling the API
     */
    private Future<SimpleHttpResponse> callApi(int clientIndex, int startIndex) throws NvdApiException {
        try {
            URIBuilder uriBuilder = new URIBuilder(endpoint);
            if (filters != null) {
                uriBuilder.addParameters(filters);
            }
            uriBuilder.addParameter("resultsPerPage", Integer.toString(resultsPerPage));
            uriBuilder.addParameter("startIndex", Integer.toString(startIndex));
            final SimpleRequestBuilder builder = SimpleRequestBuilder.get();
            if (apiKey != null) {
                builder.addHeader(API_KEY_NAME, apiKey);
            }
            URI uri = uriBuilder.build();
            final SimpleHttpRequest request = builder.setUri(uri).build();
            return clients.get(clientIndex).execute(request);
        } catch (URISyntaxException e) {
            throw new NvdApiException(e);
        }
    }

    @Override
    public void close() throws Exception {
        for (RateLimitedClient client : clients) {
            client.close();
        }
        clients = null;
        if (futures.size() > 0) {
            for (Future<SimpleHttpResponse> future : futures) {
                if (!future.isDone()) {
                    future.cancel(true);
                }
            }
            futures.clear();
        }
    }

    @Override
    public boolean hasNext() {
        if (lastStatusCode != 200) {
            return false;
        }
        return firstCall || !futures.isEmpty();
    }

    @Override
    public Collection<DefCveItem> next() {
        if (firstCall) {
            futures.add(callApi(0, 0));
        }
        String json = "";
        try {
            SimpleHttpResponse response = getCompletedFuture();
            if (response.getCode() == 200) {
                LOG.debug("Conent-Type Received: {}", response.getContentType());
                // the below if block is debug code that should be removed.
                // in rare cases a response was seen in testing that was not JSON...
                // trying to debug...
                // if (response.getBody().isBytes()) {
                // try (FileOutputStream fos = new FileOutputStream("./nvd.data");
                // DataOutputStream outStream = new DataOutputStream(new BufferedOutputStream(fos))) {
                // outStream.write(response.getBodyBytes());
                // } catch (FileNotFoundException e) {
                // e.printStackTrace();
                // } catch (IOException e) {
                // e.printStackTrace();
                // }
                // }
                json = response.getBodyText();

                CveApiJson20 current = objectMapper.readValue(json, CveApiJson20.class);
                this.totalResults = current.getTotalResults();
                long lastModified = current.getTimestamp().toEpochSecond(ZoneOffset.UTC);
                if (lastModified > this.lastModifiedRequest) {
                    this.lastModifiedRequest = lastModified;
                }
                if (firstCall) {
                    firstCall = false;
                    queueCalls();
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

    private SimpleHttpResponse getCompletedFuture() throws InterruptedException, ExecutionException {
        boolean notFound = futures.size() > 0;
        Future<SimpleHttpResponse> result = null;
        while (notFound) {
            for (Future<SimpleHttpResponse> future : futures) {
                if (future.isDone()) {
                    result = future;
                    notFound = false;
                    break;
                }
            }
            Thread.sleep(500);
        }
        if (result != null) {
            futures.remove(result);
            return result.get();
        }
        return null;
    }

    private void queueCalls() {
        int clientIndex = 0;
        int pageCount = 1;
        // start at results per page - as 0 was already requested
        for (int i = resultsPerPage; (maxPageCount <= 0 || pageCount < maxPageCount)
                && i < totalResults; i += resultsPerPage) {
            futures.add(callApi(clientIndex, i));
            pageCount += 1;
            clientIndex += 1;
            if (clientIndex >= clients.size()) {
                clientIndex = 0;
            }
        }
    }
}
