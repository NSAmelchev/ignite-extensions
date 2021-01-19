/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.performancestatistics;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.UUID;
import org.apache.ignite.internal.processors.cache.query.GridCacheQueryType;
import org.apache.ignite.internal.processors.performancestatistics.FilePerformanceStatisticsReader;
import org.apache.ignite.internal.processors.performancestatistics.OperationType;
import org.apache.ignite.internal.processors.performancestatistics.PerformanceStatisticsHandler;
import org.apache.ignite.internal.util.GridIntList;
import org.apache.ignite.internal.util.typedef.internal.A;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.lang.IgniteUuid;
import org.jetbrains.annotations.Nullable;

import static org.apache.ignite.internal.processors.performancestatistics.OperationType.CACHE_START;
import static org.apache.ignite.internal.processors.performancestatistics.OperationType.JOB;
import static org.apache.ignite.internal.processors.performancestatistics.OperationType.QUERY;
import static org.apache.ignite.internal.processors.performancestatistics.OperationType.QUERY_READS;
import static org.apache.ignite.internal.processors.performancestatistics.OperationType.TASK;
import static org.apache.ignite.internal.processors.performancestatistics.OperationType.TX_COMMIT;
import static org.apache.ignite.internal.processors.performancestatistics.OperationType.TX_ROLLBACK;

/**
 * Performance statistics reader.
 */
public class PerformanceStatisticsReader {
    /**
     * @param args Program arguments or '-h' to get usage help.
     */
    public static void main(String... args) throws Exception {
        ReaderParameters params = parseArguments(args);

        validateParameters(params);

        try (PrintHandler handler = new PrintHandler(params)) {
            PerformanceStatisticsHandler[] handlers = new PerformanceStatisticsHandler[] {handler};

            new FilePerformanceStatisticsReader(handlers).read(Collections.singletonList(new File(params.statFiles)));
        }
    }

    /**
     * Parses arguments or print help.
     *
     * @param args Arguments to parse.
     * @return Program arguments.
     */
    private static ReaderParameters parseArguments(String[] args) {
        if (args == null || args.length == 0 || "--help".equalsIgnoreCase(args[0]) || "-h".equalsIgnoreCase(args[0])) {
            System.out.println("The script is used to read performance statistics files to the console or file." +
                    U.nl() + U.nl() +
                    "Usage: read-statistics.sh path_to_files [--out out_file]" + U.nl() +
                    " path_to_files - Performance statistics file or files directory." + U.nl() +
                    " out_file      - Output file.");

            System.exit(0);
        }

        ReaderParameters params = new ReaderParameters();

        Iterator<String> iter = Arrays.asList(args).iterator();

        params.statFiles = iter.next();

        while (iter.hasNext()) {
            String arg = iter.next();

            if ("--out".equalsIgnoreCase(arg)) {
                if (!iter.hasNext())
                    throw new IllegalArgumentException("Expected output file name.");

                params.outFile = iter.next();
            }
            else
                throw new IllegalArgumentException("Unknown command: " + arg);
        }

        return params;
    }

    /** @param params Validates parameters. */
    private static void validateParameters(ReaderParameters params) {
        File statFiles = new File(params.statFiles);

        A.ensure(statFiles.exists(), "Performance statistics file or files directory does not exists.");

        if (params.outFile != null) {
            File out = new File(params.outFile);

            try {
                boolean created = out.createNewFile();

                if (!created) {
                    throw new IllegalArgumentException("Failed to create output file: file with the given name " +
                        "already exists.");
                }
            }
            catch (IOException e) {
                throw new IllegalArgumentException("Failed to create the output file.", e);
            }
        }
    }

    /** Reader parameters. */
    private static class ReaderParameters {
        /** Performance statistics file or path. */
        private String statFiles;

        /** Output file. */
        @Nullable private String outFile;
    }

    /** Handler to print performance statistics operations. */
    private static class PrintHandler implements PerformanceStatisticsHandler, AutoCloseable {
        /** Print stream. */
        private final PrintStream ps;

        /** String builder. */
        private final StringBuilder sb = new StringBuilder();

        /** @param params Reader parameters. */
        public PrintHandler(ReaderParameters params) {
            if (params.outFile != null) {
                try {
                    ps = new PrintStream(Files.newOutputStream(new File(params.outFile).toPath()));
                }
                catch (IOException e) {
                    throw new IllegalArgumentException("Cannot write to output file.", e);
                }
            }
            else
                ps = System.out;
        }

        /** {@inheritDoc} */
        @Override public void cacheStart(UUID nodeId, int cacheId, String name) {
            print(CACHE_START, "nodeId", nodeId, "cacheId", cacheId, "name", name);
        }

        /** {@inheritDoc} */
        @Override public void cacheOperation(UUID nodeId, OperationType type, int cacheId, long startTime,
        long duration) {
            print(type, "nodeId", nodeId, "cacheId", cacheId, "startTime", startTime, "duration", duration);
        }

        /** {@inheritDoc} */
        @Override public void transaction(UUID nodeId, GridIntList cacheIds, long startTime, long duration,
        boolean commited) {
            print(commited ? TX_COMMIT : TX_ROLLBACK, "nodeId", nodeId, "cacheIds", cacheIds,
                "startTime", startTime, "duration", duration);
        }

        /** {@inheritDoc} */
        @Override public void query(UUID nodeId, GridCacheQueryType type, String text, long id, long startTime,
        long duration, boolean success) {
            print(QUERY, "nodeId", nodeId, "type", type, "text", text, "id", id,
                "startTime", startTime, "duration", duration, "success", success);
        }

        /** {@inheritDoc} */
        @Override public void queryReads(UUID nodeId, GridCacheQueryType type, UUID queryNodeId, long id,
        long logicalReads, long physicalReads) {
            print(QUERY_READS, "nodeId", nodeId, "type", type, "queryNodeId", queryNodeId, "id", id,
                "logicalReads", logicalReads, "physicalReads", physicalReads);
        }

        /** {@inheritDoc} */
        @Override public void task(UUID nodeId, IgniteUuid sesId, String taskName, long startTime, long duration,
        int affPartId) {
            print(TASK, "nodeId", nodeId, "sesId", sesId, "taskName", taskName, "startTime", startTime,
                "duration", duration, "affPartId", affPartId);
        }

        /** {@inheritDoc} */
        @Override public void job(UUID nodeId, IgniteUuid sesId, long queuedTime, long startTime, long duration,
        boolean timedOut) {
            print(JOB, "nodeId", nodeId, "sesId", sesId, "queuedTime", queuedTime, "startTime", startTime,
                "duration", duration, "timedOut", timedOut);
        }

        /**
         * Prints operation.
         *
         * @param op Operation.
         * @param tuples Tuples to print (key, value).
         */
        private void print(OperationType op, Object... tuples) {
            assert tuples.length % 2 == 0;

            sb.setLength(0);

            sb.append(op).append(" [");

            for (int i = 0; i < tuples.length; i += 2) {
                sb.append(tuples[i]).append("=").append(tuples[i + 1]);

                if (i < tuples.length - 2)
                    sb.append(", ");
            }

            sb.append(']');

            ps.println(sb.toString());
        }

        /** {@inheritDoc} */
        @Override public void close() {
            if (ps != System.out)
                ps.close();
        }
    }
}
