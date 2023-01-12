# vulnz cli & nvd-lib

A [simple java client](/nvd-lib) for the NVD API and a [simple vulnz cli](/vulnz). Currently, only the NVD Vulnerabilities
API is implemented; if the Products API is desired please open an issue or create a PR.
In the future additional java client will be built to support other vulnerability sources.

## NVD Links
* [Getting Started](https://nvd.nist.gov/developers/start-here)
* [Vulnerabilities API](https://nvd.nist.gov/developers/vulnerabilities)
* [Request an NVD API Key](https://nvd.nist.gov/developers/request-an-api-key)

## usage notes

An API Key for the NVD API is highly recommended - especially when downloading the  full Vulnerability Catalog from the
NVD. Without an API key downloading takes 10+ minutes; whereas with an API key (and using 4 threads) the entire NVD
Vulnerability Catalog can be downloaded in ~90 seconds.

## nvd-lib usage

### maven

```xml
<dependency>
   <groupId>io.github.jeremylong</groupId>
   <artifactId>nvd-lib</artifactId>
   <version>1.0.0</version>
</dependency>
```

### gradle

```groovy
implementation 'io.github.jeremylong:nvd-lib:1.0.0'
```

### building from source

```shell
./gradlew clean build
```

The API is intended to be fairly simple; an example implementation is given below to retrieve the entire NVD CVE data
set - including a mechanism to keep the data up to date.

```java
import io.github.jeremylong.nvdlib.NvdCveApi;
import io.github.jeremylong.nvdlib.NvdCveApiBuilder;
import io.github.jeremylong.nvdlib.nvd.DefCveItem;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;

public class Example {

    long retrieveLastModifiedRequestEpoch() {
        //TODO implement a storage/retrieval mechanism for the epoch time.
        // if the last modified request epoch is not available the method should return 0
        return 0;
    }

    void storeLastModifiedRequestEpoch(long epoch) {
        //TODO implement a storage/retrieval mechanism for the epoch time.
    }

    public void update() {
        long lastModifiedRequest = retrieveLastModifiedRequestEpoch();
        NvdCveApiBuilder builder = NvdCveApiBuilder.aNvdCveApi();
        if (lastModifiedRequest > 0) {
            LocalDateTime start = LocalDateTime.ofEpochSecond(lastModifiedRequest, 0, ZoneOffset.UTC);
            LocalDateTime end = start.minusDays(-120);
            builder.withLastModifiedFilter(start, end);
        }
        //TODO add API key with builder's `withApiKey()`
        //TODO if an API Key is used consider adding `withThreadCount(4)`
        //TODO add any additional filters via the builder's `withFilter()`
        try (NvdCveApi api = builder.build()) {
            while (api.hasNext()) {
                Collection<DefCveItem> items = api.next();
                //TODO do something with the items
            }
            lastModifiedRequest = api.getLastModifiedRequest();
        } catch (Exception e) {
            e.printStackTrace();
        }
        storeLastModifiedRequestEpoch(lastModifiedRequest);
    }
}
```

## vulnz cli

The cli is a spring-boot command line tool built with picocli. The example
below does run the setup - which creates both the `vulnz` symlink (in `/usr/local/bin`)
and a completion script. If using zsh, the completion will be added to 
`/etc/bash_completion.d` or `/usr/local/etc/bash_completion.d` (depending
on if they exist); see [permanently installing completion](https://picocli.info/autocomplete.html#_installing_completion_scripts_permanently_in_bashzsh)
for more details. We may add a brew formula in the future.

After running `install` you may need to restart your shell for the completion to work.

```bash
$ ./gradlew build
$ cd vulnz/build/libs
$ ./vulnz-1.0.0.jar install
$ vulnz cve --cveId CVE-2021-44228 --prettyPrint
```

Example of using the CLI with an API key stored in 1password using
the `op` CLI (see [getting started with op](https://developer.1password.com/docs/cli/get-started/)):

```bash
export NVD_API_KEY=op://vaultname/nvd-api/credential
eval $(op signin)
op run -- vulnz cve --threads 4 > cve-complete.json
```
