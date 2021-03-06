/*
 * Druid - a distributed column store.
 *  Copyright 2012 - 2015 Metamarkets Group Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.druid.storage.azure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.metamx.common.MapUtils;
import com.microsoft.azure.storage.StorageException;
import io.druid.segment.loading.DataSegmentPusherUtil;
import io.druid.timeline.DataSegment;
import io.druid.timeline.partition.NoneShardSpec;
import org.easymock.EasyMockSupport;
import org.joda.time.Interval;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.junit.Assert.assertEquals;

public class AzureDataSegmentPusherTest extends EasyMockSupport
{
  private static final String containerName = "container";
  private static final String blobPath = "test/2015-04-12T00:00:00.000Z_2015-04-13T00:00:00.000Z/1/0/index.zip";
  private static final DataSegment dataSegment = new DataSegment(
      "test",
      new Interval("2015-04-12/2015-04-13"),
      "1",
      ImmutableMap.<String, Object>of("containerName", containerName, "blobPath", blobPath),
      null,
      null,
      new NoneShardSpec(),
      0,
      1
  );

  private AzureStorage azureStorage;
  private AzureAccountConfig azureAccountConfig;
  private ObjectMapper jsonMapper;

  @Before
  public void before()
  {
    azureStorage = createMock(AzureStorage.class);
    azureAccountConfig = createMock(AzureAccountConfig.class);
    jsonMapper = createMock(ObjectMapper.class);

  }

  @Test
  public void getAzurePathsTest()
  {
    final String storageDir = DataSegmentPusherUtil.getStorageDir(dataSegment);
    AzureDataSegmentPusher pusher = new AzureDataSegmentPusher(azureStorage, azureAccountConfig, jsonMapper);

    Map<String, String> paths = pusher.getAzurePaths(dataSegment);

    assertEquals(String.format("%s/%s", storageDir, AzureStorageDruidModule.INDEX_ZIP_FILE_NAME), paths.get("index"));
    assertEquals(
        String.format("%s/%s", storageDir, AzureStorageDruidModule.DESCRIPTOR_FILE_NAME),
        paths.get("descriptor")
    );
  }

  @Test
  public void uploadDataSegmentTest() throws StorageException, IOException, URISyntaxException
  {
    AzureDataSegmentPusher pusher = new AzureDataSegmentPusher(azureStorage, azureAccountConfig, jsonMapper);
    final int version = 9;
    final File compressedSegmentData = new File("index.zip");
    final File descriptorFile = new File("descriptor.json");
    final Map<String, String> azurePaths = pusher.getAzurePaths(dataSegment);

    expect(azureAccountConfig.getContainer()).andReturn(containerName).times(3);
    azureStorage.uploadBlob(compressedSegmentData, containerName, azurePaths.get("index"));
    expectLastCall();
    azureStorage.uploadBlob(descriptorFile, containerName, azurePaths.get("descriptor"));
    expectLastCall();

    replayAll();

    DataSegment pushedDataSegment = pusher.uploadDataSegment(
        dataSegment,
        version,
        compressedSegmentData,
        descriptorFile,
        azurePaths
    );

    assertEquals(compressedSegmentData.length(), pushedDataSegment.getSize());
    assertEquals(version, (int) pushedDataSegment.getBinaryVersion());
    Map<String, Object> loadSpec = pushedDataSegment.getLoadSpec();
    assertEquals(AzureStorageDruidModule.SCHEME, MapUtils.getString(loadSpec, "type"));
    assertEquals(azurePaths.get("index"), MapUtils.getString(loadSpec, "blobPath"));

    verifyAll();

  }

}
