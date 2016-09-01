/*
 * Copyright © 2015 Cask Data, Inc.
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

package co.cask.cdap.data2.dataset2.lib.table;

import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.data.batch.Split;
import co.cask.cdap.api.data.batch.SplitReader;
import co.cask.cdap.api.dataset.lib.CloseableIterator;
import co.cask.cdap.api.dataset.lib.KeyValue;
import co.cask.cdap.api.dataset.lib.ObjectMappedTable;
import co.cask.cdap.api.dataset.lib.ObjectMappedTableProperties;
import co.cask.cdap.api.dataset.table.Scan;
import co.cask.cdap.data2.dataset2.DatasetFrameworkTestUtil;
import co.cask.cdap.proto.Id;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Test for {@link ObjectMappedTableDataset}.
 */
public class ObjectMappedTableDatasetTest {
  @ClassRule
  public static DatasetFrameworkTestUtil dsFrameworkUtil = new DatasetFrameworkTestUtil();

  private static final Id.DatasetInstance RECORDS_ID =
    Id.DatasetInstance.from(DatasetFrameworkTestUtil.NAMESPACE_ID, "records");

  @Test
  public void testGetPutDelete() throws Exception {
    dsFrameworkUtil.createInstance(ObjectMappedTable.class.getName(), RECORDS_ID,
                                   ObjectMappedTableProperties.builder().setType(Record.class).build());
    try {
      ObjectMappedTableDataset<Record> records = dsFrameworkUtil.getInstance(RECORDS_ID);
      Record record = new Record(Integer.MAX_VALUE, Long.MAX_VALUE, Float.MAX_VALUE, null, "foobar",
                                 Bytes.toBytes("foobar"), ByteBuffer.wrap(Bytes.toBytes("foobar")), UUID.randomUUID());
      records.write("123", record);
      Record actual = records.read("123");
      Assert.assertEquals(record, actual);
      record = new Record(Integer.MAX_VALUE, Long.MAX_VALUE, null, Double.MAX_VALUE, "foobar",
                          Bytes.toBytes("foobar"), ByteBuffer.wrap(Bytes.toBytes("foobar")), UUID.randomUUID());
      records.write("123", record);
      actual = records.read("123");
      Assert.assertEquals(record, actual);
      records.delete("123");
      Assert.assertNull(records.read("123"));
    } finally {
      dsFrameworkUtil.deleteInstance(RECORDS_ID);
    }
  }

  @Test
  public void testScan() throws Exception {
    dsFrameworkUtil.createInstance(ObjectMappedTable.class.getName(), RECORDS_ID,
                                   ObjectMappedTableProperties.builder().setType(Record.class).build());
    try {
      ObjectMappedTableDataset<Record> records = dsFrameworkUtil.getInstance(RECORDS_ID);
      Record record1 = new Record(Integer.MAX_VALUE, Long.MAX_VALUE, Float.MAX_VALUE, Double.MAX_VALUE, "foobar",
                                  Bytes.toBytes("foobar"), ByteBuffer.wrap(Bytes.toBytes("foobar")), UUID.randomUUID());
      Record record2 = new Record(Integer.MIN_VALUE, Long.MIN_VALUE, Float.MIN_VALUE, Double.MIN_VALUE, "baz",
                                  Bytes.toBytes("baz"), ByteBuffer.wrap(Bytes.toBytes("baz")), UUID.randomUUID());
      Record record3 = new Record(1, 0L, 3.14f, 3.14159265358979323846, "hello",
                                  Bytes.toBytes("world"), ByteBuffer.wrap(Bytes.toBytes("yo")), UUID.randomUUID());
      List<KeyValue<byte[], Record>> recordList = Lists.newArrayList();
      recordList.add(new KeyValue<>(Bytes.toBytes("123"), record1));
      recordList.add(new KeyValue<>(Bytes.toBytes("456"), record2));
      recordList.add(new KeyValue<>(Bytes.toBytes("789"), record3));

      for (KeyValue<byte[], Record> record : recordList) {
        records.write(record.getKey(), record.getValue());
      }

      List<KeyValue<byte[], Record>> actualList = Lists.newArrayList();
      CloseableIterator<KeyValue<byte[], Record>> results = records.scan((String) null, null);
      while (results.hasNext()) {
        actualList.add(results.next());
      }
      results.close();
      Assert.assertEquals(recordList.size(), actualList.size());
      for (int i = 0; i < actualList.size(); i++) {
        KeyValue<byte[], Record> expected = recordList.get(i);
        KeyValue<byte[], Record> actual = actualList.get(i);
        Assert.assertArrayEquals(expected.getKey(), actual.getKey());
        Assert.assertEquals(expected.getValue(), actual.getValue());
      }

      results = records.scan("789", null);
      KeyValue<byte[], Record> actualRecord = results.next();
      Assert.assertFalse(results.hasNext());
      Assert.assertArrayEquals(actualRecord.getKey(), recordList.get(2).getKey());
      Assert.assertEquals(actualRecord.getValue(), recordList.get(2).getValue());
      results.close();

      results = records.scan(null, "124");
      actualRecord = results.next();
      Assert.assertFalse(results.hasNext());
      Assert.assertArrayEquals(actualRecord.getKey(), recordList.get(0).getKey());
      Assert.assertEquals(actualRecord.getValue(), recordList.get(0).getValue());
      results.close();

      results = records.scan(null, "123");
      Assert.assertFalse(results.hasNext());
      results.close();

      Scan scan = new Scan(null, null);
      results = records.scan(scan);
      actualList = Lists.newArrayList();
      while (results.hasNext()) {
        actualList.add(results.next());
      }
      Assert.assertEquals(recordList.size(), actualList.size());
    } finally {
      dsFrameworkUtil.deleteInstance(RECORDS_ID);
    }
  }

  @Test
  public void testGetSplits() throws Exception {
    dsFrameworkUtil.createInstance(ObjectMappedTable.class.getName(), RECORDS_ID,
                                   ObjectMappedTableProperties.builder().setType(Record.class).build());
    try {
      ObjectMappedTableDataset<Record> records = dsFrameworkUtil.getInstance(RECORDS_ID);
      Record record = new Record(Integer.MAX_VALUE, Long.MAX_VALUE, Float.MAX_VALUE, Double.MAX_VALUE, "foobar",
                                  Bytes.toBytes("foobar"), ByteBuffer.wrap(Bytes.toBytes("foobar")), UUID.randomUUID());
      byte[] rowkey = Bytes.toBytes("row1");
      records.write(rowkey, record);

      // should not include the record, since upper bound is not inclusive
      List<Split> splits = records.getSplits(1, null, rowkey);
      List<Record> recordsRead = new ArrayList<>();
      for (Split split : splits) {
        SplitReader<byte[], Record> splitReader = records.createSplitReader(split);
        try {
          splitReader.initialize(split);
          while (splitReader.nextKeyValue()) {
            recordsRead.add(splitReader.getCurrentValue());
          }
        } finally {
          splitReader.close();
        }
      }
      Assert.assertEquals(0, recordsRead.size());

      // should include the record, since lower bound is inclusive
      splits = records.getSplits(1, rowkey, null);
      recordsRead.clear();
      for (Split split : splits) {
        SplitReader<byte[], Record> splitReader = records.createSplitReader(split);
        try {
          splitReader.initialize(split);
          while (splitReader.nextKeyValue()) {
            recordsRead.add(splitReader.getCurrentValue());
          }
        } finally {
          splitReader.close();
        }
      }
      Assert.assertEquals(1, recordsRead.size());
      Assert.assertEquals(record, recordsRead.get(0));
    } finally {
      dsFrameworkUtil.deleteInstance(RECORDS_ID);
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidTypeFails() throws Exception {
    dsFrameworkUtil.createInstance(ObjectMappedTable.class.getName(),
                                   Id.DatasetInstance.from(DatasetFrameworkTestUtil.NAMESPACE_ID, "custom"),
                                   ObjectMappedTableProperties.builder().setType(Custom.class).build());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testRowKeyConflict() throws Exception {
    dsFrameworkUtil.createInstance(ObjectMappedTable.class.getName(),
                                   Id.DatasetInstance.from(DatasetFrameworkTestUtil.NAMESPACE_ID, "record"),
                                   ObjectMappedTableProperties.builder()
                                     .setType(Record.class)
                                     .setRowKeyExploreName("intfield")
                                     .build());
  }
}
