package com.continuuity.payvment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.continuuity.api.data.OperationResult;
import com.continuuity.api.data.ReadColumnRange;
import com.continuuity.api.flow.flowlet.Tuple;
import com.continuuity.api.flow.flowlet.builders.TupleBuilder;
import com.continuuity.flow.FlowTestHelper;
import com.continuuity.flow.FlowTestHelper.TestFlowHandle;
import com.continuuity.flow.flowlet.internal.TupleSerializer;
import com.continuuity.payvment.data.ActivityFeed;
import com.continuuity.payvment.data.ActivityFeed.ActivityFeedEntry;
import com.continuuity.payvment.data.CounterTable;
import com.continuuity.payvment.data.ProductTable;
import com.continuuity.payvment.data.SortedCounterTable;
import com.continuuity.payvment.data.SortedCounterTable.Counter;
import com.continuuity.payvment.util.Bytes;
import com.continuuity.payvment.util.Constants;
import com.continuuity.payvment.util.Helpers;
import com.google.gson.Gson;

public class TestProductFeedFlow extends PayvmentBaseFlowTest {

  @Test(timeout = 20000)
  public void testProductFeedFlow() throws Exception {
    // Get references to tables
    ProductTable productTable = new ProductTable(flowletContext);
    CounterTable productUpdateCountTable = new CounterTable("productUpdates",
        flowletContext);
    CounterTable allTimeScoreTable = new CounterTable("allTimeScore",
        flowletContext);
    SortedCounterTable topScoreTable = new SortedCounterTable("topScore",
        flowletContext, new SortedCounterTable.SortedCounterConfig());
    
    // Instantiate product feed flow
    ProductFeedFlow productFeedFlow = new ProductFeedFlow();
    
    // Start the flow
    TestFlowHandle flowHandle =
        FlowTestHelper.startFlow(productFeedFlow, conf, executor);
    assertTrue(flowHandle.isSuccess());
    
    // Generate a single product event
    Long now = System.currentTimeMillis();
    Long product_id = 1L;
    Long store_id = 2L;
    String category = "Housewares";
    String name = "Green Widget";
    ProductMeta productMeta =
        new ProductMeta(product_id, store_id, now, category, name, 3.5);
    String productMetaJson = productMeta.toJson();
    
    // Write json to input stream
    writeToStream(ProductFeedFlow.flowName, ProductFeedFlow.inputStream,
        Bytes.toBytes(productMetaJson));
    
    // Wait for parsing flowlet to process the tuple
    while (ProductFeedParserFlowlet.numProcessed < 1) {
      System.out.println("Waiting for parsing flowlet to process tuple");
      Thread.sleep(1000);
    }
    
    // Wait for processor flowlet to process the tuple
    while (ProductFeedFlow.ProductProcessorFlowlet.numProcessed < 1) {
      System.out.println("Waiting for processor flowlet to process tuple");
      Thread.sleep(10);
    }
    
    // Wait for processor flowlet to process the tuple
    while (ProductFeedFlow.ProductActivityFeedUpdaterFlowlet.numProcessed < 1) {
      System.out.println("Waiting for updater flowlet to process tuple");
      Thread.sleep(10);
    }
    
    System.out.println("Tuple processed to the end!");

    // If we are here, flow ran successfully!
    assertTrue(FlowTestHelper.stopFlow(flowHandle));
    
    // Verify the product is stored
    ProductMeta readProductMeta = productTable.readObject(
        Bytes.toBytes(product_id));
    assertEqual(productMeta, readProductMeta);
    
    // Verify the product update count has been incremented
    assertEquals(new Long(1),
        productUpdateCountTable.readSingleKey(Bytes.toBytes(product_id)));
    
    // Verify the product total score has increased to 1
    assertEquals(new Long(1),
        allTimeScoreTable.readSingleKey(
            Bytes.add(Constants.PRODUCT_ALL_TIME_PREFIX,
                Bytes.toBytes(product_id))));    
    
    // Verify the hourly score has been incremented to type score increase
    List<Counter> counters = topScoreTable.readTopCounters(
        Bytes.add(Bytes.toBytes(Helpers.hour(now)),
            Bytes.toBytes(productMeta.category)), 10);
    assertEquals(1, counters.size());
    assertEquals(new Long(1), counters.get(0).getCount());
    
    // Verify a new entry has been made in activity feed
    ReadColumnRange read = new ReadColumnRange(Constants.ACTIVITY_FEED_TABLE,
        ActivityFeed.makeActivityFeedRow(category), null, null);
    OperationResult<Map<byte[],byte[]>> result =
        flowletContext.getDataFabric().read(read);
    assertFalse(result.isEmpty());
    Map<byte[], byte[]> map = result.getValue();
    assertEquals(1, map.size());
    byte [] column = map.keySet().iterator().next();
    byte [] value = map.values().iterator().next();
    ActivityFeedEntry feedEntry = new ActivityFeedEntry(column, value);
    assertEquals(now, feedEntry.timestamp);
    assertEquals(store_id, feedEntry.store_id);
    assertEquals(1, feedEntry.products.size());
    assertEquals(product_id, feedEntry.products.get(0).product_id);
    assertEquals(new Long(1), feedEntry.products.get(0).score);
  }
  
  @Test
  public void testProductMetaSerialization() throws Exception {

    String exampleProductMetaJson =
        "{\"@id\":\"6709879\"," +
        "\"category\":\"Dresses\"," +
        "\"name\":\"Sleeveless Black Sequin Dress\"," +
        "\"last_modified\":\"1348074421000\"," +
        "\"store_id\":\"164341\"," +
        "\"score\":\"1.3\"}";
    
    String processedJsonString =
        ProductFeedParserFlowlet.preProcessSocialActionJSON(
            exampleProductMetaJson);
    
    Gson gson = new Gson();
    ProductMeta productMeta =
        gson.fromJson(processedJsonString, ProductMeta.class);
    
    assertEquals(new Long(6709879), productMeta.product_id);
    assertEquals(new Long(164341), productMeta.store_id);
    assertEquals(new Long(1348074421000L), productMeta.date);
    assertEquals("Dresses", productMeta.category);
    assertEquals("Sleeveless Black Sequin Dress", productMeta.name);
    assertEquals(new Double(1.3), productMeta.score);
    
    // Try with JSON generating helper method
    
    exampleProductMetaJson = productMeta.toJson();
    processedJsonString =
        ProductFeedParserFlowlet.preProcessSocialActionJSON(
            exampleProductMetaJson);
    ProductMeta productMeta2 =
        gson.fromJson(processedJsonString, ProductMeta.class);

    assertEqual(productMeta, productMeta2);
    
    // Try to serialize/deserialize in a tuple
    TupleSerializer serializer = new TupleSerializer(false);
    serializer.register(ProductMeta.class);
    Tuple sTuple = new TupleBuilder().set("meta", productMeta).create();
    byte [] bytes = serializer.serialize(sTuple);
    assertNotNull(bytes);
    Tuple dTuple = serializer.deserialize(bytes);
    ProductMeta productMeta3 = dTuple.get("meta");
    assertNotNull(productMeta);

    assertEqual(productMeta, productMeta3);
    
  }
}
