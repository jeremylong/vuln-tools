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
package com.github.jeremylong.nvd.cli.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.github.jeremylong.nvdlib.nvd.CveItem;
import com.github.jeremylong.nvdlib.nvd.DefCveItem;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"success", "reason", "lastModifiedDate", "count", "cves"})
public class CveOutput {

    @JsonProperty("cves")
    @SuppressFBWarnings("URF_UNREAD_FIELD")
    private Set<CveItem> cves = new HashSet<>();
    @JsonProperty("success")
    @SuppressFBWarnings("URF_UNREAD_FIELD")
    private boolean success;
    @JsonProperty("reason")
    @SuppressFBWarnings("URF_UNREAD_FIELD")
    private String reason;
    @JsonProperty("count")
    @SuppressFBWarnings("URF_UNREAD_FIELD")
    private int count;
    @JsonProperty("lastModifiedDate")
    @SuppressFBWarnings("URF_UNREAD_FIELD")
    private LocalDateTime lastModifiedDate;

    public void addAll(Collection<DefCveItem> items) {
        if (items != null) {
            items.stream().map(c -> c.getCve()).forEach(cves::add);
            count = cves.size();
        }
    }

    public void setLastModifiedDate(long lastModifiedDate) {
        LocalDateTime utcDate = LocalDateTime.ofEpochSecond(lastModifiedDate, 0, ZoneOffset.UTC);
        this.lastModifiedDate = utcDate;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}