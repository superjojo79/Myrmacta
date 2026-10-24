# Annexe — inventaire des licences (2026-10-24)

Complète [l'audit](audit-licences-2026-10-24.md). Une ligne par artefact ou paquet.
« OU » signifie que le POM déclare plusieurs licences : c'est en général un choix
(double licence), pas une accumulation. Les cas sensibles sont commentés dans l'audit.

Maven : 420 artefacts distincts. npm : 503 paquets (hors racine).

## Maven — répartition

- jar de production : 367
- test, hors livrable : 49
- test (défaut) et jar démo/QA avec `-Pembedded-db` : 2
- jar démo/QA seulement : 2

## npm — répartition

- build/test, hors livrable : 264
- transitive, graphe de production : 204
- peer de production, livrable : 1
- dépendance directe de dev/test : 18
- dépendance directe de production : 16

## Maven

| Livrable | Coordonnées | Licence déclarée |
|---|---|---|
| jar de production | `at.yawk.lz4:lz4-java:1.10.1` | Apache License, Version 2.0 |
| jar de production | `ch.qos.logback:logback-classic:1.5.38` | EPL-2.0 OU LGPL-2.1-only (héritée) |
| jar de production | `ch.qos.logback:logback-core:1.5.38` | EPL-2.0 OU LGPL-2.1-only (héritée) |
| jar de production | `com.aayushatharva.brotli4j:brotli4j:1.23.0` | Apache License 2.0 (héritée) |
| jar de production | `com.aayushatharva.brotli4j:native-linux-x86_64:1.23.0` | Apache License 2.0 (héritée) |
| jar de production | `com.aayushatharva.brotli4j:service:1.23.0` | Apache License 2.0 (héritée) |
| jar de production | `com.anthropic:anthropic-java-core:2.52.0` | MIT |
| jar de production | `com.dashjoin:jsonata:0.9.10` | Apache License, Version 2.0 (héritée) |
| jar de production | `com.ethlo.time:itu:1.14.0` | Apache License, Version 2.0 |
| jar de production | `com.fasterxml:classmate:1.7.3` | Apache License, Version 2.0 |
| jar de production | `com.fasterxml.jackson.core:jackson-annotations:2.21` | The Apache Software License, Version 2.0 |
| jar de production | `com.fasterxml.jackson.core:jackson-core:2.21.5` | The Apache Software License, Version 2.0 |
| jar de production | `com.fasterxml.jackson.core:jackson-databind:2.21.5` | The Apache Software License, Version 2.0 |
| jar de production | `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.21.5` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.21.5` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.5` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `com.fasterxml.jackson.module:jackson-module-kotlin:2.21.5` | The Apache Software License, Version 2.0 |
| jar de production | `com.fasterxml.uuid:java-uuid-generator:5.1.0` | The Apache Software License, Version 2.0 |
| jar de production | `com.github.librepdf:openpdf:3.0.5` | GNU Lesser General Public License (LGPL), Version 2.1 OU Mozilla Public License Version 2.0 (héritée) |
| jar de production | `com.github.luben:zstd-jni:1.5.6-10` | BSD 2-Clause License |
| jar de production | `com.github.stephenc.jcip:jcip-annotations:1.0-1` | Apache License, Version 2.0 |
| jar de production | `com.github.victools:jsonschema-generator:5.0.0` | The Apache License, Version 2.0 (héritée) |
| jar de production | `com.github.victools:jsonschema-module-jackson:5.0.0` | The Apache License, Version 2.0 (héritée) |
| jar de production | `com.github.victools:jsonschema-module-swagger-2:5.0.0` | The Apache License, Version 2.0 (héritée) |
| jar de production | `com.github.virtuald:curvesapi:1.08` | BSD License |
| jar de production | `com.google.errorprone:error_prone_annotations:2.50.0` | Apache 2.0 |
| jar de production | `com.hierynomus:asn-one:0.6.0` | The Apache License, Version 2.0 |
| jar de production | `com.hierynomus:sshj:0.41.2` | The Apache License, Version 2.0 |
| jar de production | `com.knuddels:jtokkit:1.1.0` | MIT License |
| jar de production | `com.networknt:json-schema-validator:3.0.1` | Apache License Version 2.0 |
| jar de production | `com.nimbusds:content-type:2.3` | The Apache Software License, Version 2.0 |
| jar de production | `com.nimbusds:lang-tag:1.7` | The Apache Software License, Version 2.0 |
| jar de production | `com.nimbusds:nimbus-jose-jwt:10.9.1` | The Apache Software License, Version 2.0 |
| jar de production | `com.nimbusds:oauth2-oidc-sdk:11.38.2` | Apache License, version 2.0 |
| jar de production | `com.opencsv:opencsv:5.11.2` | Apache 2 |
| jar de production | `com.squareup.okhttp3:okhttp:4.12.0` | The Apache Software License, Version 2.0 |
| jar de production | `com.squareup.okio:okio:3.6.0` | The Apache Software License, Version 2.0 |
| jar de production | `com.squareup.okio:okio-jvm:3.6.0` | The Apache Software License, Version 2.0 |
| jar de production | `com.standardwebhooks:standardwebhooks:1.1.1` | The MIT License |
| jar de production | `com.sun.istack:istack-commons-runtime:4.1.2` | Eclipse Distribution License - v 1.0 (héritée) |
| jar de production | `com.zaxxer:HikariCP:7.0.2` | The Apache Software License, Version 2.0 |
| jar de production | `com.zaxxer:SparseBitSet:1.3` | The Apache Software License, Version 2.0 |
| jar de production | `commons-codec:commons-codec:1.21.0` | Apache-2.0 (héritée) |
| jar de production | `commons-io:commons-io:2.21.0` | Apache-2.0 (héritée) |
| jar de production | `commons-logging:commons-logging:1.3.6` | Apache-2.0 (héritée) |
| jar de production | `io.micrometer:context-propagation:1.2.1` | The Apache Software License, Version 2.0 |
| jar de production | `io.micrometer:micrometer-commons:1.17.1` | The Apache Software License, Version 2.0 |
| jar de production | `io.micrometer:micrometer-core:1.17.1` | The Apache Software License, Version 2.0 |
| jar de production | `io.micrometer:micrometer-jakarta9:1.17.1` | The Apache Software License, Version 2.0 |
| jar de production | `io.micrometer:micrometer-observation:1.17.1` | The Apache Software License, Version 2.0 |
| jar de production | `io.micrometer:micrometer-registry-prometheus:1.17.1` | The Apache Software License, Version 2.0 |
| jar de production | `io.modelcontextprotocol.sdk:mcp:2.0.0` | MIT License |
| jar de production | `io.modelcontextprotocol.sdk:mcp-core:2.0.0` | MIT License |
| jar de production | `io.modelcontextprotocol.sdk:mcp-json-jackson3:2.0.0` | MIT License |
| jar de production | `io.netty:netty-buffer:4.2.19.Final` | Apache License, Version 2.0 (héritée) |
| jar de production | `io.netty:netty-codec:4.2.19.Final` | Apache License, Version 2.0 (héritée) |
| jar de production | `io.netty:netty-codec-base:4.2.19.Final` | Apache License, Version 2.0 (héritée) |
| jar de production | `io.netty:netty-codec-compression:4.2.19.Final` | Apache License, Version 2.0 (héritée) |
| jar de production | `io.netty:netty-codec-http:4.2.19.Final` | Apache License, Version 2.0 (héritée) |
| jar de production | `io.netty:netty-codec-http2:4.2.19.Final` | Apache License, Version 2.0 (héritée) |
| jar de production | `io.netty:netty-codec-marshalling:4.2.19.Final` | Apache License, Version 2.0 (héritée) |
| jar de production | `io.netty:netty-codec-protobuf:4.2.19.Final` | Apache License, Version 2.0 (héritée) |
| jar de production | `io.netty:netty-common:4.2.19.Final` | Apache License, Version 2.0 (héritée) |
| jar de production | `io.netty:netty-handler:4.2.19.Final` | Apache License, Version 2.0 (héritée) |
| jar de production | `io.netty:netty-resolver:4.2.19.Final` | Apache License, Version 2.0 (héritée) |
| jar de production | `io.netty:netty-transport:4.2.19.Final` | Apache License, Version 2.0 (héritée) |
| jar de production | `io.netty:netty-transport-classes-epoll:4.2.19.Final` | Apache License, Version 2.0 (héritée) |
| jar de production | `io.netty:netty-transport-native-unix-common:4.2.19.Final` | Apache License, Version 2.0 (héritée) |
| jar de production | `io.projectreactor:reactor-core:3.8.7` | Apache License, Version 2.0 |
| jar de production | `io.prometheus:prometheus-metrics-config:1.7.0` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `io.prometheus:prometheus-metrics-core:1.7.0` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `io.prometheus:prometheus-metrics-exposition-formats:1.7.0` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `io.prometheus:prometheus-metrics-exposition-textformats:1.7.0` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `io.prometheus:prometheus-metrics-model:1.7.0` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `io.prometheus:prometheus-metrics-tracer-common:1.7.0` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `io.swagger.core.v3:swagger-annotations:2.2.50` | Apache License 2.0 (héritée) |
| jar de production | `io.swagger.core.v3:swagger-annotations-jakarta:2.2.38` | Apache License 2.0 (héritée) |
| jar de production | `io.swagger.core.v3:swagger-core-jakarta:2.2.55` | Apache License 2.0 (héritée) |
| jar de production | `io.swagger.core.v3:swagger-models-jakarta:2.2.55` | Apache License 2.0 (héritée) |
| jar de production | `jakarta.activation:jakarta.activation-api:2.1.4` | EDL 1.0 |
| jar de production | `jakarta.annotation:jakarta.annotation-api:3.0.0` | EPL 2.0 OU GPL2 w/ CPE |
| jar de production | `jakarta.inject:jakarta.inject-api:2.0.1` | The Apache Software License, Version 2.0 |
| jar de production | `jakarta.mail:jakarta.mail-api:2.1.5` | EPL 2.0 OU GPL2 w/ CPE OU EDL 1.0 |
| jar de production | `jakarta.persistence:jakarta.persistence-api:3.2.0` | Eclipse Public License v. 2.0 OU Eclipse Distribution License v. 1.0 |
| jar de production | `jakarta.transaction:jakarta.transaction-api:2.0.1` | EPL 2.0 OU GPL2 w/ CPE |
| jar de production | `jakarta.validation:jakarta.validation-api:3.1.1` | Apache License 2.0 |
| jar de production | `jakarta.xml.bind:jakarta.xml.bind-api:4.0.5` | Eclipse Distribution License - v 1.0 (héritée) |
| jar de production | `joda-time:joda-time:2.14.0` | Apache License, Version 2.0 |
| jar de production | `net.bytebuddy:byte-buddy:1.18.11` | Apache License, Version 2.0 (héritée) |
| jar de production | `net.minidev:accessors-smart:2.6.0` | The Apache Software License, Version 2.0 |
| jar de production | `net.minidev:json-smart:2.6.0` | The Apache Software License, Version 2.0 |
| jar de production | `org.antlr:ST4:4.3.4` | The BSD License |
| jar de production | `org.antlr:antlr-runtime:3.5.3` | BSD licence (héritée) |
| jar de production | `org.antlr:antlr4-runtime:4.13.2` | BSD-3-Clause (héritée) |
| jar de production | `org.apache.commons:commons-collections4:4.5.0` | Apache-2.0 (héritée) |
| jar de production | `org.apache.commons:commons-compress:1.28.0` | Apache-2.0 (héritée) |
| jar de production | `org.apache.commons:commons-csv:1.14.1` | Apache-2.0 (héritée) |
| jar de production | `org.apache.commons:commons-lang3:3.20.0` | Apache-2.0 (héritée) |
| jar de production | `org.apache.commons:commons-math3:3.6.1` | Apache License, Version 2.0 (héritée) |
| jar de production | `org.apache.commons:commons-text:1.13.1` | Apache-2.0 (héritée) |
| jar de production | `org.apache.httpcomponents.client5:httpclient5:5.6.4` | Apache License, Version 2.0 (héritée) |
| jar de production | `org.apache.httpcomponents.core5:httpcore5:5.4.3` | Apache License, Version 2.0 (héritée) |
| jar de production | `org.apache.httpcomponents.core5:httpcore5-h2:5.4.3` | Apache License, Version 2.0 (héritée) |
| jar de production | `org.apache.kafka:kafka-clients:4.2.1` | The Apache License, Version 2.0 |
| jar de production | `org.apache.logging.log4j:log4j-api:2.25.5` | Apache-2.0 |
| jar de production | `org.apache.logging.log4j:log4j-to-slf4j:2.25.5` | Apache-2.0 |
| jar de production | `org.apache.poi:poi:5.5.1` | Apache License, Version 2.0 |
| jar de production | `org.apache.poi:poi-ooxml:5.5.1` | Apache License, Version 2.0 |
| jar de production | `org.apache.poi:poi-ooxml-lite:5.5.1` | Apache License, Version 2.0 |
| jar de production | `org.apache.tomcat.embed:tomcat-embed-core:11.0.26` | Apache License, Version 2.0 |
| jar de production | `org.apache.tomcat.embed:tomcat-embed-el:11.0.26` | Apache License, Version 2.0 |
| jar de production | `org.apache.tomcat.embed:tomcat-embed-websocket:11.0.26` | Apache License, Version 2.0 |
| jar de production | `org.apache.xmlbeans:xmlbeans:5.3.0` | The Apache Software License, Version 2.0 |
| jar de production | `org.apache.xmlgraphics:batik-anim:1.19` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `org.apache.xmlgraphics:batik-awt-util:1.19` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `org.apache.xmlgraphics:batik-bridge:1.19` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `org.apache.xmlgraphics:batik-codec:1.19` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `org.apache.xmlgraphics:batik-constants:1.19` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `org.apache.xmlgraphics:batik-css:1.19` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `org.apache.xmlgraphics:batik-dom:1.19` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `org.apache.xmlgraphics:batik-ext:1.19` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `org.apache.xmlgraphics:batik-gvt:1.19` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `org.apache.xmlgraphics:batik-i18n:1.19` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `org.apache.xmlgraphics:batik-parser:1.19` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `org.apache.xmlgraphics:batik-script:1.19` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `org.apache.xmlgraphics:batik-shared-resources:1.19` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `org.apache.xmlgraphics:batik-svg-dom:1.19` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `org.apache.xmlgraphics:batik-svggen:1.19` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `org.apache.xmlgraphics:batik-transcoder:1.19` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `org.apache.xmlgraphics:batik-util:1.19` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `org.apache.xmlgraphics:batik-xml:1.19` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `org.apache.xmlgraphics:xmlgraphics-commons:2.11` | The Apache Software License, Version 2.0 |
| jar de production | `org.aspectj:aspectjweaver:1.9.25.1` | Eclipse Public License - v 2.0 |
| jar de production | `org.attoparser:attoparser:2.0.7.RELEASE` | The Apache Software License, Version 2.0 |
| jar de production | `org.bouncycastle:bcpkix-jdk18on:1.84` | Bouncy Castle Licence |
| jar de production | `org.bouncycastle:bcprov-jdk18on:1.84` | Bouncy Castle Licence |
| jar de production | `org.bouncycastle:bcutil-jdk18on:1.84` | Bouncy Castle Licence |
| jar de production | `org.checkerframework:checker-qual:3.55.1` | The MIT License |
| jar de production | `org.eclipse.angus:angus-activation:2.0.3` | EDL 1.0 (héritée) |
| jar de production | `org.eclipse.angus:angus-mail:2.0.5` | EPL 2.0 OU GPL2 w/ CPE OU EDL 1.0 (héritée) |
| jar de production | `org.flowable:flowable-app-engine:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-app-engine-api:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-app-engine-spring:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-batch-service:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-batch-service-api:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-bpmn-converter:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-bpmn-model:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-case-validation:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-cmmn-api:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-cmmn-converter:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-cmmn-engine:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-cmmn-engine-configurator:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-cmmn-image-generator:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-cmmn-model:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-cmmn-spring:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-cmmn-spring-configurator:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-content-api:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-dmn-api:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-dmn-engine:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-dmn-engine-configurator:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-dmn-image-generator:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-dmn-model:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-dmn-spring:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-dmn-spring-configurator:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-dmn-xml-converter:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-engine:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-engine-common:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-engine-common-api:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-engine-configurator:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-entitylink-service:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-entitylink-service-api:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-event-registry:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-event-registry-api:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-event-registry-configurator:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-event-registry-json-converter:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-event-registry-model:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-event-registry-spring:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-event-registry-spring-configurator:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-eventsubscription-service:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-eventsubscription-service-api:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-form-api:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-form-model:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-http-common:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-identitylink-service:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-identitylink-service-api:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-idm-api:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-idm-engine:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-idm-engine-configurator:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-idm-spring:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-idm-spring-configurator:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-image-generator:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-job-service:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-job-service-api:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-job-spring-service:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-mail:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-process-validation:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-spring:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-spring-boot-autoconfigure:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-spring-boot-starter:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-spring-boot-starter-app:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-spring-boot-starter-cmmn:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-spring-boot-starter-dmn:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-spring-boot-starter-process:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-spring-common:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-spring-configurator:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-spring-security:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-task-service:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-task-service-api:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-variable-service:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.flowable:flowable-variable-service-api:8.0.0` | Apache v2 (héritée) |
| jar de production | `org.glassfish.jaxb:jaxb-core:4.0.9` | Eclipse Distribution License - v 1.0 (héritée) |
| jar de production | `org.glassfish.jaxb:jaxb-runtime:4.0.9` | Eclipse Distribution License - v 1.0 (héritée) |
| jar de production | `org.glassfish.jaxb:txw2:4.0.9` | Eclipse Distribution License - v 1.0 (héritée) |
| jar de production | `org.hdrhistogram:HdrHistogram:2.2.2` | Public Domain, per Creative Commons CC0 OU BSD-2-Clause |
| jar de production | `org.hibernate.models:hibernate-models:1.1.1` | Apache License Version 2.0 |
| jar de production | `org.hibernate.orm:hibernate-core:7.4.5.Final` | Apache License, Version 2.0 |
| jar de production | `org.hibernate.validator:hibernate-validator:9.1.3.Final` | Apache License 2.0 |
| jar de production | `org.jboss.logging:jboss-logging:3.6.3.Final` | Apache License 2.0 |
| jar de production | `org.jetbrains:annotations:17.0.0` | The Apache Software License, Version 2.0 |
| jar de production | `org.jetbrains.kotlin:kotlin-reflect:2.4.20` | Apache-2.0 |
| jar de production | `org.jetbrains.kotlin:kotlin-stdlib:2.4.20` | Apache-2.0 |
| jar de production | `org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.4.20` | Apache-2.0 |
| jar de production | `org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.4.20` | Apache-2.0 |
| jar de production | `org.jsoup:jsoup:1.23.2` | The MIT License |
| jar de production | `org.jspecify:jspecify:1.0.1` | The Apache License, Version 2.0 |
| jar de production | `org.liquibase:liquibase-core:4.33.0` | Apache License, Version 2.0 |
| jar de production | `org.mybatis:mybatis:3.5.19` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `org.ow2.asm:asm:9.7.1` | BSD-3-Clause |
| jar de production | `org.postgresql:postgresql:42.7.13` | BSD-2-Clause |
| jar de production | `org.projectlombok:lombok:1.18.46` | The MIT License |
| jar de production | `org.reactivestreams:reactive-streams:1.0.4` | MIT-0 |
| jar de production | `org.slf4j:jcl-over-slf4j:2.0.18` | Apache-2.0 |
| jar de production | `org.slf4j:jul-to-slf4j:2.0.18` | MIT (héritée) |
| jar de production | `org.slf4j:slf4j-api:2.0.18` | MIT (héritée) |
| jar de production | `org.springdoc:springdoc-openapi-starter-common:3.1.1` | The Apache License, Version 2.0 (héritée) |
| jar de production | `org.springdoc:springdoc-openapi-starter-webmvc-api:3.1.1` | The Apache License, Version 2.0 (héritée) |
| jar de production | `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1` | The Apache License, Version 2.0 (héritée) |
| jar de production | `org.springframework:spring-aop:7.0.9` | Apache License, Version 2.0 |
| jar de production | `org.springframework:spring-aspects:7.0.9` | Apache License, Version 2.0 |
| jar de production | `org.springframework:spring-beans:7.0.9` | Apache License, Version 2.0 |
| jar de production | `org.springframework:spring-context:7.0.9` | Apache License, Version 2.0 |
| jar de production | `org.springframework:spring-context-support:7.0.9` | Apache License, Version 2.0 |
| jar de production | `org.springframework:spring-core:7.0.9` | Apache License, Version 2.0 |
| jar de production | `org.springframework:spring-expression:7.0.9` | Apache License, Version 2.0 |
| jar de production | `org.springframework:spring-jdbc:7.0.9` | Apache License, Version 2.0 |
| jar de production | `org.springframework:spring-messaging:7.0.9` | Apache License, Version 2.0 |
| jar de production | `org.springframework:spring-orm:7.0.9` | Apache License, Version 2.0 |
| jar de production | `org.springframework:spring-tx:7.0.9` | Apache License, Version 2.0 |
| jar de production | `org.springframework:spring-web:7.0.9` | Apache License, Version 2.0 |
| jar de production | `org.springframework:spring-webmvc:7.0.9` | Apache License, Version 2.0 |
| jar de production | `org.springframework.ai:mcp-spring-webmvc:2.0.1` | Apache 2.0 |
| jar de production | `org.springframework.ai:spring-ai-anthropic:2.0.1` | Apache 2.0 |
| jar de production | `org.springframework.ai:spring-ai-autoconfigure-mcp-server-common:2.0.1` | Apache 2.0 |
| jar de production | `org.springframework.ai:spring-ai-autoconfigure-mcp-server-webmvc:2.0.1` | Apache 2.0 |
| jar de production | `org.springframework.ai:spring-ai-autoconfigure-model-anthropic:2.0.1` | Apache 2.0 |
| jar de production | `org.springframework.ai:spring-ai-autoconfigure-model-chat-client:2.0.1` | Apache 2.0 |
| jar de production | `org.springframework.ai:spring-ai-autoconfigure-model-chat-memory:2.0.1` | Apache 2.0 |
| jar de production | `org.springframework.ai:spring-ai-autoconfigure-model-chat-observation:2.0.1` | Apache 2.0 |
| jar de production | `org.springframework.ai:spring-ai-autoconfigure-model-tool:2.0.1` | Apache 2.0 |
| jar de production | `org.springframework.ai:spring-ai-client-chat:2.0.1` | Apache 2.0 |
| jar de production | `org.springframework.ai:spring-ai-commons:2.0.1` | Apache 2.0 |
| jar de production | `org.springframework.ai:spring-ai-mcp:2.0.1` | Apache 2.0 |
| jar de production | `org.springframework.ai:spring-ai-mcp-annotations:2.0.1` | Apache 2.0 |
| jar de production | `org.springframework.ai:spring-ai-model:2.0.1` | Apache 2.0 |
| jar de production | `org.springframework.ai:spring-ai-starter-mcp-server-webmvc:2.0.1` | Apache 2.0 |
| jar de production | `org.springframework.ai:spring-ai-starter-model-anthropic:2.0.1` | Apache 2.0 |
| jar de production | `org.springframework.ai:spring-ai-template-st:2.0.1` | Apache 2.0 |
| jar de production | `org.springframework.boot:spring-boot:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-actuator:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-actuator-autoconfigure:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-autoconfigure:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-data-commons:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-data-jpa:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-health:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-hibernate:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-http-converter:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-jackson:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-jdbc:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-jpa:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-liquibase:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-mail:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-micrometer-metrics:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-micrometer-observation:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-persistence:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-security:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-security-oauth2-client:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-security-oauth2-resource-server:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-servlet:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-sql:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-starter:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-starter-actuator:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-starter-aspectj:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-starter-data-jpa:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-starter-jackson:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-starter-jdbc:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-starter-liquibase:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-starter-logging:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-starter-mail:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-starter-micrometer-metrics:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-starter-oauth2-client:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-starter-oauth2-resource-server:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-starter-security:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-starter-thymeleaf:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-starter-tomcat:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-starter-tomcat-runtime:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-starter-validation:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-starter-web:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-thymeleaf:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-tomcat:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-transaction:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-validation:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-web-server:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.boot:spring-boot-webmvc:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.data:spring-data-commons:4.1.1` | Apache License, Version 2.0 (héritée) |
| jar de production | `org.springframework.data:spring-data-jpa:4.1.1` | Apache License, Version 2.0 (héritée) |
| jar de production | `org.springframework.kafka:spring-kafka:4.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.security:spring-security-config:7.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.security:spring-security-core:7.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.security:spring-security-crypto:7.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.security:spring-security-oauth2-client:7.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.security:spring-security-oauth2-core:7.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.security:spring-security-oauth2-jose:7.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.security:spring-security-oauth2-resource-server:7.1.1` | Apache License, Version 2.0 |
| jar de production | `org.springframework.security:spring-security-web:7.1.1` | Apache License, Version 2.0 |
| jar de production | `org.thymeleaf:thymeleaf:3.1.5.RELEASE` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `org.thymeleaf:thymeleaf-spring6:3.1.5.RELEASE` | The Apache Software License, Version 2.0 (héritée) |
| jar de production | `org.unbescape:unbescape:1.1.6.RELEASE` | The Apache Software License, Version 2.0 |
| jar de production | `org.webjars:swagger-ui:5.34.0` | Apache-2.0 |
| jar de production | `org.webjars:webjars-locator-lite:1.1.4` | MIT |
| jar de production | `org.xerial.snappy:snappy-java:1.1.10.7` | Apache-2.0 |
| jar de production | `org.xhtmlrenderer:flying-saucer-core:10.6.0` | GNU Lesser General Public License (LGPL), version 2.1 or later |
| jar de production | `org.xhtmlrenderer:flying-saucer-pdf:10.6.0` | GNU Lesser General Public License (LGPL), version 2.1 or later |
| jar de production | `org.yaml:snakeyaml:2.6` | Apache License, Version 2.0 |
| jar de production | `software.amazon.awssdk:annotations:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:apache5-client:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:arns:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:auth:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:aws-core:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:aws-query-protocol:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:aws-xml-protocol:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:checksums:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:checksums-spi:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:crt-core:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:endpoints-spi:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:http-auth:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:http-auth-aws:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:http-auth-aws-eventstream:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:http-auth-spi:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:http-client-spi:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:identity-spi:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:json-utils:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:metrics-spi:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:netty-nio-client:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:profiles:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:protocol-core:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:regions:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:retries:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:retries-spi:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:s3:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:sdk-core:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:third-party-jackson-core:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:utils:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.awssdk:utils-lite:2.55.15` | Apache License, Version 2.0 (héritée) |
| jar de production | `software.amazon.eventstream:eventstream:1.0.1` | Apache License, Version 2.0 |
| jar de production | `tools.jackson.core:jackson-core:3.1.5` | The Apache Software License, Version 2.0 |
| jar de production | `tools.jackson.core:jackson-databind:3.1.5` | The Apache Software License, Version 2.0 |
| jar de production | `xml-apis:xml-apis-ext:1.3.04` | The Apache Software License, Version 2.0 (héritée) |
| test (défaut) ; jar démo/QA avec -Pembedded-db | `io.zonky.test:embedded-postgres:2.2.2` | The Apache License, Version 2.0 |
| test, hors livrable | `io.zonky.test.postgres:embedded-postgres-binaries-darwin-amd64:17.11.0` | The Apache License, Version 2.0 |
| jar démo/QA seulement (`-Pembedded-db`, arm64) | `io.zonky.test.postgres:embedded-postgres-binaries-darwin-arm64v8:17.11.0` | The Apache License, Version 2.0 |
| test, hors livrable | `io.zonky.test.postgres:embedded-postgres-binaries-linux-amd64:17.11.0` | The Apache License, Version 2.0 |
| test, hors livrable | `io.zonky.test.postgres:embedded-postgres-binaries-linux-amd64-alpine:17.11.0` | The Apache License, Version 2.0 |
| jar démo/QA seulement (`-Pembedded-db`, arm64) | `io.zonky.test.postgres:embedded-postgres-binaries-linux-arm64v8:17.11.0` | The Apache License, Version 2.0 |
| test, hors livrable | `io.zonky.test.postgres:embedded-postgres-binaries-windows-amd64:17.11.0` | The Apache License, Version 2.0 |
| test (défaut) ; jar démo/QA avec -Pembedded-db | `org.tukaani:xz:1.11` | 0BSD |
| test, hors livrable | `com.github.docker-java:docker-java-api:3.7.1` | The Apache Software License, Version 2.0 (héritée) |
| test, hors livrable | `com.github.docker-java:docker-java-transport:3.7.1` | The Apache Software License, Version 2.0 (héritée) |
| test, hors livrable | `com.github.docker-java:docker-java-transport-zerodep:3.7.1` | The Apache Software License, Version 2.0 (héritée) |
| test, hors livrable | `com.icegreen:greenmail:2.1.14` | Apache 2.0 (héritée) |
| test, hors livrable | `com.icegreen:greenmail-junit5:2.1.14` | Apache 2.0 (héritée) |
| test, hors livrable | `com.jayway.jsonpath:json-path:2.10.0` | The Apache Software License, Version 2.0 |
| test, hors livrable | `com.tngtech.archunit:archunit:1.5.1` | The Apache Software License, Version 2.0 OU BSD |
| test, hors livrable | `com.tngtech.archunit:archunit-junit5:1.5.1` | The Apache Software License, Version 2.0 |
| test, hors livrable | `com.tngtech.archunit:archunit-junit5-api:1.5.1` | The Apache Software License, Version 2.0 |
| test, hors livrable | `com.tngtech.archunit:archunit-junit5-engine:1.5.1` | The Apache Software License, Version 2.0 |
| test, hors livrable | `com.tngtech.archunit:archunit-junit5-engine-api:1.5.1` | The Apache Software License, Version 2.0 |
| test, hors livrable | `com.vaadin.external.google:android-json:0.0.20131108.vaadin1` | Apache License 2.0 |
| test, hors livrable | `junit:junit:4.13.2` | Eclipse Public License 1.0 |
| test, hors livrable | `net.bytebuddy:byte-buddy-agent:1.18.11` | Apache License, Version 2.0 (héritée) |
| test, hors livrable | `net.java.dev.jna:jna:5.18.1` | LGPL-2.1-or-later OU Apache-2.0 |
| test, hors livrable | `org.apiguardian:apiguardian-api:1.1.2` | The Apache License, Version 2.0 |
| test, hors livrable | `org.assertj:assertj-core:3.27.7` | Apache-2.0 |
| test, hors livrable | `org.awaitility:awaitility:4.3.0` | Apache 2.0 (héritée) |
| test, hors livrable | `org.eclipse.angus:jakarta.mail:2.0.5` | EPL 2.0 OU GPL2 w/ CPE OU EDL 1.0 (héritée) |
| test, hors livrable | `org.hamcrest:hamcrest:3.0` | BSD-3-Clause |
| test, hors livrable | `org.junit.jupiter:junit-jupiter:6.0.3` | Eclipse Public License v2.0 |
| test, hors livrable | `org.junit.jupiter:junit-jupiter-api:6.0.3` | Eclipse Public License v2.0 |
| test, hors livrable | `org.junit.jupiter:junit-jupiter-engine:6.0.3` | Eclipse Public License v2.0 |
| test, hors livrable | `org.junit.jupiter:junit-jupiter-params:6.0.3` | Eclipse Public License v2.0 |
| test, hors livrable | `org.junit.platform:junit-platform-commons:6.0.3` | Eclipse Public License v2.0 |
| test, hors livrable | `org.junit.platform:junit-platform-engine:6.0.3` | Eclipse Public License v2.0 |
| test, hors livrable | `org.mockito:mockito-core:5.23.0` | MIT |
| test, hors livrable | `org.mockito:mockito-junit-jupiter:5.23.0` | MIT |
| test, hors livrable | `org.objenesis:objenesis:3.3` | Apache License, Version 2.0 (héritée) |
| test, hors livrable | `org.opentest4j:opentest4j:1.3.0` | The Apache License, Version 2.0 |
| test, hors livrable | `org.rnorth.duct-tape:duct-tape:1.0.8` | MIT |
| test, hors livrable | `org.skyscreamer:jsonassert:1.5.3` | The Apache Software License, Version 2.0 |
| test, hors livrable | `org.springframework:spring-test:7.0.9` | Apache License, Version 2.0 |
| test, hors livrable | `org.springframework.boot:spring-boot-resttestclient:4.1.1` | Apache License, Version 2.0 |
| test, hors livrable | `org.springframework.boot:spring-boot-starter-jackson-test:4.1.1` | Apache License, Version 2.0 |
| test, hors livrable | `org.springframework.boot:spring-boot-starter-test:4.1.1` | Apache License, Version 2.0 |
| test, hors livrable | `org.springframework.boot:spring-boot-starter-webmvc:4.1.1` | Apache License, Version 2.0 |
| test, hors livrable | `org.springframework.boot:spring-boot-starter-webmvc-test:4.1.1` | Apache License, Version 2.0 |
| test, hors livrable | `org.springframework.boot:spring-boot-test:4.1.1` | Apache License, Version 2.0 |
| test, hors livrable | `org.springframework.boot:spring-boot-test-autoconfigure:4.1.1` | Apache License, Version 2.0 |
| test, hors livrable | `org.springframework.boot:spring-boot-webmvc-test:4.1.1` | Apache License, Version 2.0 |
| test, hors livrable | `org.springframework.security:spring-security-test:7.1.1` | Apache License, Version 2.0 |
| test, hors livrable | `org.testcontainers:testcontainers:2.0.5` | MIT |
| test, hors livrable | `org.testcontainers:testcontainers-junit-jupiter:2.0.5` | MIT |
| test, hors livrable | `org.xmlunit:xmlunit-core:2.11.0` | The Apache Software License, Version 2.0 (héritée) |

## npm

| Rôle | Paquet | Version | Licence déclarée |
|---|---|---|---|
| dépendance directe de production | `@openvue/themes` | 1.0.0 | MIT |
| dépendance directe de production | `@tiptap/extension-table` | 3.31.4 | MIT |
| dépendance directe de production | `@tiptap/extension-text-align` | 3.31.4 | MIT |
| dépendance directe de production | `@tiptap/pm` | 3.31.4 | MIT |
| dépendance directe de production | `@tiptap/starter-kit` | 3.31.4 | MIT |
| dépendance directe de production | `@tiptap/vue-3` | 3.31.4 | MIT |
| dépendance directe de production | `axios` | 1.19.0 | MIT |
| dépendance directe de production | `bpmn-js` | 18.24.0 | SEE LICENSE IN LICENSE |
| dépendance directe de production | `bpmn-js-properties-panel` | 5.65.0 | MIT |
| dépendance directe de production | `dmn-js` | 17.10.2 | SEE LICENSE IN LICENSE |
| dépendance directe de production | `openvue` | 1.0.0 | MIT |
| dépendance directe de production | `pinia` | 4.0.2 | MIT |
| dépendance directe de production | `primeicons` | 7.0.0 | MIT |
| dépendance directe de production | `vue` | 3.5.41 | MIT |
| dépendance directe de production | `vue-i18n` | 11.4.12 | MIT |
| dépendance directe de production | `vue-router` | 5.2.0 | MIT |
| transitive, graphe de production | `@babel/generator` | 8.0.0 | MIT |
| transitive, graphe de production | `@babel/helper-string-parser` | 7.29.7 | MIT |
| transitive, graphe de production | `@babel/helper-validator-identifier` | 7.29.7 | MIT |
| transitive, graphe de production | `@babel/parser` | 7.29.8 | MIT |
| transitive, graphe de production | `@babel/types` | 7.29.8 | MIT |
| transitive, graphe de production | `@bpmn-io/cm-theme` | 0.2.2 | MIT |
| transitive, graphe de production | `@bpmn-io/diagram-js-ui` | 0.2.4 | MIT |
| transitive, graphe de production | `@bpmn-io/dmn-variable-resolver` | 0.7.1 | MIT |
| transitive, graphe de production | `@bpmn-io/extract-process-variables` | 2.2.1 | MIT |
| transitive, graphe de production | `@bpmn-io/feel-analyzer` | 0.5.0 | MIT |
| transitive, graphe de production | `@bpmn-io/feel-editor` | 2.7.1 | MIT |
| transitive, graphe de production | `@bpmn-io/feel-lint` | 3.2.0 | MIT |
| transitive, graphe de production | `@bpmn-io/lang-feel` | 4.0.2 | MIT |
| transitive, graphe de production | `@bpmn-io/lezer-feel` | 3.0.1 | MIT |
| peer de production, livrable | `@bpmn-io/properties-panel` | 3.51.2 | MIT |
| transitive, graphe de production | `@bpmn-io/semver-compat` | 0.1.0 | MIT |
| transitive, graphe de production | `@camunda/feel-builtins` | 1.4.0 | MIT |
| transitive, graphe de production | `@codemirror/autocomplete` | 6.20.3 | MIT |
| transitive, graphe de production | `@codemirror/commands` | 6.11.0 | MIT |
| transitive, graphe de production | `@codemirror/language` | 6.12.4 | MIT |
| transitive, graphe de production | `@codemirror/lint` | 6.9.7 | MIT |
| transitive, graphe de production | `@codemirror/state` | 6.7.1 | MIT |
| transitive, graphe de production | `@codemirror/view` | 6.43.9 | MIT |
| transitive, graphe de production | `@floating-ui/core` | 1.8.0 | MIT |
| transitive, graphe de production | `@floating-ui/dom` | 1.8.0 | MIT |
| transitive, graphe de production | `@floating-ui/utils` | 0.2.12 | MIT |
| transitive, graphe de production | `@intlify/core-base` | 11.4.12 | MIT |
| transitive, graphe de production | `@intlify/devtools-types` | 11.4.12 | MIT |
| transitive, graphe de production | `@intlify/message-compiler` | 11.4.12 | MIT |
| transitive, graphe de production | `@intlify/shared` | 11.4.12 | MIT |
| transitive, graphe de production | `@jridgewell/gen-mapping` | 0.3.13 | MIT |
| transitive, graphe de production | `@jridgewell/remapping` | 2.3.5 | MIT |
| transitive, graphe de production | `@jridgewell/resolve-uri` | 3.1.2 | MIT |
| transitive, graphe de production | `@jridgewell/sourcemap-codec` | 1.5.5 | MIT |
| transitive, graphe de production | `@jridgewell/trace-mapping` | 0.3.31 | MIT |
| transitive, graphe de production | `@lezer/common` | 1.5.2 | MIT |
| transitive, graphe de production | `@lezer/highlight` | 1.2.3 | MIT |
| transitive, graphe de production | `@lezer/lr` | 1.4.10 | MIT |
| transitive, graphe de production | `@marijn/find-cluster-break` | 1.0.4 | MIT |
| transitive, graphe de production | `@openuxkit/styled` | 1.0.0 | MIT |
| transitive, graphe de production | `@openuxkit/styles` | 1.1.0 | MIT |
| transitive, graphe de production | `@openuxkit/themes` | 1.1.0 | MIT |
| transitive, graphe de production | `@openuxkit/utils` | 1.0.1 | MIT |
| transitive, graphe de production | `@openvue/core` | 1.0.0 | MIT |
| transitive, graphe de production | `@openvue/icons` | 1.0.0 | MIT |
| transitive, graphe de production | `@tiptap/core` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-blockquote` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-bold` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-bubble-menu` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-bullet-list` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-code` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-code-block` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-document` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-dropcursor` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-floating-menu` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-gapcursor` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-hard-break` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-heading` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-horizontal-rule` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-italic` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-link` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-list` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-list-item` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-list-keymap` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-ordered-list` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-paragraph` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-strike` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-text` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extension-underline` | 3.31.4 | MIT |
| transitive, graphe de production | `@tiptap/extensions` | 3.31.4 | MIT |
| transitive, graphe de production | `@types/jsesc` | 2.5.1 | MIT |
| transitive, graphe de production | `@vue-macros/common` | 3.1.4 | MIT |
| transitive, graphe de production | `@vue/compiler-core` | 3.5.41 | MIT |
| transitive, graphe de production | `@vue/compiler-dom` | 3.5.41 | MIT |
| transitive, graphe de production | `@vue/compiler-sfc` | 3.5.41 | MIT |
| transitive, graphe de production | `@vue/compiler-ssr` | 3.5.41 | MIT |
| transitive, graphe de production | `@vue/devtools-api` | 8.2.1 | MIT |
| transitive, graphe de production | `@vue/devtools-kit` | 8.2.1 | MIT |
| transitive, graphe de production | `@vue/devtools-shared` | 8.2.1 | MIT |
| transitive, graphe de production | `@vue/reactivity` | 3.5.41 | MIT |
| transitive, graphe de production | `@vue/runtime-core` | 3.5.41 | MIT |
| transitive, graphe de production | `@vue/runtime-dom` | 3.5.41 | MIT |
| transitive, graphe de production | `@vue/server-renderer` | 3.5.41 | MIT |
| transitive, graphe de production | `@vue/shared` | 3.5.41 | MIT |
| transitive, graphe de production | `acorn` | 8.18.0 | MIT |
| transitive, graphe de production | `agent-base` | 6.0.2 | MIT |
| transitive, graphe de production | `array-move` | 4.0.0 | MIT |
| transitive, graphe de production | `ast-kit` | 2.2.0 | MIT |
| transitive, graphe de production | `ast-walker-scope` | 0.9.0 | MIT |
| transitive, graphe de production | `asynckit` | 0.4.0 | MIT |
| transitive, graphe de production | `birpc` | 2.9.0 | MIT |
| transitive, graphe de production | `bpmn-moddle` | 10.1.0 | MIT |
| transitive, graphe de production | `call-bind-apply-helpers` | 1.0.2 | MIT |
| transitive, graphe de production | `chokidar` | 5.0.0 | MIT |
| transitive, graphe de production | `clsx` | 2.1.1 | MIT |
| transitive, graphe de production | `combined-stream` | 1.0.8 | MIT |
| transitive, graphe de production | `component-props` | 1.1.1 | champ licence vide (README : MIT) |
| transitive, graphe de production | `component-xor` | 0.0.4 | MIT |
| transitive, graphe de production | `confbox` | 0.2.4 | MIT |
| transitive, graphe de production | `crelt` | 1.0.7 | MIT |
| transitive, graphe de production | `css.escape` | 1.5.1 | MIT |
| transitive, graphe de production | `csstype` | 3.2.3 | MIT |
| transitive, graphe de production | `debug` | 4.4.3 | MIT |
| transitive, graphe de production | `delayed-stream` | 1.0.0 | MIT |
| transitive, graphe de production | `diagram-js` | 15.24.0 | MIT |
| transitive, graphe de production | `diagram-js-direct-editing` | 3.5.1 | MIT |
| transitive, graphe de production | `didi` | 11.0.0 | MIT |
| transitive, graphe de production | `dmn-js-boxed-expression` | 17.10.2 | SEE LICENSE IN LICENSE |
| transitive, graphe de production | `dmn-js-decision-table` | 17.10.2 | SEE LICENSE IN LICENSE |
| transitive, graphe de production | `dmn-js-drd` | 17.10.2 | SEE LICENSE IN LICENSE |
| transitive, graphe de production | `dmn-js-literal-expression` | 17.10.2 | SEE LICENSE IN LICENSE |
| transitive, graphe de production | `dmn-js-shared` | 17.10.2 | SEE LICENSE IN LICENSE |
| transitive, graphe de production | `dmn-moddle` | 12.1.0 | MIT |
| transitive, graphe de production | `dom-iterator` | 1.0.2 | MIT |
| transitive, graphe de production | `domify` | 3.0.0 | MIT |
| transitive, graphe de production | `dunder-proto` | 1.0.1 | MIT |
| transitive, graphe de production | `entities` | 7.0.1 | BSD-2-Clause |
| transitive, graphe de production | `es-define-property` | 1.0.1 | MIT |
| transitive, graphe de production | `es-errors` | 1.3.0 | MIT |
| transitive, graphe de production | `es-object-atoms` | 1.1.2 | MIT |
| transitive, graphe de production | `es-set-tostringtag` | 2.1.0 | MIT |
| transitive, graphe de production | `escape-html` | 1.0.3 | MIT |
| transitive, graphe de production | `estree-walker` | 2.0.2 | MIT |
| transitive, graphe de production | `exsolve` | 1.1.1 | MIT |
| transitive, graphe de production | `fdir` | 6.5.0 | MIT |
| transitive, graphe de production | `follow-redirects` | 1.16.0 | MIT |
| transitive, graphe de production | `form-data` | 4.0.6 | MIT |
| transitive, graphe de production | `function-bind` | 1.1.2 | MIT |
| transitive, graphe de production | `get-intrinsic` | 1.3.0 | MIT |
| transitive, graphe de production | `get-proto` | 1.0.1 | MIT |
| transitive, graphe de production | `gopd` | 1.2.0 | MIT |
| transitive, graphe de production | `has-symbols` | 1.1.0 | MIT |
| transitive, graphe de production | `has-tostringtag` | 1.0.2 | MIT |
| transitive, graphe de production | `hasown` | 2.0.4 | MIT |
| transitive, graphe de production | `hookable` | 5.5.3 | MIT |
| transitive, graphe de production | `htm` | 3.1.1 | Apache-2.0 |
| transitive, graphe de production | `https-proxy-agent` | 5.0.1 | MIT |
| transitive, graphe de production | `ids` | 3.0.2 | MIT |
| transitive, graphe de production | `inferno` | 5.6.3 | MIT |
| transitive, graphe de production | `inferno-shared` | 5.6.3 | MIT |
| transitive, graphe de production | `inferno-vnode-flags` | 5.6.3 | MIT |
| transitive, graphe de production | `inherits-browser` | 0.1.0 | ISC |
| transitive, graphe de production | `jsesc` | 3.1.0 | MIT |
| transitive, graphe de production | `json5` | 2.2.3 | MIT |
| transitive, graphe de production | `linkifyjs` | 4.3.3 | MIT |
| transitive, graphe de production | `local-pkg` | 1.2.1 | MIT |
| transitive, graphe de production | `magic-string` | 0.30.21 | MIT |
| transitive, graphe de production | `magic-string-ast` | 1.0.3 | MIT |
| transitive, graphe de production | `math-intrinsics` | 1.1.0 | MIT |
| transitive, graphe de production | `mime-db` | 1.52.0 | MIT |
| transitive, graphe de production | `mime-types` | 2.1.35 | MIT |
| transitive, graphe de production | `min-dash` | 5.1.0 | MIT |
| transitive, graphe de production | `min-dom` | 5.3.0 | MIT |
| transitive, graphe de production | `mitt` | 3.0.1 | MIT |
| transitive, graphe de production | `mlly` | 1.8.2 | MIT |
| transitive, graphe de production | `moddle` | 8.2.1 | MIT |
| transitive, graphe de production | `moddle-xml` | 12.2.0 | MIT |
| transitive, graphe de production | `ms` | 2.1.3 | MIT |
| transitive, graphe de production | `muggle-string` | 0.4.1 | MIT |
| transitive, graphe de production | `nanoid` | 3.3.18 | MIT |
| transitive, graphe de production | `nostics` | 1.2.0 | MIT |
| transitive, graphe de production | `object-refs` | 0.4.0 | MIT |
| transitive, graphe de production | `opencollective-postinstall` | 2.0.3 | MIT |
| transitive, graphe de production | `orderedmap` | 2.1.1 | MIT |
| transitive, graphe de production | `path-intersection` | 4.1.0 | MIT |
| transitive, graphe de production | `pathe` | 2.0.3 | MIT |
| transitive, graphe de production | `perfect-debounce` | 2.1.0 | MIT |
| transitive, graphe de production | `picocolors` | 1.1.1 | ISC |
| transitive, graphe de production | `picomatch` | 4.0.5 | MIT |
| transitive, graphe de production | `pkg-types` | 2.3.1 | MIT |
| transitive, graphe de production | `postcss` | 8.5.26 | MIT |
| transitive, graphe de production | `preact` | 10.29.8 | MIT |
| transitive, graphe de production | `prosemirror-changeset` | 2.4.4 | MIT |
| transitive, graphe de production | `prosemirror-commands` | 1.7.2 | MIT |
| transitive, graphe de production | `prosemirror-dropcursor` | 1.8.4 | MIT |
| transitive, graphe de production | `prosemirror-gapcursor` | 1.4.1 | MIT |
| transitive, graphe de production | `prosemirror-history` | 1.5.1 | MIT |
| transitive, graphe de production | `prosemirror-inputrules` | 1.5.1 | MIT |
| transitive, graphe de production | `prosemirror-keymap` | 1.2.3 | MIT |
| transitive, graphe de production | `prosemirror-model` | 1.25.12 | MIT |
| transitive, graphe de production | `prosemirror-schema-list` | 1.5.1 | MIT |
| transitive, graphe de production | `prosemirror-state` | 1.4.4 | MIT |
| transitive, graphe de production | `prosemirror-tables` | 1.8.5 | MIT |
| transitive, graphe de production | `prosemirror-transform` | 1.12.2 | MIT |
| transitive, graphe de production | `prosemirror-view` | 1.42.6 | MIT |
| transitive, graphe de production | `proxy-from-env` | 2.1.0 | MIT |
| transitive, graphe de production | `quansync` | 0.2.11 | MIT |
| transitive, graphe de production | `readdirp` | 5.1.1 | MIT |
| transitive, graphe de production | `rope-sequence` | 1.3.4 | MIT |
| transitive, graphe de production | `saxen` | 11.1.1 | MIT |
| transitive, graphe de production | `scule` | 1.3.0 | MIT |
| transitive, graphe de production | `selection-ranges` | 4.1.1 | MIT |
| transitive, graphe de production | `selection-update` | 1.1.1 | MIT |
| transitive, graphe de production | `semver` | 7.8.5 | ISC |
| transitive, graphe de production | `source-map-js` | 1.2.1 | BSD-3-Clause |
| transitive, graphe de production | `style-mod` | 4.1.3 | MIT |
| transitive, graphe de production | `table-js` | 9.4.0 | MIT |
| transitive, graphe de production | `tiny-svg` | 4.1.4 | MIT |
| transitive, graphe de production | `tinyglobby` | 0.2.17 | MIT |
| transitive, graphe de production | `ufo` | 1.6.4 | MIT |
| transitive, graphe de production | `unplugin` | 3.3.0 | MIT |
| transitive, graphe de production | `unplugin-utils` | 0.3.2 | MIT |
| transitive, graphe de production | `w3c-keyname` | 2.2.8 | MIT |
| transitive, graphe de production | `webpack-virtual-modules` | 0.6.2 | MIT |
| transitive, graphe de production | `yaml` | 2.9.0 | ISC |
| dépendance directe de dev/test | `@playwright/test` | 1.62.1 | Apache-2.0 |
| dépendance directe de dev/test | `@types/node` | 24.13.3 | MIT |
| dépendance directe de dev/test | `@vitejs/plugin-vue` | 6.0.8 | MIT |
| dépendance directe de dev/test | `@vitest/coverage-v8` | 4.1.10 | MIT |
| dépendance directe de dev/test | `@vue/eslint-config-typescript` | 14.9.0 | MIT |
| dépendance directe de dev/test | `@vue/test-utils` | 2.4.11 | MIT |
| dépendance directe de dev/test | `@vue/tsconfig` | 0.9.1 | MIT |
| dépendance directe de dev/test | `eslint` | 9.39.5 | MIT |
| dépendance directe de dev/test | `eslint-plugin-vue` | 10.10.0 | MIT |
| dépendance directe de dev/test | `html-encoding-sniffer` | 6.0.0 | MIT |
| dépendance directe de dev/test | `jiti` | 2.7.0 | MIT |
| dépendance directe de dev/test | `jsdom` | 29.1.1 | MIT |
| dépendance directe de dev/test | `prettier` | 3.9.6 | MIT |
| dépendance directe de dev/test | `typescript` | 6.0.3 | Apache-2.0 |
| dépendance directe de dev/test | `typescript-eslint` | 8.67.0 | MIT |
| dépendance directe de dev/test | `vite` | 8.2.1 | MIT |
| dépendance directe de dev/test | `vitest` | 4.1.10 | MIT |
| dépendance directe de dev/test | `vue-tsc` | 3.3.9 | MIT |
| build/test, hors livrable | `@asamuzakjp/css-color` | 5.1.11 | MIT |
| build/test, hors livrable | `@asamuzakjp/dom-selector` | 7.1.1 | MIT |
| build/test, hors livrable | `@asamuzakjp/generational-cache` | 1.0.1 | MIT |
| build/test, hors livrable | `@asamuzakjp/nwsapi` | 2.3.9 | MIT |
| build/test, hors livrable | `@bcoe/v8-coverage` | 1.0.2 | MIT |
| build/test, hors livrable | `@bpmn-io/feelers-editor` | 1.1.1 | MIT |
| build/test, hors livrable | `@bpmn-io/feelers-lint` | 1.0.0 | MIT |
| build/test, hors livrable | `@bpmn-io/lang-feelers` | 1.0.0 | MIT |
| build/test, hors livrable | `@bpmn-io/lezer-feelers` | 1.0.0 | MIT |
| build/test, hors livrable | `@bramus/specificity` | 2.4.2 | MIT |
| build/test, hors livrable | `@codemirror/lang-json` | 6.0.2 | MIT |
| build/test, hors livrable | `@csstools/color-helpers` | 6.1.0 | MIT-0 |
| build/test, hors livrable | `@csstools/css-calc` | 3.3.0 | MIT |
| build/test, hors livrable | `@csstools/css-color-parser` | 4.1.10 | MIT |
| build/test, hors livrable | `@csstools/css-parser-algorithms` | 4.0.0 | MIT |
| build/test, hors livrable | `@csstools/css-syntax-patches-for-csstree` | 1.1.7 | MIT-0 |
| build/test, hors livrable | `@csstools/css-tokenizer` | 4.0.0 | MIT |
| build/test, hors livrable | `@eslint-community/eslint-utils` | 4.10.1 | MIT |
| build/test, hors livrable | `@eslint-community/regexpp` | 4.12.2 | MIT |
| build/test, hors livrable | `@eslint/config-array` | 0.21.2 | Apache-2.0 |
| build/test, hors livrable | `@eslint/config-helpers` | 0.4.2 | Apache-2.0 |
| build/test, hors livrable | `@eslint/core` | 0.17.0 | Apache-2.0 |
| build/test, hors livrable | `@eslint/eslintrc` | 3.3.6 | MIT |
| build/test, hors livrable | `@eslint/js` | 9.39.5 | MIT |
| build/test, hors livrable | `@eslint/object-schema` | 2.1.7 | Apache-2.0 |
| build/test, hors livrable | `@eslint/plugin-kit` | 0.4.1 | Apache-2.0 |
| build/test, hors livrable | `@exodus/bytes` | 1.15.1 | MIT |
| build/test, hors livrable | `@humanfs/core` | 0.19.2 | Apache-2.0 |
| build/test, hors livrable | `@humanfs/node` | 0.16.8 | Apache-2.0 |
| build/test, hors livrable | `@humanfs/types` | 0.15.0 | Apache-2.0 |
| build/test, hors livrable | `@humanwhocodes/module-importer` | 1.0.1 | Apache-2.0 |
| build/test, hors livrable | `@humanwhocodes/retry` | 0.4.3 | Apache-2.0 |
| build/test, hors livrable | `@isaacs/cliui` | 8.0.2 | ISC |
| build/test, hors livrable | `@lezer/html` | 1.3.13 | MIT |
| build/test, hors livrable | `@lezer/json` | 1.0.3 | MIT |
| build/test, hors livrable | `@lezer/markdown` | 1.7.2 | MIT |
| build/test, hors livrable | `@nodelib/fs.scandir` | 2.1.5 | MIT |
| build/test, hors livrable | `@nodelib/fs.stat` | 2.0.5 | MIT |
| build/test, hors livrable | `@nodelib/fs.walk` | 1.2.8 | MIT |
| build/test, hors livrable | `@one-ini/wasm` | 0.1.1 | MIT |
| build/test, hors livrable | `@oxc-project/types` | 0.144.0 | MIT |
| build/test, hors livrable | `@pkgjs/parseargs` | 0.11.0 | MIT |
| build/test, hors livrable | `@rolldown/binding-android-arm64` | 1.2.4 | MIT |
| build/test, hors livrable | `@rolldown/binding-darwin-arm64` | 1.2.4 | MIT |
| build/test, hors livrable | `@rolldown/binding-darwin-x64` | 1.2.4 | MIT |
| build/test, hors livrable | `@rolldown/binding-freebsd-x64` | 1.2.4 | MIT |
| build/test, hors livrable | `@rolldown/binding-linux-arm-gnueabihf` | 1.2.4 | MIT |
| build/test, hors livrable | `@rolldown/binding-linux-arm64-gnu` | 1.2.4 | MIT |
| build/test, hors livrable | `@rolldown/binding-linux-arm64-musl` | 1.2.4 | MIT |
| build/test, hors livrable | `@rolldown/binding-linux-ppc64-gnu` | 1.2.4 | MIT |
| build/test, hors livrable | `@rolldown/binding-linux-s390x-gnu` | 1.2.4 | MIT |
| build/test, hors livrable | `@rolldown/binding-linux-x64-gnu` | 1.2.4 | MIT |
| build/test, hors livrable | `@rolldown/binding-linux-x64-musl` | 1.2.4 | MIT |
| build/test, hors livrable | `@rolldown/binding-openharmony-arm64` | 1.2.4 | MIT |
| build/test, hors livrable | `@rolldown/binding-win32-arm64-msvc` | 1.2.4 | MIT |
| build/test, hors livrable | `@rolldown/binding-win32-x64-msvc` | 1.2.4 | MIT |
| build/test, hors livrable | `@rolldown/pluginutils` | 1.0.1 | MIT |
| build/test, hors livrable | `@standard-schema/spec` | 1.1.0 | MIT |
| build/test, hors livrable | `@types/chai` | 5.2.3 | MIT |
| build/test, hors livrable | `@types/deep-eql` | 4.0.2 | MIT |
| build/test, hors livrable | `@types/esrecurse` | 4.3.1 | MIT |
| build/test, hors livrable | `@types/estree` | 1.0.9 | MIT |
| build/test, hors livrable | `@types/json-schema` | 7.0.15 | MIT |
| build/test, hors livrable | `@typescript-eslint/eslint-plugin` | 8.67.0 | MIT |
| build/test, hors livrable | `@typescript-eslint/parser` | 8.67.0 | MIT |
| build/test, hors livrable | `@typescript-eslint/project-service` | 8.67.0 | MIT |
| build/test, hors livrable | `@typescript-eslint/scope-manager` | 8.67.0 | MIT |
| build/test, hors livrable | `@typescript-eslint/tsconfig-utils` | 8.67.0 | MIT |
| build/test, hors livrable | `@typescript-eslint/type-utils` | 8.67.0 | MIT |
| build/test, hors livrable | `@typescript-eslint/types` | 8.67.0 | MIT |
| build/test, hors livrable | `@typescript-eslint/typescript-estree` | 8.67.0 | MIT |
| build/test, hors livrable | `@typescript-eslint/utils` | 8.67.0 | MIT |
| build/test, hors livrable | `@typescript-eslint/visitor-keys` | 8.67.0 | MIT |
| build/test, hors livrable | `@vitest/expect` | 4.1.10 | MIT |
| build/test, hors livrable | `@vitest/mocker` | 4.1.10 | MIT |
| build/test, hors livrable | `@vitest/pretty-format` | 4.1.10 | MIT |
| build/test, hors livrable | `@vitest/runner` | 4.1.10 | MIT |
| build/test, hors livrable | `@vitest/snapshot` | 4.1.10 | MIT |
| build/test, hors livrable | `@vitest/spy` | 4.1.10 | MIT |
| build/test, hors livrable | `@vitest/utils` | 4.1.10 | MIT |
| build/test, hors livrable | `@volar/language-core` | 2.4.28 | MIT |
| build/test, hors livrable | `@volar/source-map` | 2.4.28 | MIT |
| build/test, hors livrable | `@volar/typescript` | 2.4.28 | MIT |
| build/test, hors livrable | `@vue/language-core` | 3.3.9 | MIT |
| build/test, hors livrable | `abbrev` | 2.0.0 | ISC |
| build/test, hors livrable | `acorn-jsx` | 5.3.2 | MIT |
| build/test, hors livrable | `ajv` | 6.15.0 | MIT |
| build/test, hors livrable | `alien-signals` | 3.2.1 | MIT |
| build/test, hors livrable | `ansi-regex` | 6.2.2 | MIT |
| build/test, hors livrable | `ansi-styles` | 6.2.3 | MIT |
| build/test, hors livrable | `argparse` | 2.0.1 | Python-2.0 |
| build/test, hors livrable | `assertion-error` | 2.0.1 | MIT |
| build/test, hors livrable | `ast-v8-to-istanbul` | 1.0.5 | MIT |
| build/test, hors livrable | `balanced-match` | 4.0.4 | MIT |
| build/test, hors livrable | `bidi-js` | 1.0.3 | MIT |
| build/test, hors livrable | `boolbase` | 1.0.0 | ISC |
| build/test, hors livrable | `brace-expansion` | 5.0.9 | MIT |
| build/test, hors livrable | `braces` | 3.0.3 | MIT |
| build/test, hors livrable | `callsites` | 3.1.0 | MIT |
| build/test, hors livrable | `camunda-bpmn-js-behaviors` | 1.18.0 | MIT |
| build/test, hors livrable | `camunda-bpmn-moddle` | 7.0.1 | MIT |
| build/test, hors livrable | `chai` | 6.2.2 | MIT |
| build/test, hors livrable | `chalk` | 4.1.2 | MIT |
| build/test, hors livrable | `classnames` | 2.5.1 | MIT |
| build/test, hors livrable | `color-convert` | 2.0.1 | MIT |
| build/test, hors livrable | `color-name` | 1.1.4 | MIT |
| build/test, hors livrable | `commander` | 10.0.1 | MIT |
| build/test, hors livrable | `concat-map` | 0.0.1 | MIT |
| build/test, hors livrable | `config-chain` | 1.1.13 | MIT |
| build/test, hors livrable | `convert-source-map` | 2.0.0 | MIT |
| build/test, hors livrable | `cross-spawn` | 7.0.6 | MIT |
| build/test, hors livrable | `css-tree` | 3.2.1 | MIT |
| build/test, hors livrable | `cssesc` | 3.0.0 | MIT |
| build/test, hors livrable | `data-urls` | 7.0.0 | MIT |
| build/test, hors livrable | `decimal.js` | 10.6.0 | MIT |
| build/test, hors livrable | `deep-is` | 0.1.4 | MIT |
| build/test, hors livrable | `detect-libc` | 2.1.2 | Apache-2.0 |
| build/test, hors livrable | `eastasianwidth` | 0.2.0 | MIT |
| build/test, hors livrable | `editorconfig` | 1.0.7 | MIT |
| build/test, hors livrable | `emoji-regex` | 9.2.2 | MIT |
| build/test, hors livrable | `es-module-lexer` | 2.3.1 | MIT |
| build/test, hors livrable | `escape-string-regexp` | 4.0.0 | MIT |
| build/test, hors livrable | `eslint-scope` | 9.1.2 | BSD-2-Clause |
| build/test, hors livrable | `eslint-visitor-keys` | 3.4.3 | Apache-2.0 |
| build/test, hors livrable | `espree` | 11.2.0 | BSD-2-Clause |
| build/test, hors livrable | `esquery` | 1.7.0 | BSD-3-Clause |
| build/test, hors livrable | `esrecurse` | 4.3.0 | BSD-2-Clause |
| build/test, hors livrable | `estraverse` | 5.3.0 | BSD-2-Clause |
| build/test, hors livrable | `esutils` | 2.0.3 | BSD-2-Clause |
| build/test, hors livrable | `expect-type` | 1.4.0 | Apache-2.0 |
| build/test, hors livrable | `fast-deep-equal` | 3.1.3 | MIT |
| build/test, hors livrable | `fast-glob` | 3.3.3 | MIT |
| build/test, hors livrable | `fast-json-stable-stringify` | 2.1.0 | MIT |
| build/test, hors livrable | `fast-levenshtein` | 2.0.6 | MIT |
| build/test, hors livrable | `fastq` | 1.20.1 | ISC |
| build/test, hors livrable | `file-entry-cache` | 8.0.0 | MIT |
| build/test, hors livrable | `fill-range` | 7.1.1 | MIT |
| build/test, hors livrable | `find-up` | 5.0.0 | MIT |
| build/test, hors livrable | `flat-cache` | 4.0.1 | MIT |
| build/test, hors livrable | `flatted` | 3.4.4 | ISC |
| build/test, hors livrable | `focus-trap` | 8.2.2 | MIT |
| build/test, hors livrable | `foreground-child` | 3.3.1 | ISC |
| build/test, hors livrable | `fsevents` | 2.3.3 | MIT |
| build/test, hors livrable | `glob` | 10.5.0 | ISC |
| build/test, hors livrable | `glob-parent` | 6.0.2 | ISC |
| build/test, hors livrable | `globals` | 14.0.0 | MIT |
| build/test, hors livrable | `has-flag` | 4.0.0 | MIT |
| build/test, hors livrable | `html-escaper` | 2.0.2 | MIT |
| build/test, hors livrable | `ignore` | 5.3.2 | MIT |
| build/test, hors livrable | `import-fresh` | 3.3.1 | MIT |
| build/test, hors livrable | `imurmurhash` | 0.1.4 | MIT |
| build/test, hors livrable | `ini` | 1.3.8 | ISC |
| build/test, hors livrable | `is-extglob` | 2.1.1 | MIT |
| build/test, hors livrable | `is-fullwidth-code-point` | 3.0.0 | MIT |
| build/test, hors livrable | `is-glob` | 4.0.3 | MIT |
| build/test, hors livrable | `is-number` | 7.0.0 | MIT |
| build/test, hors livrable | `is-potential-custom-element-name` | 1.0.1 | MIT |
| build/test, hors livrable | `isexe` | 2.0.0 | ISC |
| build/test, hors livrable | `istanbul-lib-coverage` | 3.2.2 | BSD-3-Clause |
| build/test, hors livrable | `istanbul-lib-report` | 3.0.1 | BSD-3-Clause |
| build/test, hors livrable | `istanbul-reports` | 3.2.0 | BSD-3-Clause |
| build/test, hors livrable | `jackspeak` | 3.4.3 | BlueOak-1.0.0 |
| build/test, hors livrable | `js-beautify` | 1.15.4 | MIT |
| build/test, hors livrable | `js-cookie` | 3.0.8 | MIT |
| build/test, hors livrable | `js-tokens` | 10.0.0 | MIT |
| build/test, hors livrable | `js-yaml` | 4.3.1 | MIT |
| build/test, hors livrable | `json-buffer` | 3.0.1 | MIT |
| build/test, hors livrable | `json-schema-traverse` | 0.4.1 | MIT |
| build/test, hors livrable | `json-stable-stringify-without-jsonify` | 1.0.1 | MIT |
| build/test, hors livrable | `keyv` | 4.5.4 | MIT |
| build/test, hors livrable | `levn` | 0.4.1 | MIT |
| build/test, hors livrable | `lightningcss` | 1.33.0 | MPL-2.0 |
| build/test, hors livrable | `lightningcss-android-arm64` | 1.33.0 | MPL-2.0 |
| build/test, hors livrable | `lightningcss-darwin-arm64` | 1.33.0 | MPL-2.0 |
| build/test, hors livrable | `lightningcss-darwin-x64` | 1.33.0 | MPL-2.0 |
| build/test, hors livrable | `lightningcss-freebsd-x64` | 1.33.0 | MPL-2.0 |
| build/test, hors livrable | `lightningcss-linux-arm-gnueabihf` | 1.33.0 | MPL-2.0 |
| build/test, hors livrable | `lightningcss-linux-arm64-gnu` | 1.33.0 | MPL-2.0 |
| build/test, hors livrable | `lightningcss-linux-arm64-musl` | 1.33.0 | MPL-2.0 |
| build/test, hors livrable | `lightningcss-linux-x64-gnu` | 1.33.0 | MPL-2.0 |
| build/test, hors livrable | `lightningcss-linux-x64-musl` | 1.33.0 | MPL-2.0 |
| build/test, hors livrable | `lightningcss-win32-arm64-msvc` | 1.33.0 | MPL-2.0 |
| build/test, hors livrable | `lightningcss-win32-x64-msvc` | 1.33.0 | MPL-2.0 |
| build/test, hors livrable | `locate-path` | 6.0.0 | MIT |
| build/test, hors livrable | `lodash.merge` | 4.6.2 | MIT |
| build/test, hors livrable | `lru-cache` | 11.5.2 | BlueOak-1.0.0 |
| build/test, hors livrable | `magicast` | 0.5.4 | MIT |
| build/test, hors livrable | `make-dir` | 4.0.0 | MIT |
| build/test, hors livrable | `mdn-data` | 2.27.1 | CC0-1.0 |
| build/test, hors livrable | `merge2` | 1.4.1 | MIT |
| build/test, hors livrable | `micromatch` | 4.0.8 | MIT |
| build/test, hors livrable | `minimatch` | 10.2.6 | BlueOak-1.0.0 |
| build/test, hors livrable | `minipass` | 7.1.3 | BlueOak-1.0.0 |
| build/test, hors livrable | `natural-compare` | 1.4.0 | MIT |
| build/test, hors livrable | `nopt` | 7.2.1 | ISC |
| build/test, hors livrable | `nth-check` | 2.1.1 | BSD-2-Clause |
| build/test, hors livrable | `obug` | 2.1.4 | MIT |
| build/test, hors livrable | `optionator` | 0.9.4 | MIT |
| build/test, hors livrable | `p-limit` | 3.1.0 | MIT |
| build/test, hors livrable | `p-locate` | 5.0.0 | MIT |
| build/test, hors livrable | `package-json-from-dist` | 1.0.1 | BlueOak-1.0.0 |
| build/test, hors livrable | `parent-module` | 1.0.1 | MIT |
| build/test, hors livrable | `parse5` | 8.0.1 | MIT |
| build/test, hors livrable | `path-browserify` | 1.0.1 | MIT |
| build/test, hors livrable | `path-exists` | 4.0.0 | MIT |
| build/test, hors livrable | `path-key` | 3.1.1 | MIT |
| build/test, hors livrable | `path-scurry` | 1.11.1 | BlueOak-1.0.0 |
| build/test, hors livrable | `playwright` | 1.62.1 | Apache-2.0 |
| build/test, hors livrable | `playwright-core` | 1.62.1 | Apache-2.0 |
| build/test, hors livrable | `postcss-selector-parser` | 7.1.5 | MIT |
| build/test, hors livrable | `prelude-ls` | 1.2.1 | MIT |
| build/test, hors livrable | `proto-list` | 1.2.4 | ISC |
| build/test, hors livrable | `punycode` | 2.3.1 | MIT |
| build/test, hors livrable | `queue-microtask` | 1.2.3 | MIT |
| build/test, hors livrable | `require-from-string` | 2.0.2 | MIT |
| build/test, hors livrable | `resolve-from` | 4.0.0 | MIT |
| build/test, hors livrable | `reusify` | 1.1.0 | MIT |
| build/test, hors livrable | `rolldown` | 1.2.4 | MIT |
| build/test, hors livrable | `run-parallel` | 1.2.0 | MIT |
| build/test, hors livrable | `saxes` | 6.0.0 | ISC |
| build/test, hors livrable | `shebang-command` | 2.0.0 | MIT |
| build/test, hors livrable | `shebang-regex` | 3.0.0 | MIT |
| build/test, hors livrable | `siginfo` | 2.0.0 | ISC |
| build/test, hors livrable | `signal-exit` | 4.1.0 | ISC |
| build/test, hors livrable | `stackback` | 0.0.2 | MIT |
| build/test, hors livrable | `std-env` | 4.2.0 | MIT |
| build/test, hors livrable | `string-width` | 5.1.2 | MIT |
| build/test, hors livrable | `string-width-cjs` | 4.2.3 | MIT |
| build/test, hors livrable | `strip-ansi` | 7.2.0 | MIT |
| build/test, hors livrable | `strip-ansi-cjs` | 6.0.1 | MIT |
| build/test, hors livrable | `strip-json-comments` | 3.1.1 | MIT |
| build/test, hors livrable | `supports-color` | 7.2.0 | MIT |
| build/test, hors livrable | `symbol-tree` | 3.2.4 | MIT |
| build/test, hors livrable | `tabbable` | 6.5.0 | MIT |
| build/test, hors livrable | `tinybench` | 2.9.0 | MIT |
| build/test, hors livrable | `tinyexec` | 1.3.0 | MIT |
| build/test, hors livrable | `tinyrainbow` | 3.1.1 | MIT |
| build/test, hors livrable | `tldts` | 7.4.10 | MIT |
| build/test, hors livrable | `tldts-core` | 7.4.10 | MIT |
| build/test, hors livrable | `to-regex-range` | 5.0.1 | MIT |
| build/test, hors livrable | `tough-cookie` | 6.0.2 | BSD-3-Clause |
| build/test, hors livrable | `tr46` | 6.0.0 | MIT |
| build/test, hors livrable | `ts-api-utils` | 2.5.0 | MIT |
| build/test, hors livrable | `type-check` | 0.4.0 | MIT |
| build/test, hors livrable | `undici` | 7.29.0 | MIT |
| build/test, hors livrable | `undici-types` | 7.18.2 | MIT |
| build/test, hors livrable | `uri-js` | 4.4.1 | BSD-2-Clause |
| build/test, hors livrable | `util-deprecate` | 1.0.2 | MIT |
| build/test, hors livrable | `vscode-uri` | 3.1.0 | MIT |
| build/test, hors livrable | `vue-component-type-helpers` | 3.3.9 | MIT |
| build/test, hors livrable | `vue-eslint-parser` | 10.4.1 | MIT |
| build/test, hors livrable | `w3c-xmlserializer` | 5.0.0 | MIT |
| build/test, hors livrable | `webidl-conversions` | 8.0.1 | BSD-2-Clause |
| build/test, hors livrable | `whatwg-mimetype` | 5.0.0 | MIT |
| build/test, hors livrable | `whatwg-url` | 16.0.1 | MIT |
| build/test, hors livrable | `which` | 2.0.2 | ISC |
| build/test, hors livrable | `why-is-node-running` | 2.3.0 | MIT |
| build/test, hors livrable | `word-wrap` | 1.2.5 | MIT |
| build/test, hors livrable | `wrap-ansi` | 8.1.0 | MIT |
| build/test, hors livrable | `wrap-ansi-cjs` | 7.0.0 | MIT |
| build/test, hors livrable | `xml-name-validator` | 5.0.0 | Apache-2.0 |
| build/test, hors livrable | `xmlchars` | 2.2.0 | MIT |
| build/test, hors livrable | `yocto-queue` | 0.1.0 | MIT |
| build/test, hors livrable | `zeebe-bpmn-moddle` | 1.18.0 | MIT |
