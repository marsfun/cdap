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

package co.cask.cdap.data.tools;

import co.cask.cdap.api.ProgramSpecification;
import co.cask.cdap.api.app.ApplicationSpecification;
import co.cask.cdap.api.data.stream.StreamSpecification;
import co.cask.cdap.api.dataset.Dataset;
import co.cask.cdap.api.dataset.DatasetManagementException;
import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.app.store.Store;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.data.dataset.SystemDatasetInstantiator;
import co.cask.cdap.data.dataset.SystemDatasetInstantiatorFactory;
import co.cask.cdap.data.view.ViewAdmin;
import co.cask.cdap.data2.dataset2.DatasetFramework;
import co.cask.cdap.data2.metadata.store.MetadataStore;
import co.cask.cdap.data2.metadata.system.AppSystemMetadataWriter;
import co.cask.cdap.data2.metadata.system.ArtifactSystemMetadataWriter;
import co.cask.cdap.data2.metadata.system.DatasetSystemMetadataWriter;
import co.cask.cdap.data2.metadata.system.ProgramSystemMetadataWriter;
import co.cask.cdap.data2.metadata.system.StreamSystemMetadataWriter;
import co.cask.cdap.data2.metadata.system.SystemMetadataWriter;
import co.cask.cdap.data2.metadata.system.ViewSystemMetadataWriter;
import co.cask.cdap.data2.transaction.stream.StreamAdmin;
import co.cask.cdap.internal.app.runtime.artifact.ArtifactDetail;
import co.cask.cdap.internal.app.runtime.artifact.ArtifactStore;
import co.cask.cdap.proto.DatasetSpecificationSummary;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.NamespaceMeta;
import co.cask.cdap.proto.ProgramType;
import co.cask.cdap.proto.artifact.ArtifactInfo;
import co.cask.cdap.proto.id.ApplicationId;
import co.cask.cdap.proto.id.ArtifactId;
import co.cask.cdap.proto.id.DatasetId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.ProgramId;
import co.cask.cdap.proto.id.StreamId;
import co.cask.cdap.store.NamespaceStore;
import com.google.inject.Inject;
import org.apache.twill.filesystem.LocationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

/**
 * Updates system metadata for existing entities.
 */
public class ExistingEntitySystemMetadataWriter {
  private static final Logger LOG = LoggerFactory.getLogger(ExistingEntitySystemMetadataWriter.class);

  private final MetadataStore metadataStore;
  private final NamespaceStore nsStore;
  private final Store store;
  private final StreamAdmin streamAdmin;
  private final ViewAdmin viewAdmin;
  private final ArtifactStore artifactStore;
  private final LocationFactory locationFactory;
  private final CConfiguration cConf;

  @Inject
  ExistingEntitySystemMetadataWriter(MetadataStore metadataStore, NamespaceStore nsStore, Store store,
                                     ArtifactStore artifactStore, StreamAdmin streamAdmin, ViewAdmin viewAdmin,
                                     LocationFactory locationFactory, CConfiguration cConf) {
    this.metadataStore = metadataStore;
    this.nsStore = nsStore;
    this.store = store;
    this.streamAdmin = streamAdmin;
    this.viewAdmin = viewAdmin;
    this.artifactStore = artifactStore;
    this.locationFactory = locationFactory;
    this.cConf = cConf;
  }

  public void write(DatasetFramework dsFramework) throws Exception {
    for (NamespaceMeta namespaceMeta : nsStore.list()) {
      NamespaceId namespace = NamespaceId.fromString(namespaceMeta.getName());
      writeSystemMetadataForArtifacts(namespace);
      writeSystemMetadataForApps(namespace);
      writeSystemMetadataForDatasets(namespace, dsFramework);
      writeSystemMetadataForStreams(namespace);
    }
  }

  private void writeSystemMetadataForArtifacts(NamespaceId namespace) throws IOException {
    for (ArtifactDetail artifactDetail : artifactStore.getArtifacts(namespace)) {
      ArtifactInfo artifactInfo = new ArtifactInfo(artifactDetail.getDescriptor().getArtifactId(),
                                                   artifactDetail.getMeta().getClasses(),
                                                   artifactDetail.getMeta().getProperties());
      ArtifactId artifactId = ArtifactId.fromIdParts(
        Arrays.asList(namespace.getNamespace(), artifactDetail.getDescriptor().getArtifactId().getName()));
      SystemMetadataWriter writer = new ArtifactSystemMetadataWriter(metadataStore, artifactId, artifactInfo);
      writer.write();
    }
  }

  private void writeSystemMetadataForApps(NamespaceId namespace) {
    for (ApplicationSpecification appSpec : store.getAllApplications(namespace.toId())) {
      ApplicationId app = new ApplicationId(namespace.getNamespace(), appSpec.getName());
      SystemMetadataWriter writer = new AppSystemMetadataWriter(metadataStore, app, appSpec);
      writer.write();
      writeSystemMetadataForPrograms(app, appSpec);
    }
  }

  private void writeSystemMetadataForPrograms(ApplicationId app, ApplicationSpecification appSpec) {
    writeSystemMetadataForPrograms(app, ProgramType.FLOW, appSpec.getFlows().values());
    writeSystemMetadataForPrograms(app, ProgramType.MAPREDUCE, appSpec.getMapReduce().values());
    writeSystemMetadataForPrograms(app, ProgramType.SERVICE, appSpec.getServices().values());
    writeSystemMetadataForPrograms(app, ProgramType.SPARK, appSpec.getSpark().values());
    writeSystemMetadataForPrograms(app, ProgramType.WORKER, appSpec.getWorkers().values());
    writeSystemMetadataForPrograms(app, ProgramType.WORKFLOW, appSpec.getWorkflows().values());
  }

  private void writeSystemMetadataForPrograms(ApplicationId app, ProgramType programType,
                                              Collection<? extends ProgramSpecification> programSpecs) {
    for (ProgramSpecification programSpec : programSpecs) {
      ProgramId programId = app.program(programType, programSpec.getName());
      SystemMetadataWriter writer = new ProgramSystemMetadataWriter(metadataStore, programId, programSpec);
      writer.write();
    }
  }

  private void writeSystemMetadataForDatasets(NamespaceId namespace, DatasetFramework dsFramework)
    throws DatasetManagementException, IOException {
    SystemDatasetInstantiatorFactory systemDatasetInstantiatorFactory =
      new SystemDatasetInstantiatorFactory(locationFactory, dsFramework, cConf);
    try (SystemDatasetInstantiator systemDatasetInstantiator = systemDatasetInstantiatorFactory.create()) {
      for (DatasetSpecificationSummary summary : dsFramework.getInstances(namespace.toId())) {
        DatasetId dsInstance = new DatasetId(namespace.getNamespace(), summary.getName());
        DatasetProperties dsProperties = DatasetProperties.of(summary.getProperties());
        String dsType = summary.getType();
        Dataset dataset = null;
        try {
          try {
            dataset = systemDatasetInstantiator.getDataset(dsInstance.toId());
          } catch (Exception e) {
            LOG.warn("Exception while instantiating dataset {}", dsInstance, e);
          }

          SystemMetadataWriter writer =
            new DatasetSystemMetadataWriter(metadataStore, dsInstance.toId(), dsProperties, dataset, dsType,
                                            summary.getDescription());
          writer.write();
        } finally {
          if (dataset != null) {
            dataset.close();
          }
        }
      }
    }
  }

  private void writeSystemMetadataForStreams(NamespaceId namespace) throws Exception {
    for (StreamSpecification streamSpec : store.getAllStreams(namespace.toId())) {
      StreamId streamId = new StreamId(namespace.getNamespace(), streamSpec.getName());
      SystemMetadataWriter writer =
        new StreamSystemMetadataWriter(metadataStore, streamId.toId(), streamAdmin.getConfig(streamId.toId()),
                                       streamSpec.getDescription());
      writer.write();
      for (Id.Stream.View view : streamAdmin.listViews(streamId.toId())) {
        writer = new ViewSystemMetadataWriter(metadataStore, view, viewAdmin.get(view));
        writer.write();
      }
    }
  }
}
