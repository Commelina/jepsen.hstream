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
    # start test
    lein with-profile kafka run test \
    --db hstream \
    --no-txn \
    --no-server-idempotence \
    --nemesis none \
    --workload queue \
    --time-limit 60 \
    --sub-via subscribe && \
    exit