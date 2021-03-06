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

package co.cask.cdap.datastreams;

import co.cask.cdap.api.data.format.StructuredRecord;
import co.cask.cdap.api.data.schema.Schema;
import co.cask.cdap.api.dataset.table.Table;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.utils.Tasks;
import co.cask.cdap.etl.mock.batch.MockSink;
import co.cask.cdap.etl.mock.batch.aggregator.FieldCountAggregator;
import co.cask.cdap.etl.mock.batch.joiner.MockJoiner;
import co.cask.cdap.etl.mock.spark.Window;
import co.cask.cdap.etl.mock.spark.compute.StringValueFilterCompute;
import co.cask.cdap.etl.mock.spark.streaming.MockSource;
import co.cask.cdap.etl.mock.test.HydratorTestBase;
import co.cask.cdap.etl.mock.transform.FieldsPrefixTransform;
import co.cask.cdap.etl.mock.transform.StringValueFilterTransform;
import co.cask.cdap.etl.proto.v2.DataStreamsConfig;
import co.cask.cdap.etl.proto.v2.ETLStage;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.artifact.AppRequest;
import co.cask.cdap.proto.artifact.ArtifactSummary;
import co.cask.cdap.proto.id.ArtifactId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.test.ApplicationManager;
import co.cask.cdap.test.DataSetManager;
import co.cask.cdap.test.MetricsManager;
import co.cask.cdap.test.SparkManager;
import co.cask.cdap.test.TestConfiguration;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 *
 */
public class DataStreamsTest extends HydratorTestBase {

  protected static final ArtifactId APP_ARTIFACT_ID =
    new ArtifactId(NamespaceId.DEFAULT.getNamespace(), "app", "1.0.0");
  protected static final ArtifactSummary APP_ARTIFACT = new ArtifactSummary("app", "1.0.0");
  private static int startCount = 0;
  @ClassRule
  public static final TestConfiguration CONFIG = new TestConfiguration(Constants.Explore.EXPLORE_ENABLED, false);

  @BeforeClass
  public static void setupTest() throws Exception {
    if (startCount++ > 0) {
      return;
    }

    setupStreamingArtifacts(APP_ARTIFACT_ID, DataStreamsApp.class);
  }

  @Test
  public void testTransformCompute() throws Exception {
    Schema schema = Schema.recordOf(
      "test",
      Schema.Field.of("id", Schema.of(Schema.Type.STRING)),
      Schema.Field.of("name", Schema.of(Schema.Type.STRING))
    );
    List<StructuredRecord> input = new ArrayList<>();
    StructuredRecord samuelRecord = StructuredRecord.builder(schema).set("id", "123").set("name", "samuel").build();
    StructuredRecord jacksonRecord = StructuredRecord.builder(schema).set("id", "456").set("name", "jackson").build();
    StructuredRecord dwayneRecord = StructuredRecord.builder(schema).set("id", "789").set("name", "dwayne").build();
    StructuredRecord johnsonRecord = StructuredRecord.builder(schema).set("id", "0").set("name", "johnson").build();
    input.add(samuelRecord);
    input.add(jacksonRecord);
    input.add(dwayneRecord);
    input.add(johnsonRecord);

    DataStreamsConfig etlConfig = DataStreamsConfig.builder()
      .addStage(new ETLStage("source", MockSource.getPlugin(schema, input)))
      .addStage(new ETLStage("sink", MockSink.getPlugin("output")))
      .addStage(new ETLStage("jacksonFilter", StringValueFilterTransform.getPlugin("name", "jackson")))
      .addStage(new ETLStage("dwayneFilter", StringValueFilterCompute.getPlugin("name", "dwayne")))
      .addConnection("source", "jacksonFilter")
      .addConnection("jacksonFilter", "dwayneFilter")
      .addConnection("dwayneFilter", "sink")
      .setBatchInterval("1s")
      .build();

    Id.Application appId = Id.Application.from(Id.Namespace.DEFAULT, "simpleApp");
    AppRequest<DataStreamsConfig> appRequest = new AppRequest<>(APP_ARTIFACT, etlConfig);
    ApplicationManager appManager = deployApplication(appId, appRequest);

    SparkManager sparkManager = appManager.getSparkManager(DataStreamsSparkLauncher.NAME);
    sparkManager.start();
    sparkManager.waitForStatus(true, 10, 1);

    final DataSetManager<Table> outputManager = getDataset("output");
    final Set<StructuredRecord> expected = new HashSet<>();
    expected.add(samuelRecord);
    expected.add(johnsonRecord);
    Tasks.waitFor(
      true,
      new Callable<Boolean>() {
        @Override
        public Boolean call() throws Exception {
          outputManager.flush();
          Set<StructuredRecord> outputRecords = new HashSet<>();
          outputRecords.addAll(MockSink.readOutput(outputManager));
          return expected.equals(outputRecords);
        }
      },
      1,
      TimeUnit.MINUTES);

    sparkManager.stop();
    sparkManager.waitForStatus(false, 10, 1);

    validateMetric(appId, "source.records.out", 4);
    validateMetric(appId, "jacksonFilter.records.in", 4);
    validateMetric(appId, "jacksonFilter.records.out", 3);
    validateMetric(appId, "dwayneFilter.records.in", 3);
    validateMetric(appId, "dwayneFilter.records.out", 2);
    validateMetric(appId, "sink.records.in", 2);
  }

  @Test
  public void testParallelAggregators() throws Exception {
    String sink1Name = "pAggOutput1";
    String sink2Name = "pAggOutput2";

    Schema inputSchema = Schema.recordOf(
      "testRecord",
      Schema.Field.of("user", Schema.of(Schema.Type.STRING)),
      Schema.Field.of("item", Schema.of(Schema.Type.LONG))
    );

    List<StructuredRecord> input1 = ImmutableList.of(
      StructuredRecord.builder(inputSchema).set("user", "samuel").set("item", 1L).build(),
      StructuredRecord.builder(inputSchema).set("user", "samuel").set("item", 2L).build());

    List<StructuredRecord> input2 = ImmutableList.of(
      StructuredRecord.builder(inputSchema).set("user", "samuel").set("item", 3L).build(),
      StructuredRecord.builder(inputSchema).set("user", "john").set("item", 4L).build(),
      StructuredRecord.builder(inputSchema).set("user", "john").set("item", 3L).build());

    /*
       source1 --|--> agg1 --> sink1
                 |
       source2 --|--> agg2 --> sink2
     */
    DataStreamsConfig pipelineConfig = DataStreamsConfig.builder()
      .setBatchInterval("5s")
      .addStage(new ETLStage("source1", MockSource.getPlugin(inputSchema, input1)))
      .addStage(new ETLStage("source2", MockSource.getPlugin(inputSchema, input2)))
      .addStage(new ETLStage("sink1", MockSink.getPlugin(sink1Name)))
      .addStage(new ETLStage("sink2", MockSink.getPlugin(sink2Name)))
      .addStage(new ETLStage("agg1", FieldCountAggregator.getPlugin("user", "string")))
      .addStage(new ETLStage("agg2", FieldCountAggregator.getPlugin("item", "long")))
      .addConnection("source1", "agg1")
      .addConnection("source1", "agg2")
      .addConnection("source2", "agg1")
      .addConnection("source2", "agg2")
      .addConnection("agg1", "sink1")
      .addConnection("agg2", "sink2")
      .build();

    AppRequest<DataStreamsConfig> appRequest = new AppRequest<>(APP_ARTIFACT, pipelineConfig);
    Id.Application appId = Id.Application.from(Id.Namespace.DEFAULT, "ParallelAggApp");
    ApplicationManager appManager = deployApplication(appId, appRequest);

    SparkManager sparkManager = appManager.getSparkManager(DataStreamsSparkLauncher.NAME);
    sparkManager.start();
    sparkManager.waitForStatus(true, 10, 1);

    Schema outputSchema1 = Schema.recordOf(
      "user.count",
      Schema.Field.of("user", Schema.of(Schema.Type.STRING)),
      Schema.Field.of("ct", Schema.of(Schema.Type.LONG))
    );
    Schema outputSchema2 = Schema.recordOf(
      "item.count",
      Schema.Field.of("item", Schema.of(Schema.Type.LONG)),
      Schema.Field.of("ct", Schema.of(Schema.Type.LONG))
    );

    // check output
    final DataSetManager<Table> sinkManager1 = getDataset(sink1Name);
    final Set<StructuredRecord> expected1 = ImmutableSet.of(
      StructuredRecord.builder(outputSchema1).set("user", "all").set("ct", 5L).build(),
      StructuredRecord.builder(outputSchema1).set("user", "samuel").set("ct", 3L).build(),
      StructuredRecord.builder(outputSchema1).set("user", "john").set("ct", 2L).build());

    Tasks.waitFor(
      true,
      new Callable<Boolean>() {
        @Override
        public Boolean call() throws Exception {
          sinkManager1.flush();
          Set<StructuredRecord> outputRecords = new HashSet<>();
          outputRecords.addAll(MockSink.readOutput(sinkManager1));
          return expected1.equals(outputRecords);
        }
      },
      1,
      TimeUnit.MINUTES);

    final DataSetManager<Table> sinkManager2 = getDataset(sink2Name);
    final Set<StructuredRecord> expected2 = ImmutableSet.of(
      StructuredRecord.builder(outputSchema2).set("item", 0L).set("ct", 5L).build(),
      StructuredRecord.builder(outputSchema2).set("item", 1L).set("ct", 1L).build(),
      StructuredRecord.builder(outputSchema2).set("item", 2L).set("ct", 1L).build(),
      StructuredRecord.builder(outputSchema2).set("item", 3L).set("ct", 2L).build(),
      StructuredRecord.builder(outputSchema2).set("item", 4L).set("ct", 1L).build());

    Tasks.waitFor(
      true,
      new Callable<Boolean>() {
        @Override
        public Boolean call() throws Exception {
          sinkManager2.flush();
          Set<StructuredRecord> outputRecords = new HashSet<>();
          outputRecords.addAll(MockSink.readOutput(sinkManager2));
          return expected2.equals(outputRecords);
        }
      },
      1,
      TimeUnit.MINUTES);

    sparkManager.stop();
    sparkManager.waitForStatus(false, 10, 1);

    validateMetric(appId, "source1.records.out", 2);
    validateMetric(appId, "source2.records.out", 3);
    validateMetric(appId, "agg1.records.in", 5);
    validateMetric(appId, "agg1.records.out", 3);
    validateMetric(appId, "agg2.records.in", 5);
    validateMetric(appId, "agg2.records.out", 5);
    validateMetric(appId, "sink1.records.in", 3);
    validateMetric(appId, "sink2.records.in", 5);
  }

  @Test
  public void testWindower() throws Exception {
    /*
     * source --> window(width=10,interval=1) --> aggregator --> filter --> sink
     */
    Schema schema = Schema.recordOf("data", Schema.Field.of("x", Schema.of(Schema.Type.STRING)));
    List<StructuredRecord> input = ImmutableList.of(
      StructuredRecord.builder(schema).set("x", "abc").build(),
      StructuredRecord.builder(schema).set("x", "abc").build(),
      StructuredRecord.builder(schema).set("x", "abc").build());

    String sinkName = "windowOut";
    // source sleeps 1 second between outputs
    DataStreamsConfig etlConfig = DataStreamsConfig.builder()
      .addStage(new ETLStage("source", MockSource.getPlugin(schema, input, 1000L)))
      .addStage(new ETLStage("window", Window.getPlugin(30, 1)))
      .addStage(new ETLStage("agg", FieldCountAggregator.getPlugin("x", "string")))
      .addStage(new ETLStage("filter", StringValueFilterTransform.getPlugin("x", "all")))
      .addStage(new ETLStage("sink", MockSink.getPlugin(sinkName)))
      .addConnection("source", "window")
      .addConnection("window", "agg")
      .addConnection("agg", "filter")
      .addConnection("filter", "sink")
      .setBatchInterval("1s")
      .build();

    AppRequest<DataStreamsConfig> appRequest = new AppRequest<>(APP_ARTIFACT, etlConfig);
    Id.Application appId = Id.Application.from(Id.Namespace.DEFAULT, "WindowerApp");
    ApplicationManager appManager = deployApplication(appId, appRequest);

    SparkManager sparkManager = appManager.getSparkManager(DataStreamsSparkLauncher.NAME);
    sparkManager.start();
    sparkManager.waitForStatus(true, 10, 1);

    // the sink should contain at least one record with count of 3, and no records with more than 3.
    // less than 3 if the window doesn't contain all 3 records yet, but there should eventually be a window
    // that contains all 3.
    final DataSetManager<Table> outputManager = getDataset(sinkName);
    Tasks.waitFor(
      true,
      new Callable<Boolean>() {
        @Override
        public Boolean call() throws Exception {
          outputManager.flush();
          boolean sawThree = false;
          for (StructuredRecord record : MockSink.readOutput(outputManager)) {
            long count = record.get("ct");
            if (count == 3L) {
              sawThree = true;
            }
            Assert.assertTrue(count <= 3L);
          }
          return sawThree;
        }
      },
      2,
      TimeUnit.MINUTES);

    sparkManager.stop();
    sparkManager.waitForStatus(false, 10, 1);
  }

  @Test
  public void testJoin() throws Exception {
    /*
     * source1 ----> t1 ------
     *                        | --> innerjoin ----> t4 ------
     * source2 ----> t2 ------                                 |
     *                                                         | ---> outerjoin --> sink1
     *                                                         |
     * source3 -------------------- t3 ------------------------
     */

    Schema inputSchema1 = Schema.recordOf(
      "customerRecord",
      Schema.Field.of("customer_id", Schema.of(Schema.Type.STRING)),
      Schema.Field.of("customer_name", Schema.of(Schema.Type.STRING))
    );

    Schema inputSchema2 = Schema.recordOf(
      "itemRecord",
      Schema.Field.of("item_id", Schema.of(Schema.Type.STRING)),
      Schema.Field.of("item_price", Schema.of(Schema.Type.LONG)),
      Schema.Field.of("cust_id", Schema.of(Schema.Type.STRING)),
      Schema.Field.of("cust_name", Schema.of(Schema.Type.STRING))
    );

    Schema inputSchema3 = Schema.recordOf(
      "transactionRecord",
      Schema.Field.of("t_id", Schema.of(Schema.Type.STRING)),
      Schema.Field.of("c_id", Schema.of(Schema.Type.STRING)),
      Schema.Field.of("i_id", Schema.of(Schema.Type.STRING))
    );

    Schema outSchema1 = Schema.recordOf(
      "join.output",
      Schema.Field.of("customer_id", Schema.of(Schema.Type.STRING)),
      Schema.Field.of("customer_name", Schema.of(Schema.Type.STRING)),
      Schema.Field.of("item_id", Schema.of(Schema.Type.STRING)),
      Schema.Field.of("item_price", Schema.of(Schema.Type.LONG)),
      Schema.Field.of("cust_id", Schema.of(Schema.Type.STRING)),
      Schema.Field.of("cust_name", Schema.of(Schema.Type.STRING))
    );

    Schema outSchema2 = Schema.recordOf(
      "join.output",
      Schema.Field.of("t_id", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
      Schema.Field.of("c_id", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
      Schema.Field.of("i_id", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
      Schema.Field.of("customer_id", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
      Schema.Field.of("customer_name", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
      Schema.Field.of("item_id", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
      Schema.Field.of("item_price", Schema.nullableOf(Schema.of(Schema.Type.LONG))),
      Schema.Field.of("cust_id", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
      Schema.Field.of("cust_name", Schema.nullableOf(Schema.of(Schema.Type.STRING)))
    );

    StructuredRecord recordSamuel = StructuredRecord.builder(inputSchema1).set("customer_id", "1")
      .set("customer_name", "samuel").build();
    StructuredRecord recordBob = StructuredRecord.builder(inputSchema1).set("customer_id", "2")
      .set("customer_name", "bob").build();
    StructuredRecord recordJane = StructuredRecord.builder(inputSchema1).set("customer_id", "3")
      .set("customer_name", "jane").build();

    StructuredRecord recordCar = StructuredRecord.builder(inputSchema2).set("item_id", "11").set("item_price", 10000L)
      .set("cust_id", "1").set("cust_name", "samuel").build();
    StructuredRecord recordBike = StructuredRecord.builder(inputSchema2).set("item_id", "22").set("item_price", 100L)
      .set("cust_id", "3").set("cust_name", "jane").build();

    StructuredRecord recordTrasCar = StructuredRecord.builder(inputSchema3).set("t_id", "1").set("c_id", "1")
      .set("i_id", "11").build();
    StructuredRecord recordTrasBike = StructuredRecord.builder(inputSchema3).set("t_id", "2").set("c_id", "3")
      .set("i_id", "22").build();
    StructuredRecord recordTrasPlane = StructuredRecord.builder(inputSchema3).set("t_id", "3").set("c_id", "4")
      .set("i_id", "33").build();

    List<StructuredRecord> input1 = ImmutableList.of(recordSamuel, recordBob, recordJane);
    List<StructuredRecord> input2 = ImmutableList.of(recordCar, recordBike);
    List<StructuredRecord> input3 = ImmutableList.of(recordTrasCar, recordTrasBike, recordTrasPlane);

    String outputName = "multiJoinOutputSink";
    DataStreamsConfig etlConfig = DataStreamsConfig.builder()
      .addStage(new ETLStage("source1", MockSource.getPlugin(inputSchema1, input1)))
      .addStage(new ETLStage("source2", MockSource.getPlugin(inputSchema2, input2)))
      .addStage(new ETLStage("source3", MockSource.getPlugin(inputSchema3, input3)))
      .addStage(new ETLStage("t1", FieldsPrefixTransform.getPlugin("", inputSchema1.toString())))
      .addStage(new ETLStage("t2", FieldsPrefixTransform.getPlugin("", inputSchema2.toString())))
      .addStage(new ETLStage("t3", FieldsPrefixTransform.getPlugin("", inputSchema3.toString())))
      .addStage(new ETLStage("t4", FieldsPrefixTransform.getPlugin("", outSchema1.toString())))
      .addStage(new ETLStage("innerjoin", MockJoiner.getPlugin("t1.customer_id=t2.cust_id",
                                                               "t1,t2", "")))
      .addStage(new ETLStage("outerjoin", MockJoiner.getPlugin("t4.item_id=t3.i_id",
                                                                 "", "")))
      .addStage(new ETLStage("multijoinSink", MockSink.getPlugin(outputName)))
      .addConnection("source1", "t1")
      .addConnection("source2", "t2")
      .addConnection("source3", "t3")
      .addConnection("t1", "innerjoin")
      .addConnection("t2", "innerjoin")
      .addConnection("innerjoin", "t4")
      .addConnection("t3", "outerjoin")
      .addConnection("t4", "outerjoin")
      .addConnection("outerjoin", "multijoinSink")
      .setBatchInterval("5s")
      .build();

    AppRequest<DataStreamsConfig> appRequest = new AppRequest<>(APP_ARTIFACT, etlConfig);
    Id.Application appId = Id.Application.from(Id.Namespace.DEFAULT, "JoinerApp");
    ApplicationManager appManager = deployApplication(appId, appRequest);

    SparkManager sparkManager = appManager.getSparkManager(DataStreamsSparkLauncher.NAME);
    sparkManager.start();
    sparkManager.waitForStatus(true, 10, 1);

    StructuredRecord joinRecordSamuel = StructuredRecord.builder(outSchema2)
      .set("customer_id", "1").set("customer_name", "samuel")
      .set("item_id", "11").set("item_price", 10000L).set("cust_id", "1").set("cust_name", "samuel")
      .set("t_id", "1").set("c_id", "1").set("i_id", "11").build();

    StructuredRecord joinRecordJane = StructuredRecord.builder(outSchema2)
      .set("customer_id", "3").set("customer_name", "jane")
      .set("item_id", "22").set("item_price", 100L).set("cust_id", "3").set("cust_name", "jane")
      .set("t_id", "2").set("c_id", "3").set("i_id", "22").build();

    StructuredRecord joinRecordPlane = StructuredRecord.builder(outSchema2)
      .set("t_id", "3").set("c_id", "4").set("i_id", "33").build();
    final Set<StructuredRecord> expected = ImmutableSet.of(joinRecordSamuel, joinRecordJane, joinRecordPlane);

    final DataSetManager<Table> outputManager = getDataset(outputName);
    Tasks.waitFor(
      true,
      new Callable<Boolean>() {
        @Override
        public Boolean call() throws Exception {
          outputManager.flush();
          Set<StructuredRecord> outputRecords = new HashSet<>();
          outputRecords.addAll(MockSink.readOutput(outputManager));
          return expected.equals(outputRecords);
        }
      },
      4,
      TimeUnit.MINUTES);

    sparkManager.stop();
    sparkManager.waitForStatus(false, 10, 1);

    validateMetric(appId, "source1.records.out", 3);
    validateMetric(appId, "source2.records.out", 2);
    validateMetric(appId, "source3.records.out", 3);
    validateMetric(appId, "t1.records.in", 3);
    validateMetric(appId, "t1.records.out", 3);
    validateMetric(appId, "t2.records.in", 2);
    validateMetric(appId, "t2.records.out", 2);
    validateMetric(appId, "t3.records.in", 3);
    validateMetric(appId, "t3.records.out", 3);
    validateMetric(appId, "t4.records.in", 2);
    validateMetric(appId, "t4.records.out", 2);
    validateMetric(appId, "innerjoin.records.in", 5);
    validateMetric(appId, "innerjoin.records.out", 2);
    validateMetric(appId, "outerjoin.records.in", 5);
    validateMetric(appId, "outerjoin.records.out", 3);
    validateMetric(appId, "multijoinSink.records.in", 3);
  }

  private void validateMetric(Id.Application appId, String metric,
                              long expected) throws TimeoutException, InterruptedException {
    MetricsManager metricsManager = getMetricsManager();
    Map<String, String> tags = ImmutableMap.of(Constants.Metrics.Tag.NAMESPACE, appId.getNamespaceId(),
                                               Constants.Metrics.Tag.APP, appId.getId(),
                                               Constants.Metrics.Tag.SPARK, DataStreamsSparkLauncher.NAME);
    metricsManager.waitForTotalMetricCount(tags, "user." + metric, expected, 10, TimeUnit.SECONDS);
    metricsManager.waitForTotalMetricCount(tags, "user." + metric, expected, 10, TimeUnit.SECONDS);
  }
}
