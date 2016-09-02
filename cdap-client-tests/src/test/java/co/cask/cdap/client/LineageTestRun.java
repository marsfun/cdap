/*
 * Copyright © 2016 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.client;

import co.cask.cdap.client.app.AllProgramsApp;
import co.cask.cdap.common.BadRequestException;
import co.cask.cdap.common.NotFoundException;
import co.cask.cdap.common.app.RunIds;
import co.cask.cdap.common.utils.Tasks;
import co.cask.cdap.common.utils.TimeMathParser;
import co.cask.cdap.data2.metadata.lineage.AccessType;
import co.cask.cdap.data2.metadata.lineage.Lineage;
import co.cask.cdap.data2.metadata.lineage.LineageSerializer;
import co.cask.cdap.data2.metadata.lineage.Relation;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.NamespaceMeta;
import co.cask.cdap.proto.ProgramRunStatus;
import co.cask.cdap.proto.ProgramStatus;
import co.cask.cdap.proto.RunRecord;
import co.cask.cdap.proto.id.ApplicationId;
import co.cask.cdap.proto.id.DatasetId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.ProgramId;
import co.cask.cdap.proto.id.StreamId;
import co.cask.cdap.proto.metadata.MetadataRecord;
import co.cask.cdap.proto.metadata.MetadataScope;
import co.cask.cdap.proto.metadata.lineage.CollapseType;
import co.cask.cdap.proto.metadata.lineage.LineageRecord;
import co.cask.cdap.test.SlowTests;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.apache.twill.api.RunId;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static co.cask.cdap.proto.ProgramStatus.STOPPED;

/**
 * Tests lineage recording and query.
 */
@Category(SlowTests.class)
public class LineageTestRun extends MetadataTestBase {
  private static final Logger LOG = LoggerFactory.getLogger(LineageTestRun.class);

  @Test
  public void testFlowLineage() throws Exception {
    NamespaceId namespace = NamespaceId.fromString("testFlowLineage");
    ApplicationId app = new ApplicationId(namespace.getNamespace(), AllProgramsApp.NAME);
    Id.Flow flow = Id.Flow.from(app.toId(), AllProgramsApp.NoOpFlow.NAME);
    DatasetId dataset = new DatasetId(namespace.getNamespace(), AllProgramsApp.DATASET_NAME);
    StreamId stream = new StreamId(namespace.getNamespace(), AllProgramsApp.STREAM_NAME);

    namespaceClient.create(new NamespaceMeta.Builder().setName(namespace.toId()).build());
    try {
      appClient.deploy(namespace.toId(), createAppJarFile(AllProgramsApp.class));

      // Add metadata to applicaton
      ImmutableMap<String, String> appProperties = ImmutableMap.of("app-key1", "app-value1");
      addProperties(app, appProperties);
      Assert.assertEquals(appProperties, getProperties(app, MetadataScope.USER));
      ImmutableSet<String> appTags = ImmutableSet.of("app-tag1");
      addTags(app, appTags);
      Assert.assertEquals(appTags, getTags(app, MetadataScope.USER));

      // Add metadata to flow
      ImmutableMap<String, String> flowProperties = ImmutableMap.of("flow-key1", "flow-value1");
      addProperties(flow.toEntityId(), flowProperties);
      Assert.assertEquals(flowProperties, getProperties(flow.toEntityId(), MetadataScope.USER));
      ImmutableSet<String> flowTags = ImmutableSet.of("flow-tag1", "flow-tag2");
      addTags(flow.toEntityId(), flowTags);
      Assert.assertEquals(flowTags, getTags(flow.toEntityId(), MetadataScope.USER));

      // Add metadata to dataset
      ImmutableMap<String, String> dataProperties = ImmutableMap.of("data-key1", "data-value1");
      addProperties(dataset, dataProperties);
      Assert.assertEquals(dataProperties, getProperties(dataset, MetadataScope.USER));
      ImmutableSet<String> dataTags = ImmutableSet.of("data-tag1", "data-tag2");
      addTags(dataset, dataTags);
      Assert.assertEquals(dataTags, getTags(dataset, MetadataScope.USER));

      // Add metadata to stream
      ImmutableMap<String, String> streamProperties = ImmutableMap.of("stream-key1", "stream-value1");
      addProperties(stream, streamProperties);
      Assert.assertEquals(streamProperties, getProperties(stream, MetadataScope.USER));
      ImmutableSet<String> streamTags = ImmutableSet.of("stream-tag1", "stream-tag2");
      addTags(stream, streamTags);
      Assert.assertEquals(streamTags, getTags(stream, MetadataScope.USER));

      long startTime = TimeMathParser.nowInSeconds();
      RunId flowRunId = runAndWait(flow.toEntityId());
      // Wait for few seconds so that the stop time secs is more than start time secs.
      TimeUnit.SECONDS.sleep(2);
      waitForStop(flow.toEntityId(), true);
      long stopTime = TimeMathParser.nowInSeconds();

      // Fetch dataset lineage
      LineageRecord lineage = fetchLineage(dataset, startTime, stopTime, 10);

      LineageRecord expected =
        LineageSerializer.toLineageRecord(
          startTime,
          stopTime,
          new Lineage(ImmutableSet.of(
            new Relation(dataset.toId(), flow, AccessType.UNKNOWN,
                         flowRunId,
                         ImmutableSet.of(Id.Flow.Flowlet.from(flow, AllProgramsApp.A.NAME))),
            new Relation(stream.toId(), flow, AccessType.READ,
                         flowRunId,
                         ImmutableSet.of(Id.Flow.Flowlet.from(flow, AllProgramsApp.A.NAME)))
          )),
          Collections.<CollapseType>emptySet());
      Assert.assertEquals(expected, lineage);

      // Fetch dataset lineage with time strings
      lineage = fetchLineage(dataset, "now-1h", "now+1h", 10);
      Assert.assertEquals(expected.getRelations(), lineage.getRelations());

      // Fetch stream lineage
      lineage = fetchLineage(stream, startTime, stopTime, 10);
      // same as dataset's lineage
      Assert.assertEquals(expected, lineage);

      // Fetch stream lineage with time strings
      lineage = fetchLineage(stream, "now-1h", "now+1h", 10);
      // same as dataset's lineage
      Assert.assertEquals(expected.getRelations(), lineage.getRelations());

      // Assert metadata
      // Id.Flow needs conversion to Id.Program JIRA - CDAP-3658
      Id.Program programForFlow = Id.Program.from(flow.getApplication(), flow.getType(), flow.getId());
      Assert.assertEquals(toSet(new MetadataRecord(app, MetadataScope.USER, appProperties, appTags),
                                new MetadataRecord(programForFlow.toEntityId(), MetadataScope.USER, flowProperties,
                                                   flowTags),
                                new MetadataRecord(dataset, MetadataScope.USER, dataProperties, dataTags),
                                new MetadataRecord(stream, MetadataScope.USER, streamProperties,
                                                   streamTags)),
                          fetchRunMetadata(new Id.Run(flow, flowRunId.getId())));

      // Assert with a time range after the flow run should return no results
      long laterStartTime = stopTime + 1000;
      long laterEndTime = stopTime + 5000;
      // Fetch stream lineage
      lineage = fetchLineage(stream, laterStartTime, laterEndTime, 10);

      Assert.assertEquals(
        LineageSerializer.toLineageRecord(laterStartTime, laterEndTime, new Lineage(ImmutableSet.<Relation>of()),
                                          Collections.<CollapseType>emptySet()),
        lineage);

      // Assert with a time range before the flow run should return no results
      long earlierStartTime = startTime - 5000;
      long earlierEndTime = startTime - 1000;
      // Fetch stream lineage
      lineage = fetchLineage(stream, earlierStartTime, earlierEndTime, 10);

      Assert.assertEquals(
        LineageSerializer.toLineageRecord(earlierStartTime, earlierEndTime, new Lineage(ImmutableSet.<Relation>of()),
                                          Collections.<CollapseType>emptySet()),
        lineage);

      // Test bad time ranges
      fetchLineage(dataset, "sometime", "sometime", 10, BadRequestException.class);
      fetchLineage(dataset, "now+1h", "now-1h", 10, BadRequestException.class);

      // Test non-existent run
      assertRunMetadataNotFound(new Id.Run(flow, RunIds.generate(1000).getId()));
    } finally {
      namespaceClient.delete(namespace.toId());
    }
  }

  @Test
  public void testAllProgramsLineage() throws Exception {
    NamespaceId namespace = new NamespaceId("testAllProgramsLineage");
    ApplicationId app = new ApplicationId(namespace.getNamespace(), AllProgramsApp.NAME);
    Id.Flow flow = Id.Flow.from(app.toId(), AllProgramsApp.NoOpFlow.NAME);
    ProgramId mapreduce = app.mr(AllProgramsApp.NoOpMR.NAME);
    ProgramId mapreduce2 = app.mr(AllProgramsApp.NoOpMR2.NAME);
    ProgramId spark = app.spark(AllProgramsApp.NoOpSpark.NAME);
    ProgramId service = app.service(AllProgramsApp.NoOpService.NAME);
    ProgramId worker = app.worker(AllProgramsApp.NoOpWorker.NAME);
    ProgramId workflow = app.workflow(AllProgramsApp.NoOpWorkflow.NAME);
    DatasetId dataset = new DatasetId(namespace.getNamespace(), AllProgramsApp.DATASET_NAME);
    DatasetId dataset2 = new DatasetId(namespace.getNamespace(), AllProgramsApp.DATASET_NAME2);
    DatasetId dataset3 = new DatasetId(namespace.getNamespace(), AllProgramsApp.DATASET_NAME3);
    StreamId stream = new StreamId(namespace.getNamespace(), AllProgramsApp.STREAM_NAME);

    namespaceClient.create(new NamespaceMeta.Builder().setName(namespace.getNamespace()).build());
    try {
      appClient.deploy(namespace.toId(), createAppJarFile(AllProgramsApp.class));

      // Add metadata
      ImmutableSet<String> sparkTags = ImmutableSet.of("spark-tag1", "spark-tag2");
      addTags(spark, sparkTags);
      Assert.assertEquals(sparkTags, getTags(spark, MetadataScope.USER));

      ImmutableSet<String> workerTags = ImmutableSet.of("worker-tag1");
      addTags(worker, workerTags);
      Assert.assertEquals(workerTags, getTags(worker, MetadataScope.USER));

      ImmutableMap<String, String> datasetProperties = ImmutableMap.of("data-key1", "data-value1");
      addProperties(dataset, datasetProperties);
      Assert.assertEquals(datasetProperties, getProperties(dataset, MetadataScope.USER));

      // Start all programs
      RunId flowRunId = runAndWait(flow.toEntityId());
      RunId mrRunId = runAndWait(mapreduce);
      RunId mrRunId2 = runAndWait(mapreduce2);
      RunId sparkRunId = runAndWait(spark);
      runAndWait(workflow);
      RunId workflowMrRunId = getRunId(mapreduce, mrRunId);
      RunId serviceRunId = runAndWait(service);
      // Worker makes a call to service to make it access datasets,
      // hence need to make sure service starts before worker, and stops after it.
      RunId workerRunId = runAndWait(worker);

      // Wait for programs to finish
      waitForStop(flow.toEntityId(), true);
      waitForStop(mapreduce, false);
      waitForStop(mapreduce2, false);
      waitForStop(spark, false);
      waitForStop(workflow, false);
      waitForStop(worker, false);
      waitForStop(service, true);

      long now = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
      long oneHour = TimeUnit.HOURS.toSeconds(1);

      // Fetch dataset lineage
      LineageRecord lineage = fetchLineage(dataset, now - oneHour, now + oneHour, toSet(CollapseType.ACCESS), 10);

      // dataset is accessed by all programs
      LineageRecord expected =
        LineageSerializer.toLineageRecord(
          now - oneHour,
          now + oneHour,
          new Lineage(ImmutableSet.of(
            // Dataset access
            new Relation(dataset.toId(), flow, AccessType.UNKNOWN, flowRunId,
                         toSet(Id.Flow.Flowlet.from(flow, AllProgramsApp.A.NAME))),
            new Relation(dataset.toId(), mapreduce.toId(), AccessType.WRITE, mrRunId),
            new Relation(dataset.toId(), mapreduce2.toId(), AccessType.WRITE, mrRunId2),
            new Relation(dataset2.toId(), mapreduce2.toId(), AccessType.READ, mrRunId2),
            new Relation(dataset.toId(), spark.toId(), AccessType.READ, sparkRunId),
            new Relation(dataset2.toId(), spark.toId(), AccessType.WRITE, sparkRunId),
            new Relation(dataset3.toId(), spark.toId(), AccessType.READ, sparkRunId),
            new Relation(dataset3.toId(), spark.toId(), AccessType.WRITE, sparkRunId),
            new Relation(dataset.toId(), mapreduce.toId(), AccessType.WRITE, workflowMrRunId),
            new Relation(dataset.toId(), service.toId(), AccessType.WRITE, serviceRunId),
            new Relation(dataset.toId(), worker.toId(), AccessType.WRITE, workerRunId),

            // Stream access
            new Relation(stream.toId(), flow, AccessType.READ, flowRunId,
                         ImmutableSet.of(Id.Flow.Flowlet.from(flow, AllProgramsApp.A.NAME))),
            new Relation(stream.toId(), mapreduce.toId(), AccessType.READ, mrRunId),
            new Relation(stream.toId(), spark.toId(), AccessType.READ, sparkRunId),
            new Relation(stream.toId(), mapreduce.toId(), AccessType.READ, workflowMrRunId),
            new Relation(stream.toId(), worker.toId(), AccessType.WRITE, workerRunId)
          )),
          toSet(CollapseType.ACCESS));
      Assert.assertEquals(expected, lineage);

      // Fetch stream lineage
      lineage = fetchLineage(stream, now - oneHour, now + oneHour, toSet(CollapseType.ACCESS), 10);

      // stream too is accessed by all programs
      Assert.assertEquals(expected, lineage);

      // Assert metadata
      // Id.Flow needs conversion to Id.Program JIRA - CDAP-3658
      Id.Program programForFlow = Id.Program.from(flow.getApplication(), flow.getType(), flow.getId());
      Assert.assertEquals(toSet(new MetadataRecord(app, MetadataScope.USER, emptyMap(), emptySet()),
                                new MetadataRecord(programForFlow.toEntityId(), MetadataScope.USER, emptyMap(),
                                                   emptySet()),
                                new MetadataRecord(dataset, MetadataScope.USER, datasetProperties,
                                                   emptySet()),
                                new MetadataRecord(stream, MetadataScope.USER, emptyMap(), emptySet())),
                          fetchRunMetadata(new Id.Run(flow, flowRunId.getId())));

      // Id.Worker needs conversion to Id.Program JIRA - CDAP-3658
      ProgramId programForWorker = new ProgramId(worker.getNamespace(), worker.getApplication(), worker.getType(),
                                                 worker.getEntityName());
      Assert.assertEquals(toSet(new MetadataRecord(app, MetadataScope.USER, emptyMap(), emptySet()),
                                new MetadataRecord(programForWorker, MetadataScope.USER, emptyMap(),
                                                   workerTags),
                                new MetadataRecord(dataset, MetadataScope.USER, datasetProperties,
                                                   emptySet()),
                                new MetadataRecord(stream, MetadataScope.USER, emptyMap(), emptySet())),
                          fetchRunMetadata(new Id.Run(worker.toId(), workerRunId.getId())));

      // Id.Spark needs conversion to Id.Program JIRA - CDAP-3658
      ProgramId programForSpark = new ProgramId(spark.getNamespace(), spark.getApplication(), spark.getType(),
                                                spark.getEntityName());
      Assert.assertEquals(toSet(new MetadataRecord(app, MetadataScope.USER, emptyMap(), emptySet()),
                                new MetadataRecord(programForSpark, MetadataScope.USER, emptyMap(),
                                                   sparkTags),
                                new MetadataRecord(dataset, MetadataScope.USER, datasetProperties,
                                                   emptySet()),
                                new MetadataRecord(dataset2, MetadataScope.USER, emptyMap(), emptySet()),
                                new MetadataRecord(dataset3, MetadataScope.USER, emptyMap(), emptySet()),
                                new MetadataRecord(stream, MetadataScope.USER, emptyMap(), emptySet())),
                          fetchRunMetadata(new Id.Run(spark.toId(), sparkRunId.getId())));
    } finally {
      namespaceClient.delete(namespace.toId());
    }
  }

  @Test
  public void testLineageInNonExistingNamespace() throws Exception {
    String namespace = "nonExistent";
    ApplicationId app = new ApplicationId(namespace, AllProgramsApp.NAME);
    Id.Flow flow = Id.Flow.from(app.getApplication(), AllProgramsApp.NoOpFlow.NAME);
    DatasetId dataset = new DatasetId(namespace, AllProgramsApp.DATASET_NAME);
    StreamId stream = new StreamId(namespace, AllProgramsApp.STREAM_NAME);

    fetchLineage(dataset, 0, 10000, 10, NotFoundException.class);

    try {
      fetchLineage(stream, 0, 10000, 10);
      Assert.fail("Expected not to be able to fetch lineage for nonexistent stream: " + stream);
    } catch (NotFoundException expected) {
    }

    assertRunMetadataNotFound(new Id.Run(flow, RunIds.generate(1000).getId()));
  }

  @Test
  public void testLineageForNonExistingEntity() throws Exception {
    DatasetId datasetInstance = new DatasetId("default", "dummy");
    fetchLineage(datasetInstance, 100, 200, 10, NotFoundException.class);
    fetchLineage(datasetInstance, -100, 200, 10, BadRequestException.class);
    fetchLineage(datasetInstance, 100, -200, 10, BadRequestException.class);
    fetchLineage(datasetInstance, 200, 100, 10, BadRequestException.class);
    fetchLineage(datasetInstance, 100, 200, -10, BadRequestException.class);
  }

  private void waitState(final ProgramId program, ProgramStatus state) throws Exception {
    Tasks.waitFor(state.toString(), new Callable<String>() {
      @Override
      public String call() throws Exception {
        return programClient.getStatus(program.toId());
      }
    }, 60000, TimeUnit.SECONDS, 1000, TimeUnit.MILLISECONDS);
  }

  private RunId runAndWait(final ProgramId program) throws Exception {
    LOG.info("Starting program {}", program);
    programClient.start(program.toId());
    waitState(program, ProgramStatus.RUNNING);
    return getRunId(program);
  }

  private void waitForStop(ProgramId program, boolean needsStop) throws Exception {
    if (needsStop && programClient.getStatus(program.toId()).equals(ProgramRunStatus.RUNNING.toString())) {
      LOG.info("Stopping program {}", program);
      programClient.stop(program.toId());
    }
    waitState(program, STOPPED);
    LOG.info("Program {} has stopped", program);
  }

  private RunId getRunId(ProgramId program) throws Exception {
    return getRunId(program, null);
  }

  private RunId getRunId(final ProgramId program, @Nullable final RunId exclude) throws Exception {
    final AtomicReference<Iterable<RunRecord>> runRecords = new AtomicReference<>();
    Tasks.waitFor(1, new Callable<Integer>() {
      @Override
      public Integer call() throws Exception {
        runRecords.set(Iterables.filter(
          programClient.getProgramRuns(program.toId(), "RUNNING", 0, Long.MAX_VALUE, Integer.MAX_VALUE),
          new Predicate<RunRecord>() {
            @Override
            public boolean apply(RunRecord input) {
              return exclude == null || !input.getPid().equals(exclude.getId());
            }
          }));
        return Iterables.size(runRecords.get());
      }
    }, 60, TimeUnit.SECONDS, 10, TimeUnit.MILLISECONDS);
    Assert.assertEquals(1, Iterables.size(runRecords.get()));
    return RunIds.fromString(Iterables.getFirst(runRecords.get(), null).getPid());
  }

  @SafeVarargs
  private static <T> Set<T> toSet(T... elements) {
    return ImmutableSet.copyOf(elements);
  }

  private Set<String> emptySet() {
    return Collections.emptySet();
  }

  private Map<String, String> emptyMap() {
    return Collections.emptyMap();
  }
}
