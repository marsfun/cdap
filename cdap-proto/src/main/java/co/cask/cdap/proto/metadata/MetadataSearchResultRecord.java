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
package co.cask.cdap.proto.metadata;

import co.cask.cdap.api.annotation.Beta;
import co.cask.cdap.proto.id.NamespacedEntityId;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Represent the Metadata search result record.
 */
@Beta
public class MetadataSearchResultRecord {
  private final NamespacedEntityId namespacedEntityId;
  private final Map<MetadataScope, Metadata> metadata;

  public MetadataSearchResultRecord(NamespacedEntityId namespacedEntityId) {
    this.namespacedEntityId = namespacedEntityId;
    this.metadata = Collections.emptyMap();
  }

  public MetadataSearchResultRecord(NamespacedEntityId namespacedEntityId, Map<MetadataScope, Metadata> metadata) {
    this.namespacedEntityId = namespacedEntityId;
    this.metadata = metadata;
  }

  public NamespacedEntityId getEntityId() {
    return namespacedEntityId;
  }

  public Map<MetadataScope, Metadata> getMetadata() {
    return metadata;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MetadataSearchResultRecord)) {
      return false;
    }
    MetadataSearchResultRecord that = (MetadataSearchResultRecord) o;
    return Objects.equals(namespacedEntityId, that.namespacedEntityId) &&
      Objects.equals(metadata, that.metadata);
  }

  @Override
  public int hashCode() {
    return Objects.hash(namespacedEntityId, metadata);
  }

  @Override
  public String toString() {
    return "MetadataSearchResultRecord{" +
      "namespacedEntityId=" + namespacedEntityId +
      ", metadata=" + metadata +
      '}';
  }
}
