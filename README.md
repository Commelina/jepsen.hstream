# jepsen.hstream

Jepsen test instances for [HStreamDB](https://github.com/hstreamdb/hstream).

**Note:**

The following files are forked & modified from [https://github.com/jepsen-io/redpanda](Jepsen's official repository), with the same license.

- `src/jepsen/hstream/kafka_test.clj`
- `src/jepsen/hstream/kafka/*`

## Overview

The project is at its early stage and is under active development.

It currently contains the following tests:

- A modified set test suitable for append-only streaming databases

## Usage

```
./scripts/build_base.sh
./scripts/build_legacy.sh
./scripts/up_legacy.sh
./scripts/clean_legacy.sh
```

For kafka version test, run `*_kafka.sh`.

## Check Test Results

The test results will be stored at `./store` directory. Check it manually or by a simple server:

- If you have [`leiningen`](https://leiningen.org/) installed:
```
lein with-profile legacy-husky run serve
```
- If you do not have `leiningen`:
```
docker run -t --rm --network host -v $(pwd):/working -v $HOME/.m2:/root/.m2 clojure:temurin-21-lein /bin/bash -c "cd /working && lein with-profile legacy-husky run serve"
```

Then browse the results at `localhost:8080`.

## Customization

- Add `BASE_IMAGE` and `HSTREAM_IMAGE` arg on building step to use your own hstream image.
- Add `USE_CHINA_MIRROR` arg to speed up downloading.
- Add `env_http_proxy` and `env_https_proxy` arg to use proxy from your host (it should allow LAN requests).
- Adjust test parameters in `docker/control/Dockerfile` or `docker/control-kafka/Dockerfile`, then **rebuild images** (no need to rebuild base image).
