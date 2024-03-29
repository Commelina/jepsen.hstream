(defproject jepsen.hstream "0.0.0-SNAPSHOT"
  :description "Jepsen test instances for HStreamDB"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0",
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :profiles {:legacy-list-append  {:main jepsen.hstream.list-append-test},
             :legacy-husky        {:main jepsen.hstream.husky-test}
             :kafka               {:main jepsen.hstream.kafka-test}}
  :repositories
    [["sonatype-snapshot"
      "https://s01.oss.sonatype.org/content/repositories/snapshots"]]
  :dependencies [[org.clojure/clojure "1.11.2"]
                 [jepsen "0.3.5"]
                 [cheshire "5.12.0"]
                 [clj-http "3.12.3"]
                 [random-string "0.1.0"]
                 [org.apache.kafka/kafka-clients "3.7.0"]
                 [io.grpc/grpc-stub "1.62.2"]
                 ;;[org.apache.logging.log4j/log4j-slf4j-impl "2.23.1"]
                 [org.slf4j/slf4j-api "2.0.12"]
                 [io.hstream/hstreamdb-java "0.17.0"
                  :exclusions [org.apache.logging.log4j/log4j-slf4j-impl]]
                 ]
  :jvm-opts ["-server"
             ;"-XX:-OmitStackTraceInFastThrow"
             "-Djava.awt.headless=true"
             ; GC tuning--see
             ; https://wiki.openjdk.java.net/display/shenandoah/Main
             ; https://wiki.openjdk.java.net/display/zgc/Main
             ;"-XX+UseZGC"
             ;"-XX+UseShenandoahGC"
             "-Xmx24g"
             ;"-XX:+UseLargePages" ; requires users do so some OS-level config
             "-XX:+AlwaysPreTouch"
             ; Instrumentation
             ;"-agentpath:/home/aphyr/yourkit/bin/linux-x86-64/libyjpagent.so=disablestacktelemetry,exceptions=disable,delay=10000"
             ]
  )
