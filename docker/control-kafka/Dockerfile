ARG HSTREAM_IMAGE="hstreamdb/hstream:latest"
FROM ${HSTREAM_IMAGE}

# Do not ask for user input when installing packages
ENV DEBIAN_FRONTEND=noninteractive

ARG USE_CHINA_MIRROR
# Mirror
RUN if [ "$USE_CHINA_MIRROR" = true ] ; then sed -i 's/archive.ubuntu.com/mirrors.ustc.edu.cn/g' /etc/apt/sources.list ; fi

# Basic system stuff
RUN apt-get -qy update && \
    apt-get -qy --no-install-recommends install \
        apt-transport-https

# Jepsen (control) dependencies
RUN apt-get -qy update && \
    apt-get -qy --no-install-recommends install \
        curl \
        dos2unix \
        git \
        gnuplot-nox \
        graphviz \
        htop \
        iputils-ping \
        libjna-java \
        leiningen \
        openjdk-21-jdk-headless \
        openssh-client \
        screen \
        wget \
        zookeeper && \
    rm -rf /var/lib/apt/lists/* && apt-get clean

ADD ./bashrc /root/.bashrc
ADD ./init-ssh-control.sh /init-ssh.sh
RUN dos2unix /init-ssh.sh /root/.bashrc \
    && chmod +x /init-ssh.sh

# Proxy
ARG arg_http_proxy
ARG arg_https_proxy
ENV env_http_proxy=$arg_http_proxy
ENV env_https_proxy=$arg_https_proxy

CMD /init-ssh.sh && \
    eval `ssh-agent` && \
    ssh-add /root/.ssh/id_rsa && \
    cd /home/Work && \
    tail -f --retry /var/jepsen/shared/hserver-cluster-started | sed '/Bootstraped/ q' && \
    ### Use proxy (if set) to download deps, then unset proxy for running tests
    export http_proxy=$env_http_proxy https_proxy=$env_https_proxy && \
    lein deps && \
    unset http_proxy && \
    unset https_proxy && \
    ####################### start test #######################
    lein with-profile kafka run test \
    #-------------- basic options --------------#
    --db hstream \
    --workload queue \
    --sub-via subscribe \
    --time-limit 20 \
    --final-time-limit 120 \
    --key-dist uniform \
    --key-count 4 \
    ##<<<
    # This value is enough. A larger one can make elle exhausted.
    --concurrency 2 \
    ##>>>
    ##<<<
    # When writes of a key exceed the limit, the generator will pick
    # a new key (which can be larger than 'key-count'!!!), which is
    # very confusing. So we set a very large limit here.
    --max-writes-per-key 102400 \
    ##>>>
    #-------------- kafka client options --------------#
    ##<<<
    # The default number of partitions is 1, which may be different
    # from the test. This can make some partitions unusable. So we
    # never create topics automatically.
    --disable-server-auto-create-topics \
    ##>>>
    ##<<<
    # We disable producer retry now to avoid dupicate writes.
    # It may be turned on in the future to perform more tests...
    --retries 0 \
    ##>>>
    #-------------- txns options --------------#
    ##<<<
    # We do not support txns and idempotent for now.
    # So we disable it.
    --no-txn \
    --no-idempotence \
    --no-server-idempotence \
    ##>>>
    #-------------- nemesis options --------------#
    --nemesis none \
    --crash-clients true \
    # --crash-client-interval 30 \ # FIXME: parse error, what happened?
    # --tcpdump \ # TODO: sadly, tcpdump is only available for redpanda.
    #-------------- default options --------------#
    ##<<<
    # The following opts is client default (3.7.0) but we
    # specify them explicitly here for clarity.
    --auto-offset-reset latest \
    --enable-auto-commit \
    --enable-server-auto-create-topics \
    --isolation-level read_uncommitted \
    --acks all && \
    ##>>>
    ##<<<
    # For kafka test, the following options are hard-coded,
    # see workload/queue.clj and workload/list_append.clj.
    # - replication-factor = 3
    # - partition-count = 4
    # - poll-ms = 100
    # - ... TODO
    ##>>>
    exit
