/*
 * Copyright 2015-2016 Cask Data, Inc.
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
package co.cask.cdap.data2.metadata.dataset;

import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.data2.datafabric.dataset.DatasetsUtil;
import co.cask.cdap.data2.dataset2.DatasetFrameworkTestUtil;
import co.cask.cdap.data2.metadata.indexer.Indexer;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.ProgramType;
import co.cask.cdap.proto.id.ApplicationId;
import co.cask.cdap.proto.id.DatasetId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.NamespacedId;
import co.cask.cdap.proto.id.ProgramId;
import co.cask.cdap.proto.id.StreamId;
import co.cask.cdap.proto.id.StreamViewId;
import co.cask.cdap.proto.metadata.MetadataSearchTargetType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Test class for {@link MetadataDataset} class.
 */
public class MetadataDatasetTest {

  @ClassRule
  public static DatasetFrameworkTestUtil dsFrameworkUtil = new DatasetFrameworkTestUtil();

  private static final Id.DatasetInstance datasetInstance =
    Id.DatasetInstance.from(DatasetFrameworkTestUtil.NAMESPACE_ID, "meta");

  private MetadataDataset dataset;

  private final ApplicationId app1 = ApplicationId.fromIdParts(Arrays.asList("ns1", "app1"));
  private final ApplicationId appNs2 = ApplicationId.fromIdParts(Arrays.asList("ns2", "app1"));
  // Have to use Id.Program for comparison here because the MetadataDataset APIs return Id.Program.
  private final ProgramId flow1 = ProgramId.fromIdParts(Arrays.asList("ns1", "app1", ProgramType.FLOW.getPrettyName(),
                                                                      "flow1"));
  private final DatasetId dataset1 = DatasetId.fromIdParts(Arrays.asList("ns1", "ds1"));
  private final StreamId stream1 = StreamId.fromIdParts(Arrays.asList("ns1", "s1"));
  private final StreamViewId view1 = StreamViewId.fromIdParts(Arrays.asList(stream1.getStream(), "v1"));
  private final co.cask.cdap.proto.id.ArtifactId artifact1 =
    co.cask.cdap.proto.id.ArtifactId.fromIdParts(Arrays.asList("ns1", "a1", "1.0.0"));

  @Before
  public void before() throws Exception {
    dataset = getDataset(datasetInstance);
  }

  @After
  public void after() throws Exception {
    dataset = null;
  }

  @Test
  public void testProperties() throws Exception {
    Assert.assertEquals(0, dataset.getProperties(app1).size());
    Assert.assertEquals(0, dataset.getProperties(flow1).size());
    Assert.assertEquals(0, dataset.getProperties(dataset1).size());
    Assert.assertEquals(0, dataset.getProperties(stream1).size());
    Assert.assertEquals(0, dataset.getProperties(view1).size());
    Assert.assertEquals(0, dataset.getProperties(artifact1).size());
    // Set some properties
    dataset.setProperty(app1, "akey1", "avalue1");
    dataset.setProperty(flow1, "fkey1", "fvalue1");
    dataset.setProperty(flow1, "fK", "fV");
    dataset.setProperty(dataset1, "dkey1", "dvalue1");
    dataset.setProperty(stream1, "skey1", "svalue1");
    dataset.setProperty(stream1, "skey2", "svalue2");
    dataset.setProperty(view1, "vkey1", "vvalue1");
    dataset.setProperty(view1, "vkey2", "vvalue2");
    dataset.setProperty(artifact1, "rkey1", "rvalue1");
    dataset.setProperty(artifact1, "rkey2", "rvalue2");
    // verify
    Map<String, String> properties = dataset.getProperties(app1);
    Assert.assertEquals(ImmutableMap.of("akey1", "avalue1"), properties);
    dataset.removeProperties(app1, "akey1");
    Assert.assertNull(dataset.getProperty(app1, "akey1"));
    MetadataEntry result = dataset.getProperty(flow1, "fkey1");
    MetadataEntry expected = new MetadataEntry(flow1, "fkey1", "fvalue1");
    Assert.assertEquals(expected, result);
    Assert.assertEquals(ImmutableMap.of("fkey1", "fvalue1", "fK", "fV"), dataset.getProperties(flow1));
    dataset.removeProperties(flow1, "fkey1");
    properties = dataset.getProperties(flow1);
    Assert.assertEquals(1, properties.size());
    Assert.assertEquals("fV", properties.get("fK"));
    dataset.removeProperties(flow1);
    Assert.assertEquals(0, dataset.getProperties(flow1).size());
    expected = new MetadataEntry(dataset1, "dkey1", "dvalue1");
    Assert.assertEquals(expected, dataset.getProperty(dataset1, "dkey1"));
    Assert.assertEquals(ImmutableMap.of("skey1", "svalue1", "skey2", "svalue2"), dataset.getProperties(stream1));
    properties = dataset.getProperties(artifact1);
    Assert.assertEquals(ImmutableMap.of("rkey1", "rvalue1", "rkey2", "rvalue2"), properties);
    result = dataset.getProperty(artifact1, "rkey2");
    expected = new MetadataEntry(artifact1, "rkey2", "rvalue2");
    Assert.assertEquals(expected, result);
    properties = dataset.getProperties(view1);
    Assert.assertEquals(ImmutableMap.of("vkey1", "vvalue1", "vkey2", "vvalue2"), properties);
    result = dataset.getProperty(view1, "vkey2");
    expected = new MetadataEntry(view1, "vkey2", "vvalue2");
    Assert.assertEquals(expected, result);
    // reset a property
    dataset.setProperty(stream1, "skey1", "sv1");
    Assert.assertEquals(ImmutableMap.of("skey1", "sv1", "skey2", "svalue2"), dataset.getProperties(stream1));
    // cleanup
    dataset.removeProperties(app1);
    dataset.removeProperties(flow1);
    dataset.removeProperties(dataset1);
    dataset.removeProperties(stream1);
    dataset.removeProperties(artifact1);
    dataset.removeProperties(view1);
    Assert.assertEquals(0, dataset.getProperties(app1).size());
    Assert.assertEquals(0, dataset.getProperties(flow1).size());
    Assert.assertEquals(0, dataset.getProperties(dataset1).size());
    Assert.assertEquals(0, dataset.getProperties(stream1).size());
    Assert.assertEquals(0, dataset.getProperties(view1).size());
    Assert.assertEquals(0, dataset.getProperties(artifact1).size());
  }

  @Test
  public void testTags() {
    Assert.assertEquals(0, dataset.getTags(app1).size());
    Assert.assertEquals(0, dataset.getTags(flow1).size());
    Assert.assertEquals(0, dataset.getTags(dataset1).size());
    Assert.assertEquals(0, dataset.getTags(stream1).size());
    Assert.assertEquals(0, dataset.getTags(view1).size());
    Assert.assertEquals(0, dataset.getTags(artifact1).size());
    dataset.addTags(app1, "tag1", "tag2", "tag3");
    dataset.addTags(flow1, "tag1");
    dataset.addTags(dataset1, "tag3", "tag2");
    dataset.addTags(stream1, "tag2");
    dataset.addTags(view1, "tag4");
    dataset.addTags(artifact1, "tag3");
    Set<String> tags = dataset.getTags(app1);
    Assert.assertEquals(3, tags.size());
    Assert.assertTrue(tags.contains("tag1"));
    Assert.assertTrue(tags.contains("tag2"));
    Assert.assertTrue(tags.contains("tag3"));
    // add the same tag again
    dataset.addTags(app1, "tag1");
    Assert.assertEquals(3, dataset.getTags(app1).size());
    tags = dataset.getTags(flow1);
    Assert.assertEquals(1, tags.size());
    Assert.assertTrue(tags.contains("tag1"));
    tags = dataset.getTags(dataset1);
    Assert.assertEquals(2, tags.size());
    Assert.assertTrue(tags.contains("tag3"));
    Assert.assertTrue(tags.contains("tag2"));
    tags = dataset.getTags(stream1);
    Assert.assertEquals(1, tags.size());
    Assert.assertTrue(tags.contains("tag2"));
    tags = dataset.getTags(view1);
    Assert.assertEquals(1, tags.size());
    Assert.assertTrue(tags.contains("tag4"));
    dataset.removeTags(app1, "tag1", "tag2");
    tags = dataset.getTags(app1);
    Assert.assertEquals(1, tags.size());
    Assert.assertTrue(tags.contains("tag3"));
    dataset.removeTags(dataset1, "tag3");
    tags = dataset.getTags(dataset1);
    Assert.assertEquals(1, tags.size());
    Assert.assertTrue(tags.contains("tag2"));
    tags = dataset.getTags(artifact1);
    Assert.assertEquals(1, tags.size());
    Assert.assertTrue(tags.contains("tag3"));
    // cleanup
    dataset.removeTags(app1);
    dataset.removeTags(flow1);
    dataset.removeTags(dataset1);
    dataset.removeTags(stream1);
    dataset.removeTags(view1);
    dataset.removeTags(artifact1);
    Assert.assertEquals(0, dataset.getTags(app1).size());
    Assert.assertEquals(0, dataset.getTags(flow1).size());
    Assert.assertEquals(0, dataset.getTags(dataset1).size());
    Assert.assertEquals(0, dataset.getTags(stream1).size());
    Assert.assertEquals(0, dataset.getTags(view1).size());
    Assert.assertEquals(0, dataset.getTags(artifact1).size());
  }

  @Test
  public void testSearchOnTags() throws Exception {
    Assert.assertEquals(0, dataset.getTags(app1).size());
    Assert.assertEquals(0, dataset.getTags(appNs2).size());
    Assert.assertEquals(0, dataset.getTags(flow1).size());
    Assert.assertEquals(0, dataset.getTags(dataset1).size());
    Assert.assertEquals(0, dataset.getTags(stream1).size());
    dataset.addTags(app1, "tag1", "tag2", "tag3");
    dataset.addTags(appNs2, "tag1", "tag2", "tag3_more");
    dataset.addTags(flow1, "tag1");
    dataset.addTags(dataset1, "tag3", "tag2", "tag12-tag33");
    dataset.addTags(stream1, "tag2, tag4");

    // Try to search on all tags
    List<MetadataEntry> results =
      dataset.search("ns1", "tags:*", ImmutableSet.of(MetadataSearchTargetType.ALL));
    // results for dataset1 - ns1:tags:tag12, ns1:tags:tag2, ns1:tags:tag3, ns1:tags:tag33, ns1:tags:tag12-tag33
    Assert.assertEquals(11, results.size());

    // Try to search for tag1*
    results = dataset.search("ns1", "tags:tag1*", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertEquals(4, results.size());

    // Try to search for tag1 with spaces in search query and mixed case of tags keyword
    results = dataset.search("ns1", "  tAGS  :  tag1  ", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertEquals(2, results.size());

    // Try to search for tag4
    results = dataset.search("ns1", "tags:tag4", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertEquals(1, results.size());

    // Try to search for tag33
    results = dataset.search("ns1", "tags:tag33", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertEquals(1, results.size());

    // Try to search for a tag which has - in it
    results = dataset.search("ns1", "tag12-tag33", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertEquals(1, results.size());

    // Try to search for tag33 with spaces in query
    results = dataset.search("ns1", "  tag33  ", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertEquals(1, results.size());

    // Try to search for tag3
    results = dataset.search("ns1", "tags:tag3*", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertEquals(3, results.size());

    // try search in another namespace
    results = dataset.search("ns2", "tags:tag1", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertEquals(1, results.size());

    results = dataset.search("ns2", "tag3", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertEquals(1, results.size());

    results = dataset.search("ns2", "tag*", ImmutableSet.of(MetadataSearchTargetType.APP));
    // 9 due to matches of type ns2:tag1, ns2:tags:tag1, and splitting of tag3_more
    Assert.assertEquals(9, results.size());

    // cleanup
    dataset.removeTags(app1);
    dataset.removeTags(flow1);
    dataset.removeTags(dataset1);
    dataset.removeTags(stream1);
    // Search should be empty after deleting tags
    results = dataset.search("ns1", "tags:tag3*", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertEquals(0, results.size());
    Assert.assertEquals(0, dataset.getTags(app1).size());
    Assert.assertEquals(0, dataset.getTags(flow1).size());
    Assert.assertEquals(0, dataset.getTags(dataset1).size());
    Assert.assertEquals(0, dataset.getTags(stream1).size());
  }

  @Test
  public void testSearchOnValue() throws Exception {
    // Add some metadata
    MetadataEntry entry = new MetadataEntry(flow1, "key1", "value1");
    String multiWordValue = "aV1 av2 ,  -  ,  av3 - av4_av5 av6";
    MetadataEntry multiWordEntry = new MetadataEntry(flow1, "multiword", multiWordValue);

    dataset.setProperty(flow1, "key1", "value1");
    dataset.setProperty(flow1, "key2", "value2");
    dataset.setProperty(flow1, "multiword", multiWordValue);

    // Search for it based on value
    List<MetadataEntry> results =
      dataset.search("ns1", "value1", ImmutableSet.of(MetadataSearchTargetType.PROGRAM));
    Assert.assertEquals(ImmutableList.of(entry), results);

    // Search for it based on a word in value with spaces in search query
    results = dataset.search("ns1", "  aV1   ", ImmutableSet.of(MetadataSearchTargetType.PROGRAM));
    Assert.assertEquals(ImmutableList.of(multiWordEntry), results);

    // Search for it based split patterns to make sure nothing is matched
    results = dataset.search("ns1", "-", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertTrue(results.isEmpty());
    results = dataset.search("ns1", ",", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertTrue(results.isEmpty());
    results = dataset.search("ns1", "_", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertTrue(results.isEmpty());
    results = dataset.search("ns1", ", ,", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertTrue(results.isEmpty());
    results = dataset.search("ns1", ", - ,", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertTrue(results.isEmpty());

    // Search for it based on a word in value
    results = dataset.search("ns1", "av5", ImmutableSet.of(MetadataSearchTargetType.PROGRAM));
    Assert.assertEquals(ImmutableList.of(multiWordEntry), results);

    // Case insensitive
    results = dataset.search("ns1", "ValUe1", ImmutableSet.of(MetadataSearchTargetType.PROGRAM));
    Assert.assertEquals(ImmutableList.of(entry), results);

    // Search based on value
    dataset.setProperty(flow1, "key3", "value1");
    results = dataset.search("ns1", "value1", ImmutableSet.of(MetadataSearchTargetType.PROGRAM));
    Assert.assertEquals(2, results.size());
    for (MetadataEntry result : results) {
      Assert.assertEquals("value1", result.getValue());
    }

    // Search based on value prefix
    dataset.setProperty(stream1, "key21", "value21");
    results = dataset.search("ns1", "value2*", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertEquals(2, results.size());
    for (MetadataEntry result : results) {
      Assert.assertTrue(result.getValue().startsWith("value2"));
    }

    // Search based on value prefix in the wrong namespace
    results = dataset.search("ns12", "value2*", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertTrue(results.isEmpty());
  }

  @Test
  public void testSearchOnKeyValue() throws Exception {
    // Add some properties to flow1
    MetadataEntry flowEntry1 = new MetadataEntry(flow1, "key1", "value1");
    MetadataEntry flowEntry2 = new MetadataEntry(flow1, "key2", "value2");
    dataset.setProperty(flow1, "key1", "value1");
    dataset.setProperty(flow1, "key2", "value2");

    // add a multi word value
    String multiWordKey = "multiword";
    String multiWordValue = "aV1 av2 ,  -  ,  av3 - av4_av5 av6";
    dataset.setProperty(flow1, multiWordKey, multiWordValue);

    MetadataEntry streamEntry1 = new MetadataEntry(stream1, "Key1", "Value1");
    MetadataEntry streamEntry2 = new MetadataEntry(stream1, "sKey1", "sValue1");
    dataset.setProperty(stream1, "sKey1", "sValue1");
    dataset.setProperty(stream1, "Key1", "Value1");

    // Search for it based on value
    List<MetadataEntry> results =
      dataset.search("ns1", "key1" + MetadataDataset.KEYVALUE_SEPARATOR + "value1",
                     ImmutableSet.of(MetadataSearchTargetType.PROGRAM));
    Assert.assertEquals(ImmutableList.of(flowEntry1), results);

    // Search for it based on a word in value with spaces in search query
    results = dataset.search("ns1", "  multiword" + MetadataDataset.KEYVALUE_SEPARATOR + "aV1   ",
                             ImmutableSet.of(MetadataSearchTargetType.PROGRAM));

    MetadataEntry flowMultiWordEntry = new MetadataEntry(flow1, multiWordKey, multiWordValue);
    Assert.assertEquals(ImmutableList.of(flowMultiWordEntry), results);

    // Search for it based on a word in value
    results =
      dataset.search("ns1", multiWordKey + MetadataDataset.KEYVALUE_SEPARATOR + "aV5",
                     ImmutableSet.of(MetadataSearchTargetType.PROGRAM));
    Assert.assertEquals(ImmutableList.of(flowMultiWordEntry), results);

    dataset.removeProperties(flow1, multiWordKey);
    results = dataset.search("ns1", multiWordKey + MetadataDataset.KEYVALUE_SEPARATOR + "aV5",
                             ImmutableSet.of(MetadataSearchTargetType.PROGRAM));
    // search results should be empty after removing this key as the indexes are deleted
    Assert.assertTrue(results.isEmpty());

    // Test wrong ns
    List<MetadataEntry> results2  =
      dataset.search("ns12", "key1" + MetadataDataset.KEYVALUE_SEPARATOR + "value1",
                     ImmutableSet.of(MetadataSearchTargetType.PROGRAM));
    Assert.assertTrue(results2.isEmpty());

    // Test multi word query
    results = dataset.search("ns1", "  value1  av2 ", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertEquals(Sets.newHashSet(flowEntry1, streamEntry1), Sets.newHashSet(results));

    results = dataset.search("ns1", "  value1  sValue1 ", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertEquals(Sets.newHashSet(flowEntry1, streamEntry1, streamEntry2), Sets.newHashSet(results));

    results = dataset.search("ns1", "  valu*  sVal* ", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertEquals(Sets.newHashSet(flowEntry1, flowEntry2, streamEntry1, streamEntry2), Sets.newHashSet(results));

    // Using empty filter should also search for all target types
    results = dataset.search("ns1", "  valu*  sVal* ", ImmutableSet.<MetadataSearchTargetType>of());
    Assert.assertEquals(Sets.newHashSet(flowEntry1, flowEntry2, streamEntry1, streamEntry2), Sets.newHashSet(results));
  }

  @Test
  public void testSearchIncludesSystemEntities() {
    // Use the same artifact in two different namespaces - system and ns2
    co.cask.cdap.proto.id.ArtifactId sysArtifact = co.cask.cdap.proto.id.ArtifactId.fromIdParts(Arrays.asList(
      NamespaceId.SYSTEM.getNamespace(), "artifact", "1.0"));
    co.cask.cdap.proto.id.ArtifactId ns2Artifact = co.cask.cdap.proto.id.ArtifactId.fromIdParts(Arrays.asList(
      "ns2", "artifact", "1.0"));
    String multiWordKey = "multiword";
    String multiWordValue = "aV1 av2 ,  -  ,  av3 - av4_av5 av6";
    dataset.setProperty(flow1, multiWordKey, multiWordValue);
    dataset.setProperty(sysArtifact, multiWordKey, multiWordValue);
    dataset.setProperty(ns2Artifact, multiWordKey, multiWordValue);
    // perform the exact same multiword search in the 'ns1' namespace. It should return the system artifact along with
    // matched entities in the 'ns1' namespace
    MetadataEntry flowMultiWordEntry = new MetadataEntry(flow1, multiWordKey, multiWordValue);
    MetadataEntry systemArtifactEntry = new MetadataEntry(sysArtifact, multiWordKey, multiWordValue);
    MetadataEntry ns2ArtifactEntry = new MetadataEntry(ns2Artifact, multiWordKey, multiWordValue);
    List<MetadataEntry> results = dataset.search("ns1", "aV5", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertEquals(Sets.newHashSet(flowMultiWordEntry, systemArtifactEntry), Sets.newHashSet(results));
    // search only programs - should only return flow
    results = dataset.search("ns1", multiWordKey + MetadataDataset.KEYVALUE_SEPARATOR + "aV5",
                             ImmutableSet.of(MetadataSearchTargetType.PROGRAM));
    Assert.assertEquals(ImmutableList.of(flowMultiWordEntry), results);
    // search only artifacts - should only return system artifact
    results = dataset.search("ns1", multiWordKey + MetadataDataset.KEYVALUE_SEPARATOR + multiWordValue,
                             ImmutableSet.of(MetadataSearchTargetType.ARTIFACT));
    // this query returns the system artifact 4 times, since the dataset returns a list with duplicates for scoring
    // purposes. Convert to a Set for comparison.
    Assert.assertEquals(Sets.newHashSet(systemArtifactEntry), Sets.newHashSet(results));
    // search all entities in namespace 'ns2' - should return the system artifact and the same artifact in ns2
    results = dataset.search("ns2", multiWordKey + MetadataDataset.KEYVALUE_SEPARATOR + "aV4",
                             ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertEquals(Sets.newHashSet(systemArtifactEntry, ns2ArtifactEntry), Sets.newHashSet(results));
    // search only programs in a namespace 'ns2'. Should return empty
    results = dataset.search("ns2", "aV*", ImmutableSet.of(MetadataSearchTargetType.PROGRAM));
    Assert.assertTrue(results.isEmpty());
    // search all entities in namespace 'ns3'. Should return only the system artifact
    results = dataset.search("ns3", "av*", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertEquals(Sets.newHashSet(systemArtifactEntry), Sets.newHashSet(results));
    // search the system namespace for all entities. Should return only the system artifact
    results = dataset.search(Id.Namespace.SYSTEM.getId(), "av*", ImmutableSet.of(MetadataSearchTargetType.ALL));
    Assert.assertEquals(Sets.newHashSet(systemArtifactEntry), Sets.newHashSet(results));
    // clean up
    dataset.removeProperties(flow1);
    dataset.removeProperties(sysArtifact);
  }

  @Test
  public void testUpdateSearch() throws Exception {
    dataset.setProperty(flow1, "key1", "value1");
    dataset.setProperty(flow1, "key2", "value2");
    dataset.addTags(flow1, "tag1", "tag2");

    Assert.assertEquals(ImmutableList.of(new MetadataEntry(flow1, "key1", "value1")),
                        dataset.search(flow1.getNamespace(), "value1", ImmutableSet.<MetadataSearchTargetType>of()));
    Assert.assertEquals(ImmutableList.of(new MetadataEntry(flow1, "key2", "value2")),
                        dataset.search(flow1.getNamespace(), "value2", ImmutableSet.<MetadataSearchTargetType>of()));
    Assert.assertEquals(ImmutableList.of(new MetadataEntry(flow1, MetadataDataset.TAGS_KEY, "tag1,tag2")),
                        dataset.search(flow1.getNamespace(), "tag2", ImmutableSet.<MetadataSearchTargetType>of()));
    // Update key1
    dataset.setProperty(flow1, "key1", "value3");
    dataset.removeProperties(flow1, "key2");
    dataset.removeTags(flow1, "tag2");

    // Searching for value1 should be empty
    Assert.assertEquals(ImmutableList.of(),
                        dataset.search(flow1.getNamespace(), "value1", ImmutableSet.<MetadataSearchTargetType>of()));
    // Instead key1 has value value3 now
    Assert.assertEquals(ImmutableList.of(new MetadataEntry(flow1, "key1", "value3")),
                        dataset.search(flow1.getNamespace(), "value3", ImmutableSet.<MetadataSearchTargetType>of()));
    // key2 was deleted
    Assert.assertEquals(ImmutableList.of(),
                        dataset.search(flow1.getNamespace(), "value2", ImmutableSet.<MetadataSearchTargetType>of()));
    // tag2 was deleted
    Assert.assertEquals(ImmutableList.of(),
                        dataset.search(flow1.getNamespace(), "tag2", ImmutableSet.<MetadataSearchTargetType>of()));
    Assert.assertEquals(ImmutableList.of(new MetadataEntry(flow1, MetadataDataset.TAGS_KEY, "tag1")),
                        dataset.search(flow1.getNamespace(), "tag1", ImmutableSet.<MetadataSearchTargetType>of()));
  }

  @Test
  public void testMultiGet() throws Exception {
    Map<NamespacedId, Metadata> allMetadata = new HashMap<>();
    allMetadata.put(flow1, new Metadata(flow1,
                                        ImmutableMap.of("key1", "value1", "key2", "value2"),
                                        ImmutableSet.of("tag1", "tag2", "tag3")));
    allMetadata.put(dataset1, new Metadata(dataset1,
                                           ImmutableMap.of("key10", "value10", "key11", "value11"),
                                           ImmutableSet.<String>of()));
    allMetadata.put(app1, new Metadata(app1,
                                       ImmutableMap.of("key20", "value20", "key21", "value21"),
                                       ImmutableSet.<String>of()));
    allMetadata.put(stream1, new Metadata(stream1,
                                          ImmutableMap.of("key30", "value30", "key31", "value31", "key32", "value32"),
                                          ImmutableSet.<String>of()));
    allMetadata.put(artifact1, new Metadata(artifact1,
                                            ImmutableMap.of("key40", "value41"),
                                            ImmutableSet.<String>of()));
    allMetadata.put(view1, new Metadata(view1,
                                        ImmutableMap.of("key50", "value50", "key51", "value51"),
                                        ImmutableSet.of("tag51")));

    for (Map.Entry<NamespacedId, Metadata> entry : allMetadata.entrySet()) {
      Metadata metadata = entry.getValue();
      for (Map.Entry<String, String> props : metadata.getProperties().entrySet()) {
        dataset.setProperty(metadata.getEntityId(), props.getKey(), props.getValue());
      }
      dataset.addTags(metadata.getEntityId(), metadata.getTags().toArray(new String[metadata.getTags().size()]));
    }

    ImmutableSet<Metadata> expected =
      ImmutableSet.<Metadata>builder()
        .add(allMetadata.get(flow1))
        .add(allMetadata.get(app1))
        .build();
    Assert.assertEquals(expected, dataset.getMetadata(ImmutableSet.of(flow1, app1)));

    expected =
      ImmutableSet.<Metadata>builder()
        .add(allMetadata.get(view1))
        .add(allMetadata.get(stream1))
        .add(allMetadata.get(dataset1))
        .add(allMetadata.get(artifact1))
        .build();
    Assert.assertEquals(expected, dataset.getMetadata(ImmutableSet.of(view1, stream1, dataset1, artifact1)));

    expected =
      ImmutableSet.<Metadata>builder()
        .add(allMetadata.get(artifact1))
        .build();
    Assert.assertEquals(expected, dataset.getMetadata(ImmutableSet.of(artifact1)));

    expected = ImmutableSet.of();
    Assert.assertEquals(expected, dataset.getMetadata(ImmutableSet.<NamespacedId>of()));
  }

  @Test
  public void testDelete() throws Exception {
    dataset.setProperty(flow1, "key1", "value1");
    dataset.setProperty(flow1, "key2", "value2");
    dataset.addTags(flow1, "tag1", "tag2");

    dataset.setProperty(app1, "key10", "value10");
    dataset.setProperty(app1, "key12", "value12");
    dataset.addTags(app1, "tag11", "tag12");

    Assert.assertEquals(ImmutableMap.of("key1", "value1", "key2", "value2"), dataset.getProperties(flow1));
    Assert.assertEquals(ImmutableSet.of("tag1", "tag2"), dataset.getTags(flow1));
    Assert.assertEquals(ImmutableMap.of("key10", "value10", "key12", "value12"), dataset.getProperties(app1));
    Assert.assertEquals(ImmutableSet.of("tag11", "tag12"), dataset.getTags(app1));

    // Delete all tags for flow1, and delete all properties for app1
    dataset.removeTags(flow1);
    dataset.removeProperties(app1);

    Assert.assertEquals(ImmutableMap.of("key1", "value1", "key2", "value2"), dataset.getProperties(flow1));
    Assert.assertEquals(ImmutableSet.of(), dataset.getTags(flow1));
    Assert.assertEquals(ImmutableMap.of(), dataset.getProperties(app1));
    Assert.assertEquals(ImmutableSet.of("tag11", "tag12"), dataset.getTags(app1));
  }

  @Test
  public void testHistory() throws Exception {
    MetadataDataset dataset =
      getDataset(Id.DatasetInstance.from(DatasetFrameworkTestUtil.NAMESPACE_ID, "testHistory"));

    doTestHistory(dataset, flow1, "f_");
    doTestHistory(dataset, app1, "a_");
    doTestHistory(dataset, dataset1, "d_");
    doTestHistory(dataset, stream1, "s_");
  }

  @Test
  public void testIndexRebuilding() throws Exception {
    MetadataDataset dataset =
      getDataset(Id.DatasetInstance.from(DatasetFrameworkTestUtil.NAMESPACE_ID, "testIndexRebuilding"));
    dataset.setProperty(flow1, "flowKey", "flowValue", new ReversingIndexer());
    dataset.setProperty(dataset1, "datasetKey", "datasetValue", new ReversingIndexer());
    String namespaceId = flow1.getNamespace();
    Set<MetadataSearchTargetType> targetTypes = Collections.singleton(MetadataSearchTargetType.ALL);
    List<MetadataEntry> searchResults = dataset.search(namespaceId, "flowValue", targetTypes);
    Assert.assertTrue(searchResults.isEmpty());
    searchResults = dataset.search(namespaceId, "flowKey:flow*", targetTypes);
    Assert.assertTrue(searchResults.isEmpty());
    searchResults = dataset.search(namespaceId, "datasetValue", targetTypes);
    Assert.assertTrue(searchResults.isEmpty());
    searchResults = dataset.search(namespaceId, "datasetKey:dataset*", targetTypes);
    Assert.assertTrue(searchResults.isEmpty());
    // Re-build indexes. Now the default indexer should be used
    byte[] startRowKeyForNextBatch = dataset.rebuildIndexes(null, 1);
    Assert.assertNotNull(startRowKeyForNextBatch);
    List<MetadataEntry> flowSearchResults = dataset.search(namespaceId, "flowValue", targetTypes);
    List<MetadataEntry> dsSearchResults = dataset.search(namespaceId, "datasetValue", targetTypes);
    if (!flowSearchResults.isEmpty()) {
      Assert.assertEquals(1, flowSearchResults.size());
      flowSearchResults = dataset.search(namespaceId, "flowKey:flow*", targetTypes);
      Assert.assertEquals(1, flowSearchResults.size());
      Assert.assertTrue(dsSearchResults.isEmpty());
      dsSearchResults = dataset.search(namespaceId, "datasetKey:dataset*", targetTypes);
      Assert.assertTrue(dsSearchResults.isEmpty());
    } else {
      flowSearchResults = dataset.search(namespaceId, "flowKey:flow*", targetTypes);
      Assert.assertTrue(flowSearchResults.isEmpty());
      Assert.assertEquals(1, dsSearchResults.size());
      dsSearchResults = dataset.search(namespaceId, "datasetKey:dataset*", targetTypes);
      Assert.assertEquals(1, dsSearchResults.size());
    }
    startRowKeyForNextBatch = dataset.rebuildIndexes(startRowKeyForNextBatch, 1);
    Assert.assertNull(startRowKeyForNextBatch);
    searchResults = dataset.search(namespaceId, "flowValue", targetTypes);
    Assert.assertEquals(1, searchResults.size());
    searchResults = dataset.search(namespaceId, "flowKey:flow*", targetTypes);
    Assert.assertEquals(1, searchResults.size());
    searchResults = dataset.search(namespaceId, "datasetValue", targetTypes);
    Assert.assertEquals(1, searchResults.size());
    searchResults = dataset.search(namespaceId, "datasetKey:dataset*", targetTypes);
    Assert.assertEquals(1, searchResults.size());
  }

  @Test
  public void testIndexDeletion() throws Exception {
    MetadataDataset dataset =
      getDataset(Id.DatasetInstance.from(DatasetFrameworkTestUtil.NAMESPACE_ID, "testIndexRebuilding"));
    dataset.setProperty(flow1, "flowKey", "flowValue");
    dataset.setProperty(dataset1, "datasetKey", "datasetValue");
    String namespaceId = flow1.getNamespace();
    Set<MetadataSearchTargetType> targetTypes = Collections.singleton(MetadataSearchTargetType.ALL);
    MetadataEntry expectedFlowEntry = new MetadataEntry(flow1, "flowKey", "flowValue");
    MetadataEntry expectedDatasetEntry = new MetadataEntry(dataset1, "datasetKey", "datasetValue");
    List<MetadataEntry> searchResults = dataset.search(namespaceId, "flowValue", targetTypes);
    Assert.assertEquals(ImmutableList.of(expectedFlowEntry), searchResults);
    searchResults = dataset.search(namespaceId, "flowKey:flow*", targetTypes);
    Assert.assertEquals(ImmutableList.of(expectedFlowEntry), searchResults);
    searchResults = dataset.search(namespaceId, "datasetValue", targetTypes);
    Assert.assertEquals(ImmutableList.of(expectedDatasetEntry), searchResults);
    searchResults = dataset.search(namespaceId, "datasetKey:dataset*", targetTypes);
    Assert.assertEquals(ImmutableList.of(expectedDatasetEntry), searchResults);
    // delete indexes
    // 4 indexes should have been deleted - flowValue, flowKey:flowValue, datasetValue, datasetKey:datasetValue
    for (int i = 0; i < 4; i++) {
      Assert.assertEquals(1, dataset.deleteAllIndexes(1));
    }
    Assert.assertEquals(0, dataset.deleteAllIndexes(1));
  }

  private void doTestHistory(MetadataDataset dataset, NamespacedId targetId, String prefix)
    throws Exception {
    // Metadata change history keyed by time in millis the change was made
    Map<Long, Metadata> expected = new HashMap<>();

    // No history for targetId at the beginning
    Metadata completeRecord = new Metadata(targetId);
    expected.put(System.currentTimeMillis(), completeRecord);
    // Get history for targetId, should be empty
    Assert.assertEquals(ImmutableSet.of(completeRecord),
                        dataset.getSnapshotBeforeTime(ImmutableSet.of(targetId), System.currentTimeMillis()));
    // Also, the metadata itself should be equal to the last recorded snapshot
    Assert.assertEquals(getFirst(dataset.getSnapshotBeforeTime(ImmutableSet.of(targetId), System.currentTimeMillis())),
                        new Metadata(targetId, dataset.getProperties(targetId), dataset.getTags(targetId)));
    // Since the key to expected map is time in millis, sleep for a millisecond to make sure the key is distinct
    TimeUnit.MILLISECONDS.sleep(1);

    // Add first record
    completeRecord = new Metadata(targetId, toProps(prefix, "k1", "v1"), toTags(prefix, "t1", "t2"));
    addMetadataHistory(dataset, completeRecord);
    long time = System.currentTimeMillis();
    expected.put(time, completeRecord);
    // Since this is the first record, history should be the same as what was added.
    Assert.assertEquals(ImmutableSet.of(completeRecord),
                        dataset.getSnapshotBeforeTime(ImmutableSet.of(targetId), time));
    // Also, the metadata itself should be equal to the last recorded snapshot
    Assert.assertEquals(getFirst(dataset.getSnapshotBeforeTime(ImmutableSet.of(targetId), System.currentTimeMillis())),
                        new Metadata(targetId, dataset.getProperties(targetId), dataset.getTags(targetId)));
    TimeUnit.MILLISECONDS.sleep(1);

    // Add a new property and a tag
    dataset.setProperty(targetId, prefix + "k2", "v2");
    dataset.addTags(targetId, prefix + "t3");
    // Save the complete metadata record at this point
    completeRecord = new Metadata(targetId, toProps(prefix, "k1", "v1", "k2", "v2"),
                                  toTags(prefix, "t1", "t2", "t3"));
    time = System.currentTimeMillis();
    expected.put(time, completeRecord);
    // Assert the history record with the change
    Assert.assertEquals(ImmutableSet.of(completeRecord),
                        dataset.getSnapshotBeforeTime(ImmutableSet.of(targetId), time));
    // Also, the metadata itself should be equal to the last recorded snapshot
    Assert.assertEquals(getFirst(dataset.getSnapshotBeforeTime(ImmutableSet.of(targetId), System.currentTimeMillis())),
                        new Metadata(targetId, dataset.getProperties(targetId), dataset.getTags(targetId)));
    TimeUnit.MILLISECONDS.sleep(1);

    // Add another property and a tag
    dataset.setProperty(targetId, prefix + "k3", "v3");
    dataset.addTags(targetId, prefix + "t4");
    // Save the complete metadata record at this point
    completeRecord = new Metadata(targetId, toProps(prefix, "k1", "v1", "k2", "v2", "k3", "v3"),
                                  toTags(prefix, "t1", "t2", "t3", "t4"));
    time = System.currentTimeMillis();
    expected.put(time, completeRecord);
    // Assert the history record with the change
    Assert.assertEquals(ImmutableSet.of(completeRecord),
                        dataset.getSnapshotBeforeTime(ImmutableSet.of(targetId), time));
    // Also, the metadata itself should be equal to the last recorded snapshot
    Assert.assertEquals(getFirst(dataset.getSnapshotBeforeTime(ImmutableSet.of(targetId), System.currentTimeMillis())),
                        new Metadata(targetId, dataset.getProperties(targetId), dataset.getTags(targetId)));
    TimeUnit.MILLISECONDS.sleep(1);

    // Add the same property and tag as second time
    dataset.setProperty(targetId, prefix + "k2", "v2");
    dataset.addTags(targetId, prefix + "t3");
    // Save the complete metadata record at this point
    completeRecord = new Metadata(targetId, toProps(prefix, "k1", "v1", "k2", "v2", "k3", "v3"),
                                  toTags(prefix, "t1", "t2", "t3", "t4"));
    time = System.currentTimeMillis();
    expected.put(time, completeRecord);
    // Assert the history record with the change
    Assert.assertEquals(ImmutableSet.of(completeRecord),
                        dataset.getSnapshotBeforeTime(ImmutableSet.of(targetId), time));
    // Also, the metadata itself should be equal to the last recorded snapshot
    Assert.assertEquals(getFirst(dataset.getSnapshotBeforeTime(ImmutableSet.of(targetId), System.currentTimeMillis())),
                        new Metadata(targetId, dataset.getProperties(targetId), dataset.getTags(targetId)));
    TimeUnit.MILLISECONDS.sleep(1);

    // Remove a property and two tags
    dataset.removeProperties(targetId, prefix + "k2");
    dataset.removeTags(targetId, prefix + "t4");
    dataset.removeTags(targetId, prefix + "t2");
    // Save the complete metadata record at this point
    completeRecord = new Metadata(targetId, toProps(prefix, "k1", "v1", "k3", "v3"),
                                  toTags(prefix, "t1", "t3"));
    time = System.currentTimeMillis();
    expected.put(time, completeRecord);
    // Assert the history record with the change
    Assert.assertEquals(ImmutableSet.of(completeRecord),
                        dataset.getSnapshotBeforeTime(ImmutableSet.of(targetId), time));
    // Also, the metadata itself should be equal to the last recorded snapshot
    Assert.assertEquals(getFirst(dataset.getSnapshotBeforeTime(ImmutableSet.of(targetId), System.currentTimeMillis())),
                        new Metadata(targetId, dataset.getProperties(targetId), dataset.getTags(targetId)));
    TimeUnit.MILLISECONDS.sleep(1);

    // Remove all properties and all tags
    dataset.removeProperties(targetId);
    dataset.removeTags(targetId);
    // Save the complete metadata record at this point
    completeRecord = new Metadata(targetId);
    time = System.currentTimeMillis();
    expected.put(time, completeRecord);
    // Assert the history record with the change
    Assert.assertEquals(ImmutableSet.of(completeRecord),
                        dataset.getSnapshotBeforeTime(ImmutableSet.of(targetId), time));
    // Also, the metadata itself should be equal to the last recorded snapshot
    Assert.assertEquals(getFirst(dataset.getSnapshotBeforeTime(ImmutableSet.of(targetId), System.currentTimeMillis())),
                        new Metadata(targetId, dataset.getProperties(targetId), dataset.getTags(targetId)));
    TimeUnit.MILLISECONDS.sleep(1);

    // Add one more property and a tag
    dataset.setProperty(targetId, prefix + "k2", "v2");
    dataset.addTags(targetId, prefix + "t2");
    // Save the complete metadata record at this point
    completeRecord = new Metadata(targetId, toProps(prefix, "k2", "v2"),
                                  toTags(prefix, "t2"));
    time = System.currentTimeMillis();
    expected.put(time, completeRecord);
    // Assert the history record with the change
    Assert.assertEquals(ImmutableSet.of(completeRecord),
                        dataset.getSnapshotBeforeTime(ImmutableSet.of(targetId), time));
    // Also, the metadata itself should be equal to the last recorded snapshot
    Assert.assertEquals(getFirst(dataset.getSnapshotBeforeTime(ImmutableSet.of(targetId), System.currentTimeMillis())),
                        new Metadata(targetId, dataset.getProperties(targetId), dataset.getTags(targetId)));
    TimeUnit.MILLISECONDS.sleep(1);

    // Now assert all history
    for (Map.Entry<Long, Metadata> entry : expected.entrySet()) {
      Assert.assertEquals(entry.getValue(),
                          getFirst(dataset.getSnapshotBeforeTime(ImmutableSet.of(targetId), entry.getKey())));
    }

    // Asserting for current time should give the latest record
    Assert.assertEquals(ImmutableSet.of(completeRecord),
                        dataset.getSnapshotBeforeTime(ImmutableSet.of(targetId), System.currentTimeMillis()));
    // Also, the metadata itself should be equal to the last recorded snapshot
    Assert.assertEquals(getFirst(dataset.getSnapshotBeforeTime(ImmutableSet.of(targetId), System.currentTimeMillis())),
                        new Metadata(targetId, dataset.getProperties(targetId), dataset.getTags(targetId)));
  }

  private void addMetadataHistory(MetadataDataset dataset, Metadata record) {
    for (Map.Entry<String, String> entry : record.getProperties().entrySet()) {
      dataset.setProperty(record.getEntityId(), entry.getKey(), entry.getValue());
    }
    //noinspection ToArrayCallWithZeroLengthArrayArgument
    dataset.addTags(record.getEntityId(), record.getTags().toArray(new String[0]));
  }

  private Map<String, String> toProps(String prefix, String k1, String v1) {
    return ImmutableMap.of(prefix + k1, v1);
  }

  private Map<String, String> toProps(String prefix, String k1, String v1, String k2, String v2) {
    return ImmutableMap.of(prefix + k1, v1, prefix + k2, v2);
  }

  private Map<String, String> toProps(String prefix, String k1, String v1, String k2, String v2, String k3, String v3) {
    return ImmutableMap.of(prefix + k1, v1, prefix + k2, v2, prefix + k3, v3);
  }

  private Set<String> toTags(String prefix, String... tags) {
    ImmutableSet.Builder<String> builder = new ImmutableSet.Builder<>();
    for (String tag : tags) {
      builder.add(prefix + tag);
    }
    return builder.build();
  }

  private <T> T getFirst(Iterable<T> iterable) {
    Assert.assertEquals(1, Iterables.size(iterable));
    return iterable.iterator().next();
  }

  private static MetadataDataset getDataset(Id.DatasetInstance instance) throws Exception {
    return DatasetsUtil.getOrCreateDataset(dsFrameworkUtil.getFramework(), instance,
                                           MetadataDataset.class.getName(),
                                           DatasetProperties.EMPTY, null, null);
  }

  private static final class ReversingIndexer implements Indexer {

    @Override
    public Set<String> getIndexes(MetadataEntry entry) {
      return ImmutableSet.of(reverse(entry.getKey()), reverse(entry.getValue()));
    }

    private String reverse(String toReverse) {
      return new StringBuilder(toReverse).reverse().toString();
    }
  }
}
