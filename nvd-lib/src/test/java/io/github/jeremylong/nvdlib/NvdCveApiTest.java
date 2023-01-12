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

import io.github.jeremylong.nvdlib.nvd.DefCveItem;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;

class NvdCveApiTest {

    long retrieveLastModifiedRequestEpoch() {
        // TODO implement a storage/retrieval mechanism for the epoch time.
        // if the last modified request epoch is not avaiable the method should return 0

        return LocalDateTime.now(ZoneOffset.UTC).minusDays(20).toEpochSecond(ZoneOffset.UTC);
    }

    void storeLastModifiedRequestEpoch(long epoch) {
        // TODO implement a storage/retrieval mechanism for the epoch time.
    }

    @Test
    public void update() {
        long lastModifiedRequest = retrieveLastModifiedRequestEpoch();
        NvdCveApiBuilder builder = NvdCveApiBuilder.aNvdCveApi();
        if (lastModifiedRequest > 0) {
            LocalDateTime start = LocalDateTime.ofEpochSecond(lastModifiedRequest, 0, ZoneOffset.UTC);
            LocalDateTime end = start.minusDays(-120);
            builder.withLastModifiedFilter(start, end);
        }
        // TODO add any additional filters via the builder's `withfilter()`

        // TODO add API key with builder's `withApiKey()`

        try (NvdCveApi api = builder.build()) {
            // in a real world case - `while` would be used instead of `if`
            if (api.hasNext()) {
                Collection<DefCveItem> items = api.next();
                // TODO do something with the items
            }
            lastModifiedRequest = api.getLastModifiedRequest();
        } catch (Exception e) {
            e.printStackTrace();
        }
        storeLastModifiedRequestEpoch(lastModifiedRequest);
    }
}