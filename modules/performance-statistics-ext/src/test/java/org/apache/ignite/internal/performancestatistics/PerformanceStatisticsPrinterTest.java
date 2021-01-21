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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ignite.internal.processors.cache.query.GridCacheQueryType;
import org.apache.ignite.internal.processors.performancestatistics.FilePerformanceStatisticsWriter;
import org.apache.ignite.internal.processors.performancestatistics.OperationType;
import org.apache.ignite.internal.util.GridIntList;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.lang.IgniteUuid;
import org.apache.ignite.testframework.junits.GridTestKernalContext;
import org.apache.ignite.testframework.junits.logger.GridTestLog4jLogger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static java.lang.Integer.parseInt;
import static java.util.stream.Collectors.joining;
import static org.apache.ignite.internal.processors.performancestatistics.FilePerformanceStatisticsWriter.PERF_STAT_DIR;
import static org.apache.ignite.internal.processors.performancestatistics.OperationType.CACHE_GET;
import static org.apache.ignite.internal.processors.performancestatistics.OperationType.CACHE_PUT;
import static org.apache.ignite.internal.processors.performancestatistics.OperationType.CACHE_START;
import static org.apache.ignite.internal.processors.performancestatistics.OperationType.JOB;
import static org.apache.ignite.internal.processors.performancestatistics.OperationType.QUERY;
import static org.apache.ignite.internal.processors.performancestatistics.OperationType.QUERY_READS;
import static org.apache.ignite.internal.processors.performancestatistics.OperationType.TASK;
import static org.apache.ignite.internal.processors.performancestatistics.OperationType.TX_COMMIT;
import static org.apache.ignite.internal.processors.performancestatistics.OperationType.TX_ROLLBACK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests the performance statistics printer.
 */
public class PerformanceStatisticsPrinterTest {
    /** Test node ID. */
    private final static UUID NODE_ID = UUID.randomUUID();

    /** */
    @Before
    public void beforeTest() throws Exception {
        U.delete(new File(U.defaultWorkDirectory()));
    }

    /** */
    @After
    public void afterTest() throws Exception {
        U.delete(new File(U.defaultWorkDirectory()));
    }

    /** @throws Exception If failed. */
    @Test
    public void testOperationsFilter() throws Exception {
        List<OperationType> expOps = F.asList(CACHE_START, CACHE_GET, TX_COMMIT, TX_ROLLBACK,
            QUERY, QUERY_READS, TASK, JOB);

        createStatistics(writer -> {
            writer.cacheStart(0, "cache");
            writer.cacheOperation(CACHE_GET, 0, 0, 0);
            writer.transaction(GridIntList.asList(0), 0, 0, true);
            writer.transaction(GridIntList.asList(0), 0, 0, false);
            writer.query(GridCacheQueryType.SQL_FIELDS, "query", 0, 0, 0, true);
            writer.queryReads(GridCacheQueryType.SQL_FIELDS, NODE_ID, 0, 0, 0);
            writer.task(new IgniteUuid(NODE_ID, 0), "", 0, 0, 0);
            writer.job(new IgniteUuid(NODE_ID, 0), 0, 0, 0, true);
        });

        checkOperationFilter(null, expOps);
        checkOperationFilter(F.asList(CACHE_START), F.asList(CACHE_START));
        checkOperationFilter(F.asList(TASK, JOB), F.asList(TASK, JOB));
        checkOperationFilter(F.asList(CACHE_PUT), Collections.emptyList());
    }

    /** */
    private void checkOperationFilter(List<OperationType> opsParam, List<OperationType> expOps) throws Exception {
        List<String> args = new LinkedList<>();

        if (opsParam != null) {
            args.add("--ops");

            args.add(opsParam.stream().map(Enum::toString).collect(joining(",")));
        }

        List<OperationType> ops = new LinkedList<>(expOps);

        readStatistics(args, json -> {
            OperationType op = OperationType.valueOf(json.get("op").asText());

            assertTrue("Unexpected operation: " + op, ops.remove(op));

            UUID nodeId = UUID.fromString(json.get("nodeId").asText());

            assertEquals(NODE_ID, nodeId);
        });

        assertTrue("Expected operations:" + ops, ops.isEmpty());
    }

    /** @throws Exception If failed. */
    @Test
    public void testStartTimeFilter() throws Exception {
        long startTime1 = 10;
        long startTime2 = 20;

        createStatistics(writer -> {
            for (long startTime : new long[] {startTime1, startTime2}) {
                writer.cacheOperation(CACHE_GET, 0, startTime, 0);
                writer.transaction(GridIntList.asList(0), startTime, 0, true);
                writer.transaction(GridIntList.asList(0), startTime, 0, false);
                writer.query(GridCacheQueryType.SQL_FIELDS, "query", 0, startTime, 0, true);
                writer.task(new IgniteUuid(NODE_ID, 0), "", startTime, 0, 0);
                writer.job(new IgniteUuid(NODE_ID, 0), 0, startTime, 0, true);
            }
        });

        checkStartTimeFilter(null, null, F.asList(startTime1, startTime2));
        checkStartTimeFilter(null, startTime1, F.asList(startTime1));
        checkStartTimeFilter(startTime2, null, F.asList(startTime2));
        checkStartTimeFilter(startTime1, startTime2, F.asList(startTime1, startTime2));
    }

    /** */
    private void checkStartTimeFilter(Long from, Long to, List<Long> expTimes) throws Exception {
        List<OperationType> opsWithStartTime = F.asList(CACHE_GET, TX_COMMIT, TX_ROLLBACK, QUERY, TASK, JOB);

        List<String> args = new LinkedList<>();

        if (from != null) {
            args.add("--from");

            args.add(from.toString());
        }

        if (to != null) {
            args.add("--to");

            args.add(to.toString());
        }

        Map<Long, List<OperationType>> opsByTime = new HashMap<>();

        for (Long time : expTimes)
            opsByTime.put(time, new LinkedList<>(opsWithStartTime));

        readStatistics(args, json -> {
            OperationType op = OperationType.valueOf(json.get("op").asText());

            if (opsWithStartTime.contains(op)) {
                long startTime = json.get("startTime").asLong();

                assertTrue("Unexpected startTime: " + startTime, opsByTime.containsKey(startTime));
                assertTrue("Unexpected operation: " + op, opsByTime.get(startTime).remove(op));
            }
        });

        assertTrue("Expected operations: " + opsByTime, opsByTime.values().stream().allMatch(List::isEmpty));
    }

    /** @throws Exception If failed. */
    @Test
    public void testCacheIdsFilter() throws Exception {
        int cacheId1 = 1;
        int cacheId2 = 2;

        createStatistics(writer -> {
            for (int cacheId : new int[] {cacheId1, cacheId2}) {
                writer.cacheStart(cacheId, "cache-" + cacheId);
                writer.cacheOperation(CACHE_GET, cacheId, 0, 0);
                writer.transaction(GridIntList.asList(cacheId), 0, 0, true);
                writer.transaction(GridIntList.asList(cacheId), 0, 0, false);
            }
        });

        checkCacheIdsFilter(null, new int[] {cacheId1, cacheId2});
        checkCacheIdsFilter(new int[] {cacheId1}, new int[] {cacheId1});
        checkCacheIdsFilter(new int[] {cacheId2}, new int[] {cacheId2});
        checkCacheIdsFilter(new int[] {cacheId1, cacheId2}, new int[] {cacheId1, cacheId2});
        checkCacheIdsFilter(new int[] {-1}, new int[0]);
    }

    /** */
    private void checkCacheIdsFilter(int[] cacheIds, int[] expCacheIds) throws Exception {
        Set<OperationType> cacheIdOps = new HashSet<>(F.asList(CACHE_START, CACHE_GET, TX_COMMIT, TX_ROLLBACK));

        List<String> args = new LinkedList<>();

        if (cacheIds != null) {
            args.add("--cache-ids");

            args.add(Arrays.stream(cacheIds).mapToObj(String::valueOf).collect(joining(",")));
        }

        Map<Integer, List<OperationType>> opsById = new HashMap<>();

        for (Integer id : expCacheIds)
            opsById.put(id, new LinkedList<>(cacheIdOps));

        readStatistics(args, json -> {
            OperationType op = OperationType.valueOf(json.get("op").asText());

            Integer id = null;

            if (OperationType.cacheOperation(op) || op == CACHE_START)
                id = json.get("cacheId").asInt();
            else if (OperationType.transactionOperation(op))
                id = parseInt(json.get("cacheIds").asText().replace("[", "").replace("]", ""));
            else
                fail("Unexpected operation: " + op);

            assertTrue("Unexpected cache id: " + id, opsById.containsKey(id));
            assertTrue("Unexpected operation: " + op, opsById.get(id).remove(op));
        });

        assertTrue("Expected operations: " + opsById, opsById.values().stream().allMatch(List::isEmpty));
    }

    /** Writes statistics through passed writer. */
    private void createStatistics(Consumer<FilePerformanceStatisticsWriter> c) throws Exception {
        FilePerformanceStatisticsWriter writer = new FilePerformanceStatisticsWriter(new TestKernalContext(NODE_ID));

        writer.start();

        c.accept(writer);

        writer.stop();
    }

    /**
     * @param args Additional program arguments.
     * @param c Consumer to handle operations.
     * @throws Exception If failed.
     */
    private void readStatistics(List<String> args, Consumer<JsonNode> c) throws Exception {
        File perfStatDir = new File(U.defaultWorkDirectory(), PERF_STAT_DIR);

        assertTrue(perfStatDir.exists());

        File out = new File(U.defaultWorkDirectory(), "report.txt");

        U.delete(out);

        List<String> pArgs = new LinkedList<>();

        pArgs.add(perfStatDir.getAbsolutePath());
        pArgs.add("--out");
        pArgs.add(out.getAbsolutePath());

        pArgs.addAll(args);

        PerformanceStatisticsPrinter.main(pArgs.toArray(new String[0]));

        assertTrue(out.exists());

        ObjectMapper mapper = new ObjectMapper();

        try (BufferedReader reader = new BufferedReader(new FileReader(out))) {
            String line;

            while ((line = reader.readLine()) != null) {
                JsonNode json = mapper.readTree(line);

                assertTrue(json.isObject());

                UUID nodeId = UUID.fromString(json.get("nodeId").asText());

                assertEquals(NODE_ID, nodeId);

                c.accept(json);
            }
        }
    }

    /** Test kernal context. */
    private static class TestKernalContext extends GridTestKernalContext {
        /** Node ID. */
        private final UUID nodeId;

        /** @param nodeId Node ID. */
        public TestKernalContext(UUID nodeId) {
            super(new GridTestLog4jLogger());

            this.nodeId = nodeId;
        }

        /** {@inheritDoc} */
        @Override public UUID localNodeId() {
            return nodeId;
        }
    }
}
