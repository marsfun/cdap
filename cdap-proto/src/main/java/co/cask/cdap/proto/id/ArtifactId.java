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
package co.cask.cdap.proto.id;

import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.element.EntityType;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

/**
 * Uniquely identifies an artifact.
 */
public class ArtifactId extends NamespacedId implements ParentedId<NamespaceId> {
  private final String namespace;
  private final String artifact;
  private final String version;
  private transient Integer hashCode;

  public ArtifactId(String namespace, String artifact, String version) {
    super(EntityType.ARTIFACT);
    this.namespace = namespace;
    this.artifact = artifact;
    this.version = version;
  }

  public String getNamespace() {
    return namespace;
  }

  public String getArtifact() {
    return artifact;
  }

  @Override
  public String getEntityName() {
    return getArtifact();
  }

  public String getVersion() {
    return version;
  }

  @Override
  public NamespaceId getParent() {
    return new NamespaceId(namespace);
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    ArtifactId that = (ArtifactId) o;
    return Objects.equals(namespace, that.namespace) &&
      Objects.equals(artifact, that.artifact) &&
      Objects.equals(version, that.version);
  }

  @Override
  public int hashCode() {
    Integer hashCode = this.hashCode;
    if (hashCode == null) {
      this.hashCode = hashCode = Objects.hash(super.hashCode(), namespace, artifact, version);
    }
    return hashCode;
  }

  @Override
  public Id.Artifact toId() {
    return Id.Artifact.from(Id.Namespace.from(namespace), artifact, version);
  }

  @SuppressWarnings("unused")
  public static ArtifactId fromIdParts(Iterable<String> idString) {
    Iterator<String> iterator = idString.iterator();
    return new ArtifactId(
      next(iterator, "namespace"), next(iterator, "artifact"),
      remaining(iterator, "version"));
  }

  @Override
  protected Iterable<String> toIdParts() {
    return Collections.unmodifiableList(Arrays.asList(namespace, artifact, version));
  }

  public static ArtifactId fromString(String string) {
    return EntityId.fromString(string, ArtifactId.class);
  }
}
