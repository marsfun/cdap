/*
 * Copyright © 2015-2016 Cask Data, Inc.
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

import co.cask.cdap.proto.id.NamespacedId;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents the complete metadata of a {@link NamespacedId} including its properties, tags in a given
 * {@link MetadataScope}
 */
@Deprecated
public class MetadataRecord {
  private final NamespacedId namespacedId;
  private final MetadataScope scope;
  private final Map<String, String> properties;
  private final Set<String> tags;

  /**
   * Returns an empty {@link MetadataRecord} in the specified {@link MetadataScope}.
   */
  public MetadataRecord(NamespacedId namespacedId, MetadataScope scope) {
    this(namespacedId, scope, Collections.<String, String>emptyMap(), Collections.<String>emptySet());
  }

  /**
   * Returns a new {@link MetadataRecord} from the specified existing {@link MetadataRecord}.
   */
  public MetadataRecord(MetadataRecord other) {
    this(other.getEntityId(), other.getScope(), other.getProperties(), other.getTags());
  }

  public MetadataRecord(NamespacedId namespacedId, MetadataScope scope, Map<String, String> properties,
                        Set<String> tags) {
    this.namespacedId = namespacedId;
    this.scope = scope;
    this.properties = properties;
    this.tags = tags;
  }

  public NamespacedId getEntityId() {
    return namespacedId;
  }

  public MetadataScope getScope() {
    return scope;
  }

  public Map<String, String> getProperties() {
    return properties;
  }

  public Set<String> getTags() {
    return tags;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    MetadataRecord that = (MetadataRecord) o;

    return Objects.equals(namespacedId, that.namespacedId) &&
      scope == that.scope &&
      Objects.equals(properties, that.properties) &&
      Objects.equals(tags, that.tags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(namespacedId, scope, properties, tags);
  }

  @Override
  public String toString() {
    return "MetadataRecord{" +
      "namespacedId=" + namespacedId +
      ", scope=" + scope +
      ", properties=" + properties +
      ", tags=" + tags +
      '}';
  }
}
